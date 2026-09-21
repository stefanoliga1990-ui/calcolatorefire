package com.example.calcolatorefire.domain;

import java.util.ArrayList;
import java.util.List;

public final class FireCalculator {

    private static final double RATE_TOLERANCE = 1e-12;
    private static final double MONEY_TOLERANCE = 0.01;

    public FireCalculationResult calculate(FireCalculationInput input) {
        validate(input);

        int accumulationMonths = toMonths(input.fireAge() - input.currentAge(), CalculationErrorCode.INVALID_AGE_ORDER);
        int fireMonths = toMonths(input.fireDurationYears(), CalculationErrorCode.INVALID_FIRE_DURATION);

        double monthlyInflation = monthlyRate(input.annualInflationRate());
        double monthlyFireReturn = monthlyRate(input.annualFireReturnRate());
        double monthlyAccumulationReturn = monthlyRate(input.annualAccumulationReturnRate());
        double monthlyContributionGrowth = monthlyRate(input.annualContributionGrowthRate());
        double monthlyRealFireReturn = (1.0 + monthlyFireReturn) / (1.0 + monthlyInflation) - 1.0;

        double inflationToFire = Math.pow(1.0 + monthlyInflation, accumulationMonths);
        double firstWithdrawal = input.monthlyExpenseToday() * inflationToFire;
        double terminalCapitalAtFire = input.terminalCapitalToday() * inflationToFire;
        double terminalCapitalNominalAtEnd = input.terminalCapitalToday()
                * Math.pow(1.0 + monthlyInflation, accumulationMonths + fireMonths);

        FireCashFlows fireCashFlows = fireCashFlows(
                input,
                accumulationMonths,
                fireMonths,
                firstWithdrawal,
                monthlyInflation
        );

        Double finiteTarget = input.method() == FireMethod.FINITE
                ? finiteTarget(
                        fireCashFlows,
                        firstWithdrawal,
                        terminalCapitalAtFire,
                        terminalCapitalNominalAtEnd,
                        monthlyRealFireReturn,
                        monthlyFireReturn,
                        fireMonths
                )
                : null;
        Double swrBaseTarget = input.method() == FireMethod.SWR
                ? firstWithdrawal * 12.0 / input.annualSafeWithdrawalRate()
                : null;
        Double swrTarget = input.method() == FireMethod.SWR
                ? safeWithdrawalRateTarget(
                        input,
                        fireCashFlows,
                        swrBaseTarget,
                        monthlyFireReturn,
                        accumulationMonths,
                        fireMonths
                )
                : null;
        double selectedTarget = input.method() == FireMethod.FINITE ? finiteTarget : swrTarget;
        double selectedTargetToday = selectedTarget / inflationToFire;

        double projectedCurrentCapitalAtFire = input.currentCapital()
                * Math.pow(1.0 + monthlyAccumulationReturn, accumulationMonths);
        ResourceAccumulation resourceAccumulation = projectAdditionalAccumulation(
                input,
                accumulationMonths,
                monthlyAccumulationReturn,
                monthlyInflation
        );
        double availableBeforeNewPac = projectedCurrentCapitalAtFire
                + resourceAccumulation.investedIncomeFinalBalance()
                + resourceAccumulation.availableExistingInvestmentsFinalBalance()
                + resourceAccumulation.availableFutureLumpSumsFinalBalance();
        double capitalGap = Math.max(0.0, selectedTarget - availableBeforeNewPac);

        if (accumulationMonths == 0 && capitalGap > MONEY_TOLERANCE) {
            throw new FireCalculationException(
                    CalculationErrorCode.UNREACHABLE_WITH_ZERO_MONTHS,
                    "Il capitale è insufficiente e non ci sono mesi disponibili per il PAC."
            );
        }

        double initialContribution = initialContribution(
                capitalGap,
                accumulationMonths,
                monthlyAccumulationReturn,
                monthlyContributionGrowth
        );

        AccumulationRun accumulation = projectAccumulation(
                input,
                accumulationMonths,
                monthlyAccumulationReturn,
                monthlyContributionGrowth,
                initialContribution,
                resourceAccumulation
        );

        Double finiteTargetProjectedFinal = finiteTarget == null
                ? null
                : projectRawFinalBalance(finiteTarget, fireCashFlows, monthlyFireReturn, fireMonths);

        DecumulationRun targetRun = projectDecumulation(
                selectedTarget,
                input.fireAge(),
                fireCashFlows,
                monthlyFireReturn,
                fireMonths
        );

        double personalStart = Math.max(selectedTarget, accumulation.finalBalance());
        DecumulationRun personalRun = projectDecumulation(
                personalStart,
                input.fireAge(),
                fireCashFlows,
                monthlyFireReturn,
                fireMonths
        );

        return new FireCalculationResult(
                accumulationMonths,
                fireMonths,
                monthlyInflation,
                monthlyFireReturn,
                monthlyRealFireReturn,
                monthlyAccumulationReturn,
                monthlyContributionGrowth,
                firstWithdrawal,
                terminalCapitalAtFire,
                terminalCapitalNominalAtEnd,
                finiteTarget,
                swrBaseTarget,
                swrTarget,
                selectedTarget,
                selectedTargetToday,
                fireCashFlows.additionalIncome()[1],
                fireCashFlows.netWithdrawals()[1],
                projectedCurrentCapitalAtFire,
                capitalGap,
                initialContribution,
                accumulation.totalContributions(),
                resourceAccumulation.totalIncomeInvested(),
                resourceAccumulation.totalExistingInvestmentContributions(),
                accumulation.mainPortfolioFinalBalance(),
                resourceAccumulation.investedIncomeFinalBalance(),
                resourceAccumulation.availableExistingInvestmentsFinalBalance(),
                resourceAccumulation.availableFutureLumpSumsFinalBalance(),
                accumulation.finalBalance(),
                finiteTargetProjectedFinal,
                targetRun.finalBalance(),
                personalStart,
                personalRun.finalBalance(),
                fireCashFlows.totalCapitalInflows(),
                fireCashFlows.terminalCapitalInflow(),
                personalRun.totalShortfall(),
                personalRun.depletionMonth(),
                accumulation.points(),
                personalRun.points(),
                resourceAccumulation.existingInvestments(),
                resourceAccumulation.futureLumpSums()
        );
    }

    private static double finiteTarget(
            FireCashFlows cashFlows,
            double firstWithdrawal,
            double terminalCapitalAtFire,
            double terminalCapitalNominalAtEnd,
            double monthlyRealReturn,
            double monthlyFireReturn,
            int fireMonths
    ) {
        if (!hasVariableFireResources(cashFlows)) {
            return finiteTarget(firstWithdrawal, terminalCapitalAtFire, monthlyRealReturn, fireMonths);
        }

        double required = Math.max(0.0, terminalCapitalNominalAtEnd - cashFlows.terminalCapitalInflow());
        for (int month = fireMonths; month >= 1; month--) {
            required = Math.max(0.0, cashFlows.netWithdrawals()[month]
                    + required / (1.0 + monthlyFireReturn)
                    - cashFlows.capitalInflows()[month]);
        }
        return required;
    }

    private static double safeWithdrawalRateTarget(
            FireCalculationInput input,
            FireCashFlows cashFlows,
            double baseTarget,
            double monthlyFireReturn,
            int accumulationMonths,
            int fireMonths
    ) {
        if (!hasBridgeResources(cashFlows)) {
            return baseTarget;
        }

        int stableMonth = stableRegimeStartMonth(input, accumulationMonths, fireMonths);
        double bridge = Math.max(0.0, cashFlows.netWithdrawals()[stableMonth]
                * 12.0
                / input.annualSafeWithdrawalRate()
                - cashFlows.capitalInflows()[stableMonth]);
        for (int month = stableMonth - 1; month >= 1; month--) {
            bridge = Math.max(0.0, cashFlows.netWithdrawals()[month]
                    + bridge / (1.0 + monthlyFireReturn)
                    - cashFlows.capitalInflows()[month]);
        }
        return Math.min(baseTarget, bridge);
    }

    static double monthlyRate(double annualRate) {
        return Math.pow(1.0 + annualRate, 1.0 / 12.0) - 1.0;
    }

    private static double finiteTarget(
            double firstWithdrawal,
            double terminalCapitalAtFire,
            double monthlyRealReturn,
            int fireMonths
    ) {
        if (Math.abs(monthlyRealReturn) < RATE_TOLERANCE) {
            return firstWithdrawal * fireMonths + terminalCapitalAtFire;
        }

        double factor = (1.0 - Math.pow(1.0 + monthlyRealReturn, -fireMonths))
                / monthlyRealReturn
                * (1.0 + monthlyRealReturn);
        return firstWithdrawal * factor
                + terminalCapitalAtFire / Math.pow(1.0 + monthlyRealReturn, fireMonths);
    }

    private static double initialContribution(
            double capitalGap,
            int months,
            double monthlyReturn,
            double monthlyGrowth
    ) {
        if (capitalGap == 0.0 || months == 0) {
            return 0.0;
        }

        double factor;
        if (Math.abs(monthlyReturn - monthlyGrowth) < RATE_TOLERANCE) {
            factor = months * Math.pow(1.0 + monthlyReturn, months - 1);
        } else {
            factor = (Math.pow(1.0 + monthlyReturn, months) - Math.pow(1.0 + monthlyGrowth, months))
                    / (monthlyReturn - monthlyGrowth);
        }

        if (!Double.isFinite(factor) || factor <= 0.0) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_RATE,
                    "I tassi non producono un fattore di accumulo valido."
            );
        }
        return capitalGap / factor;
    }

    private static AccumulationRun projectAccumulation(
            FireCalculationInput input,
            int months,
            double monthlyReturn,
            double monthlyGrowth,
            double initialContribution,
            ResourceAccumulation resources
    ) {
        List<AccumulationPoint> points = new ArrayList<>(months + 1);
        double primaryBalance = input.currentCapital();
        double cumulativeContributions = 0.0;
        double initialExistingBalance = resources.availableExistingInvestmentsBalances()[0];
        double initialLumpSumBalance = resources.availableFutureLumpSumsBalances()[0];
        points.add(new AccumulationPoint(
                0,
                input.currentAge(),
                primaryBalance,
                0.0,
                0.0,
                0.0,
                primaryBalance,
                0.0,
                0.0,
                initialExistingBalance,
                initialLumpSumBalance,
                primaryBalance + initialExistingBalance + initialLumpSumBalance
        ));

        for (int month = 1; month <= months; month++) {
            double incomeOpening = resources.investedIncomeBalances()[month - 1];
            double opening = primaryBalance + incomeOpening;
            double investmentReturn = opening * monthlyReturn;
            double contribution = initialContribution * Math.pow(1.0 + monthlyGrowth, month - 1);
            cumulativeContributions += contribution;
            primaryBalance = primaryBalance + primaryBalance * monthlyReturn + contribution;
            double closing = primaryBalance + resources.investedIncomeBalances()[month];
            double existingBalance = resources.availableExistingInvestmentsBalances()[month];
            double lumpSumBalance = resources.availableFutureLumpSumsBalances()[month];
            points.add(new AccumulationPoint(
                    month,
                    input.currentAge() + month / 12.0,
                    opening,
                    investmentReturn,
                    contribution,
                    resources.incomeContributions()[month],
                    closing,
                    cumulativeContributions,
                    resources.cumulativeIncomeContributions()[month],
                    existingBalance,
                    lumpSumBalance,
                    closing + existingBalance + lumpSumBalance
            ));
        }
        double mainPortfolioFinalBalance = primaryBalance + resources.investedIncomeFinalBalance();
        double totalFinalBalance = mainPortfolioFinalBalance
                + resources.availableExistingInvestmentsFinalBalance()
                + resources.availableFutureLumpSumsFinalBalance();
        return new AccumulationRun(
                List.copyOf(points),
                totalFinalBalance,
                mainPortfolioFinalBalance,
                cumulativeContributions
        );
    }

    private static DecumulationRun projectDecumulation(
            double startingBalance,
            int fireAge,
            FireCashFlows cashFlows,
            double monthlyReturn,
            int months
    ) {
        List<DecumulationPoint> points = new ArrayList<>(months + 1);
        double balance = startingBalance;
        double totalShortfall = 0.0;
        Integer depletionMonth = null;
        points.add(new DecumulationPoint(
                0, fireAge, balance, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, balance
        ));

        for (int month = 1; month <= months; month++) {
            double opening = balance;
            double capitalInflow = cashFlows.capitalInflows()[month];
            double available = opening + capitalInflow;
            double scheduledWithdrawal = cashFlows.netWithdrawals()[month];
            double actualWithdrawal = Math.min(Math.max(available, 0.0), scheduledWithdrawal);
            double shortfall = scheduledWithdrawal - actualWithdrawal;
            double remaining = available - actualWithdrawal;
            double investmentReturn = remaining * monthlyReturn;
            double terminalCapitalInflow = month == months ? cashFlows.terminalCapitalInflow() : 0.0;
            balance = Math.max(0.0, remaining + investmentReturn + terminalCapitalInflow);
            totalShortfall += shortfall;
            if (depletionMonth == null && shortfall > MONEY_TOLERANCE) {
                depletionMonth = month;
            }
            points.add(new DecumulationPoint(
                    month,
                    fireAge + month / 12.0,
                    opening,
                    cashFlows.grossExpenses()[month],
                    cashFlows.additionalIncome()[month],
                    scheduledWithdrawal,
                    capitalInflow,
                    terminalCapitalInflow,
                    actualWithdrawal,
                    shortfall,
                    investmentReturn,
                    balance
            ));
        }
        return new DecumulationRun(List.copyOf(points), balance, totalShortfall, depletionMonth);
    }

    private static double projectRawFinalBalance(
            double startingBalance,
            FireCashFlows cashFlows,
            double monthlyReturn,
            int months
    ) {
        double balance = startingBalance;
        for (int month = 1; month <= months; month++) {
            balance = (balance + cashFlows.capitalInflows()[month] - cashFlows.netWithdrawals()[month])
                    * (1.0 + monthlyReturn);
        }
        return balance + cashFlows.terminalCapitalInflow();
    }

    private static ResourceAccumulation projectAdditionalAccumulation(
            FireCalculationInput input,
            int months,
            double monthlyAccumulationReturn,
            double monthlyInflation
    ) {
        double[] incomeContributions = new double[months + 1];
        double[] cumulativeIncomeContributions = new double[months + 1];
        double[] investedIncomeBalances = new double[months + 1];
        double[] availableExistingBalances = new double[months + 1];
        double[] availableFutureLumpSumsBalances = new double[months + 1];
        List<ExistingInvestmentResult> investmentResults = new ArrayList<>();
        List<FutureLumpSumResult> lumpSumResults = new ArrayList<>();
        double totalExistingContributions = 0.0;

        for (int resourceIndex = 0; resourceIndex < input.additionalResources().size(); resourceIndex++) {
            AdditionalResource resource = input.additionalResources().get(resourceIndex);
            if (!(resource instanceof ExistingInvestment investment)) {
                continue;
            }

            double resourceMonthlyReturn = monthlyRate(investment.annualReturnRate());
            double contributionMonthlyGrowth = monthlyRate(investment.annualContributionGrowthRate());
            Integer startMonth = investment.contributionStartAge() == null
                    ? null
                    : toMonths(investment.contributionStartAge() - input.currentAge(),
                            CalculationErrorCode.INVALID_RESOURCE_PERIOD);
            Integer endMonth = investment.contributionEndAge() == null
                    ? null
                    : toMonths(investment.contributionEndAge() - input.currentAge(),
                            CalculationErrorCode.INVALID_RESOURCE_PERIOD);

            List<ExistingInvestmentPoint> points = new ArrayList<>(months + 1);
            double balance = investment.currentCapital();
            double cumulativeContributions = 0.0;
            points.add(new ExistingInvestmentPoint(
                    0, input.currentAge(), balance, 0.0, 0.0, balance, 0.0
            ));
            if (investment.availableAtFire()) {
                availableExistingBalances[0] += balance;
            }

            for (int month = 1; month <= months; month++) {
                int absoluteMonth = month - 1;
                double opening = balance;
                double investmentReturn = opening * resourceMonthlyReturn;
                double contribution = startMonth != null
                        && absoluteMonth >= startMonth
                        && absoluteMonth < endMonth
                        ? investment.initialMonthlyContribution()
                                * Math.pow(1.0 + contributionMonthlyGrowth, absoluteMonth - startMonth)
                        : 0.0;
                cumulativeContributions += contribution;
                balance = opening + investmentReturn + contribution;
                points.add(new ExistingInvestmentPoint(
                        month,
                        input.currentAge() + month / 12.0,
                        opening,
                        investmentReturn,
                        contribution,
                        balance,
                        cumulativeContributions
                ));
                if (investment.availableAtFire()) {
                    availableExistingBalances[month] += balance;
                }
            }

            totalExistingContributions += cumulativeContributions;
            investmentResults.add(new ExistingInvestmentResult(
                    resourceIndex,
                    investment.name(),
                    investment.availableAtFire(),
                    cumulativeContributions,
                    balance,
                    points
            ));
        }

        for (int resourceIndex = 0; resourceIndex < input.additionalResources().size(); resourceIndex++) {
            AdditionalResource resource = input.additionalResources().get(resourceIndex);
            if (!(resource instanceof FutureLumpSum lumpSum)) {
                continue;
            }

            int receiptMonth = toMonths(
                    lumpSum.receiptAge() - input.currentAge(),
                    CalculationErrorCode.INVALID_RESOURCE_PERIOD
            );
            double nominalAmountAtReceipt = lumpSum.amountBasis() == AmountBasis.TODAY
                    ? lumpSum.amount() * Math.pow(1.0 + monthlyInflation, receiptMonth)
                    : lumpSum.amount();
            double balanceAtFire = 0.0;
            Integer fireReceiptMonth = null;

            if (receiptMonth <= months) {
                double monthlyReturn = lumpSum.investAfterReceipt()
                        ? monthlyRate(lumpSum.annualReturnRateAfterReceipt())
                        : 0.0;
                for (int month = receiptMonth; month <= months; month++) {
                    double balance = nominalAmountAtReceipt
                            * Math.pow(1.0 + monthlyReturn, month - receiptMonth);
                    availableFutureLumpSumsBalances[month] += balance;
                }
                balanceAtFire = nominalAmountAtReceipt
                        * Math.pow(1.0 + monthlyReturn, months - receiptMonth);
            } else {
                fireReceiptMonth = receiptMonth - months + 1;
            }

            lumpSumResults.add(new FutureLumpSumResult(
                    resourceIndex,
                    lumpSum.name(),
                    lumpSum.amountBasis(),
                    receiptMonth,
                    input.currentAge() + receiptMonth / 12.0,
                    nominalAmountAtReceipt,
                    balanceAtFire,
                    fireReceiptMonth
            ));
        }

        double incomeBalance = 0.0;
        double totalIncomeInvested = 0.0;
        for (int month = 1; month <= months; month++) {
            int absoluteMonth = month - 1;
            double contribution = 0.0;
            for (AdditionalResource resource : input.additionalResources()) {
                if (resource instanceof PeriodicIncome income && income.investBeforeFire()) {
                    contribution += periodicIncomeAt(input, income, absoluteMonth);
                }
            }
            totalIncomeInvested += contribution;
            incomeBalance = incomeBalance * (1.0 + monthlyAccumulationReturn) + contribution;
            incomeContributions[month] = contribution;
            cumulativeIncomeContributions[month] = totalIncomeInvested;
            investedIncomeBalances[month] = incomeBalance;
        }

        return new ResourceAccumulation(
                incomeContributions,
                cumulativeIncomeContributions,
                investedIncomeBalances,
                availableExistingBalances,
                availableFutureLumpSumsBalances,
                totalIncomeInvested,
                totalExistingContributions,
                incomeBalance,
                availableExistingBalances[months],
                availableFutureLumpSumsBalances[months],
                List.copyOf(investmentResults),
                List.copyOf(lumpSumResults)
        );
    }

    private static FireCashFlows fireCashFlows(
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
                int fireReceiptMonth = receiptMonth - accumulationMonths + 1;
                capitalInflows[fireReceiptMonth] += nominalAmount;
            } else if (receiptMonth == fireEndMonth) {
                terminalCapitalInflow += nominalAmount;
            }
        }

        double totalCapitalInflows = terminalCapitalInflow;
        for (int month = 1; month <= fireMonths; month++) {
            totalCapitalInflows += capitalInflows[month];
        }
        return new FireCashFlows(
                grossExpenses,
                additionalIncome,
                netWithdrawals,
                capitalInflows,
                terminalCapitalInflow,
                totalCapitalInflows
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
        double monthlyGrowth = monthlyRate(income.annualGrowthRate());
        return income.monthlyAmountToday() * Math.pow(1.0 + monthlyGrowth, absoluteMonth);
    }

    private static boolean hasBridgeResources(FireCashFlows cashFlows) {
        for (int month = 1; month < cashFlows.additionalIncome().length; month++) {
            if (cashFlows.additionalIncome()[month] > 0.0 || cashFlows.capitalInflows()[month] > 0.0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasVariableFireResources(FireCashFlows cashFlows) {
        return hasBridgeResources(cashFlows) || cashFlows.terminalCapitalInflow() > 0.0;
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

    private static int toMonths(int years, CalculationErrorCode code) {
        try {
            return Math.multiplyExact(years, 12);
        } catch (ArithmeticException exception) {
            throw new FireCalculationException(code, "L'orizzonte temporale è troppo grande.");
        }
    }

    private static void validate(FireCalculationInput input) {
        if (input == null) {
            throw new FireCalculationException(CalculationErrorCode.INVALID_AMOUNT, "Gli input sono obbligatori.");
        }
        if (input.method() == null) {
            throw new FireCalculationException(CalculationErrorCode.INVALID_METHOD, "Il metodo è obbligatorio.");
        }
        if (input.currentAge() < 0 || input.fireAge() < input.currentAge()) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_AGE_ORDER,
                    "L'età FIRE non può essere inferiore all'età attuale."
            );
        }
        if (input.fireDurationYears() <= 0) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_FIRE_DURATION,
                    "La durata del FIRE deve essere positiva."
            );
        }

        validateAmount(input.monthlyExpenseToday(), "La spesa mensile");
        validateAmount(input.terminalCapitalToday(), "Il capitale finale");
        validateAmount(input.currentCapital(), "Il patrimonio corrente");
        validateRate(input.annualInflationRate(), "L'inflazione");
        validateRate(input.annualFireReturnRate(), "Il rendimento FIRE");
        validateRate(input.annualAccumulationReturnRate(), "Il rendimento di accumulo");
        validateRate(input.annualContributionGrowthRate(), "La crescita del PAC");

        if (input.method() == FireMethod.SWR && (input.annualSafeWithdrawalRate() == null
                || !Double.isFinite(input.annualSafeWithdrawalRate())
                || input.annualSafeWithdrawalRate() <= 0.0)) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_SWR,
                    "La SWR deve essere positiva."
            );
        }

        AdditionalResourceValidator.validate(input);
    }

    private static void validateAmount(double value, String label) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_AMOUNT,
                    label + " deve essere un importo non negativo e finito."
            );
        }
    }

    private static void validateRate(double value, String label) {
        if (!Double.isFinite(value) || value <= -1.0) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_RATE,
                    label + " deve essere maggiore di -100%."
            );
        }
    }

    private record AccumulationRun(
            List<AccumulationPoint> points,
            double finalBalance,
            double mainPortfolioFinalBalance,
            double totalContributions
    ) {
    }

    private record ResourceAccumulation(
            double[] incomeContributions,
            double[] cumulativeIncomeContributions,
            double[] investedIncomeBalances,
            double[] availableExistingInvestmentsBalances,
            double[] availableFutureLumpSumsBalances,
            double totalIncomeInvested,
            double totalExistingInvestmentContributions,
            double investedIncomeFinalBalance,
            double availableExistingInvestmentsFinalBalance,
            double availableFutureLumpSumsFinalBalance,
            List<ExistingInvestmentResult> existingInvestments,
            List<FutureLumpSumResult> futureLumpSums
    ) {
    }

    private record FireCashFlows(
            double[] grossExpenses,
            double[] additionalIncome,
            double[] netWithdrawals,
            double[] capitalInflows,
            double terminalCapitalInflow,
            double totalCapitalInflows
    ) {
    }

    private record DecumulationRun(
            List<DecumulationPoint> points,
            double finalBalance,
            double totalShortfall,
            Integer depletionMonth
    ) {
    }
}
