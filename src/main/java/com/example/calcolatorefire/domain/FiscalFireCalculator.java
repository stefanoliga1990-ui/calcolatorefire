package com.example.calcolatorefire.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.DoublePredicate;

/**
 * Couples fiscal accumulation, target calculation and decumulation while
 * keeping the legacy calculator independent.
 */
public final class FiscalFireCalculator {

    private static final double MONEY_TOLERANCE = 0.01;
    private static final double NUMERIC_TOLERANCE = 1e-7;
    private static final int MAX_SOLVER_ITERATIONS = 160;

    private final FiscalCalculator fiscalCalculator = new FiscalCalculator();
    private final FiscalAccumulationProjector accumulationProjector = new FiscalAccumulationProjector();

    public FiscalFireCalculationResult calculate(FiscalFireCalculationInput fiscalInput) {
        validate(fiscalInput);
        FireCalculationInput input = fiscalInput.fireInput();
        FiscalSettings settings = fiscalInput.settings();
        int accumulationMonths = toMonths(
                input.fireAge() - input.currentAge(),
                CalculationErrorCode.INVALID_AGE_ORDER
        );
        int fireMonths = toMonths(input.fireDurationYears(), CalculationErrorCode.INVALID_FIRE_DURATION);
        double monthlyInflation = FireCalculator.monthlyRate(input.annualInflationRate());
        double monthlyFireReturn = FireCalculator.monthlyRate(input.annualFireReturnRate());
        double monthlyAccumulationReturn = FireCalculator.monthlyRate(input.annualAccumulationReturnRate());
        double monthlyContributionGrowth = FireCalculator.monthlyRate(input.annualContributionGrowthRate());
        double inflationToFire = Math.pow(1.0 + monthlyInflation, accumulationMonths);
        double firstWithdrawal = input.monthlyExpenseToday() * inflationToFire;
        double terminalCapitalNominalAtEnd = input.terminalCapitalToday()
                * Math.pow(1.0 + monthlyInflation, accumulationMonths + fireMonths);
        FiscalCashFlows cashFlows = fireCashFlows(
                input,
                accumulationMonths,
                fireMonths,
                firstWithdrawal,
                monthlyInflation
        );

        Evaluation zeroContribution = evaluateContribution(
                fiscalInput,
                accumulationMonths,
                fireMonths,
                monthlyAccumulationReturn,
                monthlyContributionGrowth,
                monthlyFireReturn,
                monthlyInflation,
                terminalCapitalNominalAtEnd,
                cashFlows,
                0.0
        );

        Evaluation selectedEvaluation;
        double initialContribution;
        if (isFunded(zeroContribution)) {
            selectedEvaluation = zeroContribution;
            initialContribution = 0.0;
        } else {
            if (accumulationMonths == 0) {
                throw new FireCalculationException(
                        CalculationErrorCode.UNREACHABLE_WITH_ZERO_MONTHS,
                        "Il capitale è insufficiente e non ci sono mesi disponibili per il PAC."
                );
            }
            selectedEvaluation = solveContribution(
                    fiscalInput,
                    accumulationMonths,
                    fireMonths,
                    monthlyAccumulationReturn,
                    monthlyContributionGrowth,
                    monthlyFireReturn,
                    monthlyInflation,
                    terminalCapitalNominalAtEnd,
                    cashFlows
            );
            initialContribution = selectedEvaluation.initialContribution();
        }

        TargetValues targets = selectedEvaluation.targets();
        FiscalPortfolioState availableState = selectedEvaluation.accumulation().availableAtFireState();
        FiscalPortfolioState targetState = proportionalState(availableState, targets.selectedTarget());
        FiscalDecumulationProjection targetRun = projectDecumulation(
                targetState,
                cashFlows,
                monthlyFireReturn,
                settings
        );
        FiscalDecumulationProjection personalRun = projectDecumulation(
                availableState,
                cashFlows,
                monthlyFireReturn,
                settings
        );

        return new FiscalFireCalculationResult(
                accumulationMonths,
                fireMonths,
                settings,
                targets.finiteTarget(),
                targets.safeWithdrawalRateBaseTarget(),
                targets.safeWithdrawalRateTarget(),
                targets.selectedTarget(),
                targetState.taxBasis(),
                initialContribution,
                selectedEvaluation.accumulation(),
                targetRun,
                personalRun
        );
    }

    private Evaluation solveContribution(
            FiscalFireCalculationInput fiscalInput,
            int accumulationMonths,
            int fireMonths,
            double monthlyAccumulationReturn,
            double monthlyContributionGrowth,
            double monthlyFireReturn,
            double monthlyInflation,
            double terminalCapitalNominalAtEnd,
            FiscalCashFlows cashFlows
    ) {
        double lower = 0.0;
        double upper = 1.0;
        Evaluation upperEvaluation = null;

        for (int iteration = 0; iteration < MAX_SOLVER_ITERATIONS; iteration++) {
            try {
                upperEvaluation = evaluateContribution(
                        fiscalInput,
                        accumulationMonths,
                        fireMonths,
                        monthlyAccumulationReturn,
                        monthlyContributionGrowth,
                        monthlyFireReturn,
                        monthlyInflation,
                        terminalCapitalNominalAtEnd,
                        cashFlows,
                        upper
                );
            } catch (FireCalculationException exception) {
                if (exception.code() == CalculationErrorCode.INVALID_AMOUNT) {
                    throw solutionNotFound("delimitare il PAC mensile necessario");
                }
                throw exception;
            }
            if (isFunded(upperEvaluation)) {
                break;
            }
            lower = upper;
            upper *= 2.0;
            if (!Double.isFinite(upper) || upper > CalculationLimits.MAX_AMOUNT) {
                throw solutionNotFound("delimitare il PAC mensile necessario");
            }
        }
        if (upperEvaluation == null || !isFunded(upperEvaluation)) {
            throw solutionNotFound("delimitare il PAC mensile necessario");
        }

        for (int iteration = 0; iteration < MAX_SOLVER_ITERATIONS; iteration++) {
            double middle = lower + (upper - lower) / 2.0;
            if (middle == lower || middle == upper) {
                break;
            }
            Evaluation middleEvaluation = evaluateContribution(
                    fiscalInput,
                    accumulationMonths,
                    fireMonths,
                    monthlyAccumulationReturn,
                    monthlyContributionGrowth,
                    monthlyFireReturn,
                    monthlyInflation,
                    terminalCapitalNominalAtEnd,
                    cashFlows,
                    middle
            );
            if (isFunded(middleEvaluation)) {
                upper = middle;
                upperEvaluation = middleEvaluation;
            } else {
                lower = middle;
            }
            if (upper - lower <= NUMERIC_TOLERANCE) {
                break;
            }
        }
        return upperEvaluation;
    }

    private Evaluation evaluateContribution(
            FiscalFireCalculationInput fiscalInput,
            int accumulationMonths,
            int fireMonths,
            double monthlyAccumulationReturn,
            double monthlyContributionGrowth,
            double monthlyFireReturn,
            double monthlyInflation,
            double terminalCapitalNominalAtEnd,
            FiscalCashFlows cashFlows,
            double initialContribution
    ) {
        FiscalAccumulationPortfolioResult accumulation = projectAccumulation(
                fiscalInput,
                accumulationMonths,
                monthlyAccumulationReturn,
                monthlyContributionGrowth,
                monthlyInflation,
                initialContribution
        );
        double basisRatio = taxBasisRatio(accumulation.availableAtFireState());
        TargetValues targets = calculateTargets(
                fiscalInput.fireInput(),
                fiscalInput.settings(),
                cashFlows,
                fireMonths,
                monthlyFireReturn,
                terminalCapitalNominalAtEnd,
                basisRatio
        );
        return new Evaluation(initialContribution, accumulation, targets);
    }

    private FiscalAccumulationPortfolioResult projectAccumulation(
            FiscalFireCalculationInput fiscalInput,
            int months,
            double monthlyAccumulationReturn,
            double monthlyContributionGrowth,
            double monthlyInflation,
            double initialContribution
    ) {
        FireCalculationInput input = fiscalInput.fireInput();
        List<Double> mainInflows = new ArrayList<>(Collections.nCopies(months, 0.0));
        for (int month = 0; month < months; month++) {
            double amount = 0.0;
            for (AdditionalResource resource : input.additionalResources()) {
                if (resource instanceof PeriodicIncome income && income.investBeforeFire()) {
                    amount += periodicIncomeAt(input, income, month);
                }
            }
            mainInflows.set(month, amount);
        }

        List<FiscalAccumulationPlan> plans = new ArrayList<>();
        plans.add(FiscalAccumulationPlan.growingPac(
                "Patrimonio principale",
                fiscalCalculator.initialState(input.currentCapital(), fiscalInput.currentTaxBasis()),
                months,
                monthlyAccumulationReturn,
                initialContribution,
                monthlyContributionGrowth,
                mainInflows,
                true
        ).withSource("MAIN_PORTFOLIO", null));

        for (int resourceIndex = 0; resourceIndex < input.additionalResources().size(); resourceIndex++) {
            AdditionalResource resource = input.additionalResources().get(resourceIndex);
            if (resource instanceof ExistingInvestment investment) {
                int startMonth = investment.contributionStartAge() == null
                        ? 0
                        : toMonths(investment.contributionStartAge() - input.currentAge(),
                                CalculationErrorCode.INVALID_RESOURCE_PERIOD);
                int endMonth = investment.contributionEndAge() == null
                        ? 0
                        : toMonths(investment.contributionEndAge() - input.currentAge(),
                                CalculationErrorCode.INVALID_RESOURCE_PERIOD);
                Double taxBasis = fiscalInput.existingInvestmentTaxBases().get(resourceIndex);
                plans.add(FiscalAccumulationPlan.existingInvestment(
                        "Investimento: " + investment.name(),
                        fiscalCalculator.initialState(investment.currentCapital(), taxBasis),
                        months,
                        FireCalculator.monthlyRate(investment.annualReturnRate()),
                        investment.initialMonthlyContribution(),
                        FireCalculator.monthlyRate(investment.annualContributionGrowthRate()),
                        startMonth,
                        endMonth,
                        investment.availableAtFire()
                ).withSource("EXISTING_INVESTMENT", resourceIndex));
            } else if (resource instanceof FutureLumpSum lumpSum) {
                int receiptMonth = toMonths(
                        lumpSum.receiptAge() - input.currentAge(),
                        CalculationErrorCode.INVALID_RESOURCE_PERIOD
                );
                if (receiptMonth > months) {
                    continue;
                }
                double nominalAmount = lumpSum.amountBasis() == AmountBasis.TODAY
                        ? lumpSum.amount() * Math.pow(1.0 + monthlyInflation, receiptMonth)
                        : lumpSum.amount();
                double initialAmount = receiptMonth == 0 ? nominalAmount : 0.0;
                List<Double> inflows = new ArrayList<>(Collections.nCopies(months, 0.0));
                if (receiptMonth > 0) {
                    inflows.set(receiptMonth - 1, nominalAmount);
                }
                plans.add(new FiscalAccumulationPlan(
                        "Capitale futuro: " + lumpSum.name(),
                        fiscalCalculator.initialState(initialAmount, null),
                        lumpSum.investAfterReceipt()
                                ? FireCalculator.monthlyRate(lumpSum.annualReturnRateAfterReceipt())
                                : 0.0,
                        Collections.nCopies(months, 0.0),
                        inflows,
                        true,
                        lumpSum.investAfterReceipt(),
                        "FUTURE_LUMP_SUM",
                        resourceIndex
                ));
            }
        }
        return accumulationProjector.projectPortfolio(plans, fiscalInput.settings());
    }

    private TargetValues calculateTargets(
            FireCalculationInput input,
            FiscalSettings settings,
            FiscalCashFlows cashFlows,
            int fireMonths,
            double monthlyFireReturn,
            double terminalCapitalNominalAtEnd,
            double basisRatio
    ) {
        if (input.method() == FireMethod.FINITE) {
            double finiteTarget = solveMinimumBalance(balance -> {
                FiscalDecumulationProjection projection = projectDecumulation(
                        stateWithRatio(balance, basisRatio),
                        cashFlows,
                        monthlyFireReturn,
                        settings
                );
                return projection.totalShortfall() <= MONEY_TOLERANCE
                        && projection.finalState().balance() >= terminalCapitalNominalAtEnd;
            });
            return new TargetValues(finiteTarget, null, null, finiteTarget);
        }

        double taxableGainRatio = Math.max(0.0, 1.0 - basisRatio);
        double netSaleFactor = 1.0 - settings.capitalGainsTaxRate() * taxableGainRatio;
        double baseGrossSale = cashFlows.grossExpenses()[1] / netSaleFactor;
        double baseTarget = baseGrossSale * 12.0 / input.annualSafeWithdrawalRate();
        double selectedTarget = baseTarget;
        if (hasBridgeResources(cashFlows)) {
            int stableMonth = stableRegimeStartMonth(input, cashFlows.accumulationMonths(), fireMonths);
            double bridgeTarget = solveMinimumBalance(balance -> swrBridgeIsFunded(
                    stateWithRatio(balance, basisRatio),
                    cashFlows,
                    stableMonth,
                    monthlyFireReturn,
                    input.annualSafeWithdrawalRate(),
                    settings
            ));
            selectedTarget = Math.min(baseTarget, bridgeTarget);
        }
        requireCalculatedAmount(baseTarget, "target SWR fiscale base");
        requireCalculatedAmount(selectedTarget, "target SWR fiscale");
        return new TargetValues(null, baseTarget, selectedTarget, selectedTarget);
    }

    private boolean swrBridgeIsFunded(
            FiscalPortfolioState startingState,
            FiscalCashFlows cashFlows,
            int stableMonth,
            double monthlyFireReturn,
            double annualSafeWithdrawalRate,
            FiscalSettings settings
    ) {
        FiscalPortfolioState state = startingState;
        for (int month = 1; month < stableMonth; month++) {
            FiscalDecumulationMonthResult result = fiscalCalculator.decumulateMonth(
                    state,
                    cashFlows.netWithdrawals()[month],
                    cashFlows.capitalInflows()[month],
                    monthlyFireReturn,
                    settings
            );
            if (result.shortfall() > MONEY_TOLERANCE) {
                return false;
            }
            state = result.closingState();
        }
        FiscalDecumulationMonthResult stable = fiscalCalculator.decumulateMonth(
                state,
                cashFlows.netWithdrawals()[stableMonth],
                cashFlows.capitalInflows()[stableMonth],
                monthlyFireReturn,
                settings
        );
        double stableReserve = stable.requiredGrossSale() * 12.0 / annualSafeWithdrawalRate;
        return stable.availableBalance() >= stableReserve;
    }

    private double solveMinimumBalance(DoublePredicate isSufficient) {
        if (isSufficient.test(0.0)) {
            return 0.0;
        }
        double lower = 0.0;
        double upper = 1.0;
        boolean delimited = false;
        for (int iteration = 0; iteration < MAX_SOLVER_ITERATIONS; iteration++) {
            if (isSufficient.test(upper)) {
                delimited = true;
                break;
            }
            lower = upper;
            upper *= 2.0;
            if (!Double.isFinite(upper) || upper > CalculationLimits.MAX_AMOUNT) {
                break;
            }
        }
        if (!delimited) {
            if (CalculationLimits.MAX_AMOUNT > lower && isSufficient.test(CalculationLimits.MAX_AMOUNT)) {
                upper = CalculationLimits.MAX_AMOUNT;
            } else {
                throw solutionNotFound("delimitare il target FIRE fiscale");
            }
        }

        for (int iteration = 0; iteration < MAX_SOLVER_ITERATIONS; iteration++) {
            double middle = lower + (upper - lower) / 2.0;
            if (middle == lower || middle == upper) {
                break;
            }
            if (isSufficient.test(middle)) {
                upper = middle;
            } else {
                lower = middle;
            }
            if (upper - lower <= MONEY_TOLERANCE / 2.0) {
                break;
            }
        }
        return upper;
    }

    private FiscalDecumulationProjection projectDecumulation(
            FiscalPortfolioState startingState,
            FiscalCashFlows cashFlows,
            double monthlyReturn,
            FiscalSettings settings
    ) {
        List<FiscalDecumulationProjectionPoint> points = new ArrayList<>(cashFlows.fireMonths() + 1);
        FiscalPortfolioState state = startingState;
        points.add(new FiscalDecumulationProjectionPoint(
                0, state, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, state
        ));
        double totalGrossSales = 0.0;
        double totalTax = 0.0;
        double totalNetProceeds = 0.0;
        double totalStampDuty = 0.0;
        double totalShortfall = 0.0;
        Integer depletionMonth = null;

        for (int month = 1; month <= cashFlows.fireMonths(); month++) {
            FiscalDecumulationMonthResult result = fiscalCalculator.decumulateMonth(
                    state,
                    cashFlows.netWithdrawals()[month],
                    cashFlows.capitalInflows()[month],
                    monthlyReturn,
                    settings
            );
            double terminalInflow = month == cashFlows.fireMonths()
                    ? cashFlows.terminalCapitalInflow()
                    : 0.0;
            FiscalPortfolioState closingState = result.closingState();
            if (terminalInflow > 0.0) {
                closingState = new FiscalPortfolioState(
                        closingState.balance() + terminalInflow,
                        closingState.taxBasis() + terminalInflow
                );
            }
            totalGrossSales += result.grossSale();
            totalTax += result.capitalGainsTax();
            totalNetProceeds += result.netProceeds();
            totalStampDuty += result.stampDuty();
            totalShortfall += result.shortfall();
            requireCalculatedAmount(totalGrossSales, "vendite lorde totali");
            requireCalculatedAmount(totalTax, "imposte sulle plusvalenze totali");
            requireCalculatedAmount(totalNetProceeds, "ricavi netti totali");
            requireCalculatedAmount(totalStampDuty, "bollo totale nel FIRE");
            requireCalculatedAmount(totalShortfall, "shortfall totale");
            if (depletionMonth == null && result.shortfall() > MONEY_TOLERANCE) {
                depletionMonth = month;
            }
            points.add(new FiscalDecumulationProjectionPoint(
                    month,
                    result.openingState(),
                    cashFlows.grossExpenses()[month],
                    cashFlows.additionalIncome()[month],
                    result.requestedNetAmount(),
                    result.netCapitalInflow(),
                    result.taxableGainRatio(),
                    result.requiredGrossSale(),
                    result.grossSale(),
                    result.capitalGainsTax(),
                    result.netProceeds(),
                    result.shortfall(),
                    result.investmentReturn(),
                    result.stampDuty(),
                    terminalInflow,
                    closingState
            ));
            state = closingState;
        }
        return new FiscalDecumulationProjection(
                startingState,
                state,
                totalGrossSales,
                totalTax,
                totalNetProceeds,
                totalStampDuty,
                totalShortfall,
                depletionMonth,
                points
        );
    }

    private static boolean isFunded(Evaluation evaluation) {
        return evaluation.accumulation().availableAtFireState().balance()
                >= evaluation.targets().selectedTarget();
    }

    private static FiscalPortfolioState proportionalState(
            FiscalPortfolioState availableState,
            double targetBalance
    ) {
        if (targetBalance == 0.0) {
            return new FiscalPortfolioState(0.0, 0.0);
        }
        double ratio = taxBasisRatio(availableState);
        return stateWithRatio(targetBalance, ratio);
    }

    private static FiscalPortfolioState stateWithRatio(double balance, double basisRatio) {
        double taxBasis = balance * basisRatio;
        if (!Double.isFinite(taxBasis)) {
            throw solutionNotFound("calcolare il costo fiscale proporzionale");
        }
        return new FiscalPortfolioState(balance, taxBasis);
    }

    private static double taxBasisRatio(FiscalPortfolioState state) {
        if (state.balance() == 0.0) {
            return 1.0;
        }
        double ratio = state.taxBasis() / state.balance();
        if (!Double.isFinite(ratio) || ratio < 0.0) {
            throw solutionNotFound("calcolare il rapporto del costo fiscale");
        }
        return ratio;
    }

    private static FiscalCashFlows fireCashFlows(
            FireCalculationInput input,
            int accumulationMonths,
            int fireMonths,
            double firstWithdrawal,
            double monthlyInflation
    ) {
        double[] grossExpenses = new double[fireMonths + 1];
        double[] additionalIncome = new double[fireMonths + 1];
        double[] netWithdrawals = new double[fireMonths + 1];
        double[] capitalInflows = new double[fireMonths + 1];
        int fireEndMonth = accumulationMonths + fireMonths;
        double terminalCapitalInflow = 0.0;

        for (int month = 1; month <= fireMonths; month++) {
            int absoluteMonth = accumulationMonths + month - 1;
            double incomeTotal = 0.0;
            for (AdditionalResource resource : input.additionalResources()) {
                if (resource instanceof PeriodicIncome income && income.offsetDuringFire()) {
                    incomeTotal += periodicIncomeAt(input, income, absoluteMonth);
                }
            }
            double grossExpense = firstWithdrawal * Math.pow(1.0 + monthlyInflation, month - 1);
            grossExpenses[month] = grossExpense;
            additionalIncome[month] = incomeTotal;
            netWithdrawals[month] = Math.max(0.0, grossExpense - incomeTotal);
        }

        for (AdditionalResource resource : input.additionalResources()) {
            if (!(resource instanceof FutureLumpSum lumpSum)) {
                continue;
            }
            int receiptMonth = toMonths(
                    lumpSum.receiptAge() - input.currentAge(),
                    CalculationErrorCode.INVALID_RESOURCE_PERIOD
            );
            if (receiptMonth <= accumulationMonths) {
                continue;
            }
            double nominalAmount = lumpSum.amountBasis() == AmountBasis.TODAY
                    ? lumpSum.amount() * Math.pow(1.0 + monthlyInflation, receiptMonth)
                    : lumpSum.amount();
            if (receiptMonth < fireEndMonth) {
                capitalInflows[receiptMonth - accumulationMonths + 1] += nominalAmount;
            } else if (receiptMonth == fireEndMonth) {
                terminalCapitalInflow += nominalAmount;
            }
        }
        return new FiscalCashFlows(
                accumulationMonths,
                fireMonths,
                grossExpenses,
                additionalIncome,
                netWithdrawals,
                capitalInflows,
                terminalCapitalInflow
        );
    }

    private static double periodicIncomeAt(
            FireCalculationInput input,
            PeriodicIncome income,
            int absoluteMonth
    ) {
        int startMonth = toMonths(
                income.startAge() - input.currentAge(),
                CalculationErrorCode.INVALID_RESOURCE_PERIOD
        );
        int endMonth = income.endAge() == null
                ? Integer.MAX_VALUE
                : toMonths(income.endAge() - input.currentAge(), CalculationErrorCode.INVALID_RESOURCE_PERIOD);
        if (absoluteMonth < startMonth || absoluteMonth >= endMonth) {
            return 0.0;
        }
        return income.monthlyAmountToday()
                * Math.pow(1.0 + FireCalculator.monthlyRate(income.annualGrowthRate()), absoluteMonth);
    }

    private static boolean hasBridgeResources(FiscalCashFlows cashFlows) {
        for (int month = 1; month <= cashFlows.fireMonths(); month++) {
            if (cashFlows.additionalIncome()[month] > 0.0 || cashFlows.capitalInflows()[month] > 0.0) {
                return true;
            }
        }
        return false;
    }

    private static int stableRegimeStartMonth(
            FireCalculationInput input,
            int accumulationMonths,
            int fireMonths
    ) {
        int stableMonth = 1;
        int fireEndMonth = accumulationMonths + fireMonths;
        for (AdditionalResource resource : input.additionalResources()) {
            if (resource instanceof PeriodicIncome income && income.offsetDuringFire()) {
                int startMonth = toMonths(
                        income.startAge() - input.currentAge(),
                        CalculationErrorCode.INVALID_RESOURCE_PERIOD
                );
                if (startMonth >= accumulationMonths && startMonth < fireEndMonth) {
                    stableMonth = Math.max(stableMonth, startMonth - accumulationMonths + 1);
                }
                if (income.endAge() != null) {
                    int endMonth = toMonths(
                            income.endAge() - input.currentAge(),
                            CalculationErrorCode.INVALID_RESOURCE_PERIOD
                    );
                    if (endMonth >= accumulationMonths && endMonth < fireEndMonth) {
                        stableMonth = Math.max(stableMonth, endMonth - accumulationMonths + 1);
                    }
                }
            } else if (resource instanceof FutureLumpSum lumpSum) {
                int receiptMonth = toMonths(
                        lumpSum.receiptAge() - input.currentAge(),
                        CalculationErrorCode.INVALID_RESOURCE_PERIOD
                );
                if (receiptMonth > accumulationMonths && receiptMonth < fireEndMonth) {
                    stableMonth = Math.max(stableMonth, receiptMonth - accumulationMonths + 1);
                }
            }
        }
        return stableMonth;
    }

    private void validate(FiscalFireCalculationInput fiscalInput) {
        if (fiscalInput == null || fiscalInput.fireInput() == null) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_AMOUNT,
                    "Gli input del calcolo fiscale sono obbligatori."
            );
        }
        FireCalculator.validate(fiscalInput.fireInput());
        if (fiscalInput.settings() == null) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_CAPITAL_GAINS_TAX_RATE,
                    "Le impostazioni fiscali sono obbligatorie."
            );
        }
        fiscalCalculator.initialState(
                fiscalInput.fireInput().currentCapital(),
                fiscalInput.currentTaxBasis()
        );
        for (Map.Entry<Integer, Double> entry : fiscalInput.existingInvestmentTaxBases().entrySet()) {
            int index = entry.getKey();
            if (index < 0 || index >= fiscalInput.fireInput().additionalResources().size()
                    || !(fiscalInput.fireInput().additionalResources().get(index) instanceof ExistingInvestment investment)) {
                throw new FireCalculationException(
                        CalculationErrorCode.INVALID_RESOURCE,
                        "Il costo fiscale aggiuntivo deve riferirsi a un investimento esistente."
                );
            }
            fiscalCalculator.initialState(investment.currentCapital(), entry.getValue());
        }
    }

    private static int toMonths(int years, CalculationErrorCode code) {
        try {
            return Math.multiplyExact(years, 12);
        } catch (ArithmeticException exception) {
            throw new FireCalculationException(code, "L'orizzonte temporale è troppo grande.");
        }
    }

    private static void requireCalculatedAmount(double amount, String label) {
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw solutionNotFound("calcolare " + label);
        }
    }

    private static FireCalculationException solutionNotFound(String operation) {
        return new FireCalculationException(
                CalculationErrorCode.FISCAL_SOLUTION_NOT_FOUND,
                "Il risolutore fiscale non riesce a " + operation + "."
        );
    }

    private record FiscalCashFlows(
            int accumulationMonths,
            int fireMonths,
            double[] grossExpenses,
            double[] additionalIncome,
            double[] netWithdrawals,
            double[] capitalInflows,
            double terminalCapitalInflow
    ) {
    }

    private record TargetValues(
            Double finiteTarget,
            Double safeWithdrawalRateBaseTarget,
            Double safeWithdrawalRateTarget,
            double selectedTarget
    ) {
    }

    private record Evaluation(
            double initialContribution,
            FiscalAccumulationPortfolioResult accumulation,
            TargetValues targets
    ) {
    }
}
