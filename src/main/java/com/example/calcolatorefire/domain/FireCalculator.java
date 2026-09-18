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

        Double finiteTarget = input.method() == FireMethod.FINITE
                ? finiteTarget(firstWithdrawal, terminalCapitalAtFire, monthlyRealFireReturn, fireMonths)
                : null;
        Double swrTarget = input.method() == FireMethod.SWR
                ? firstWithdrawal * 12.0 / input.annualSafeWithdrawalRate()
                : null;
        double selectedTarget = input.method() == FireMethod.FINITE ? finiteTarget : swrTarget;
        double selectedTargetToday = selectedTarget / inflationToFire;

        double projectedCurrentCapitalAtFire = input.currentCapital()
                * Math.pow(1.0 + monthlyAccumulationReturn, accumulationMonths);
        double capitalGap = Math.max(0.0, selectedTarget - projectedCurrentCapitalAtFire);

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
                initialContribution
        );

        Double finiteTargetProjectedFinal = finiteTarget == null ? null : projectRawFinalBalance(
                finiteTarget, firstWithdrawal, monthlyInflation, monthlyFireReturn, fireMonths
        );

        DecumulationRun targetRun = projectDecumulation(
                selectedTarget,
                input.fireAge(),
                firstWithdrawal,
                monthlyInflation,
                monthlyFireReturn,
                fireMonths
        );

        double personalStart = Math.max(selectedTarget, accumulation.finalBalance());
        DecumulationRun personalRun = projectDecumulation(
                personalStart,
                input.fireAge(),
                firstWithdrawal,
                monthlyInflation,
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
                swrTarget,
                selectedTarget,
                selectedTargetToday,
                projectedCurrentCapitalAtFire,
                capitalGap,
                initialContribution,
                accumulation.totalContributions(),
                accumulation.finalBalance(),
                finiteTargetProjectedFinal,
                targetRun.finalBalance(),
                personalStart,
                personalRun.finalBalance(),
                personalRun.totalShortfall(),
                personalRun.depletionMonth(),
                accumulation.points(),
                personalRun.points()
        );
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
            double initialContribution
    ) {
        List<AccumulationPoint> points = new ArrayList<>(months + 1);
        double balance = input.currentCapital();
        double cumulativeContributions = 0.0;
        points.add(new AccumulationPoint(0, input.currentAge(), balance, 0.0, 0.0, balance, 0.0));

        for (int month = 1; month <= months; month++) {
            double opening = balance;
            double investmentReturn = opening * monthlyReturn;
            double contribution = initialContribution * Math.pow(1.0 + monthlyGrowth, month - 1);
            cumulativeContributions += contribution;
            balance = opening + investmentReturn + contribution;
            points.add(new AccumulationPoint(
                    month,
                    input.currentAge() + month / 12.0,
                    opening,
                    investmentReturn,
                    contribution,
                    balance,
                    cumulativeContributions
            ));
        }
        return new AccumulationRun(List.copyOf(points), balance, cumulativeContributions);
    }

    private static DecumulationRun projectDecumulation(
            double startingBalance,
            int fireAge,
            double firstWithdrawal,
            double monthlyInflation,
            double monthlyReturn,
            int months
    ) {
        List<DecumulationPoint> points = new ArrayList<>(months + 1);
        double balance = startingBalance;
        double totalShortfall = 0.0;
        Integer depletionMonth = null;
        points.add(new DecumulationPoint(0, fireAge, balance, 0.0, 0.0, 0.0, 0.0, balance));

        for (int month = 1; month <= months; month++) {
            double opening = balance;
            double scheduledWithdrawal = firstWithdrawal * Math.pow(1.0 + monthlyInflation, month - 1);
            double actualWithdrawal = Math.min(opening, scheduledWithdrawal);
            double shortfall = scheduledWithdrawal - actualWithdrawal;
            double remaining = opening - actualWithdrawal;
            double investmentReturn = remaining * monthlyReturn;
            balance = Math.max(0.0, remaining + investmentReturn);
            totalShortfall += shortfall;
            if (depletionMonth == null && shortfall > MONEY_TOLERANCE) {
                depletionMonth = month;
            }
            points.add(new DecumulationPoint(
                    month,
                    fireAge + month / 12.0,
                    opening,
                    scheduledWithdrawal,
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
            double firstWithdrawal,
            double monthlyInflation,
            double monthlyReturn,
            int months
    ) {
        double balance = startingBalance;
        for (int month = 1; month <= months; month++) {
            double withdrawal = firstWithdrawal * Math.pow(1.0 + monthlyInflation, month - 1);
            balance = (balance - withdrawal) * (1.0 + monthlyReturn);
        }
        return balance;
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
            double totalContributions
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
