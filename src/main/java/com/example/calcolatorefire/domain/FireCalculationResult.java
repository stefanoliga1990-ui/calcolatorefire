package com.example.calcolatorefire.domain;

import java.util.List;

public record FireCalculationResult(
        int accumulationMonths,
        int fireMonths,
        double monthlyInflationRate,
        double monthlyFireReturnRate,
        double monthlyRealFireReturnRate,
        double monthlyAccumulationReturnRate,
        double monthlyContributionGrowthRate,
        double firstMonthlyWithdrawal,
        double terminalCapitalAtFire,
        double terminalCapitalNominalAtEnd,
        Double finiteTarget,
        Double safeWithdrawalRateBaseTarget,
        Double safeWithdrawalRateTarget,
        double selectedTarget,
        double selectedTargetToday,
        double firstMonthlyAdditionalIncome,
        double firstMonthlyNetWithdrawal,
        double projectedCurrentCapitalAtFire,
        double capitalGap,
        double initialMonthlyContribution,
        double totalNominalContributions,
        double totalNominalAdditionalIncomeInvested,
        double totalNominalExistingInvestmentContributions,
        double projectedMainPortfolioFinalBalance,
        double projectedInvestedIncomeFinalBalance,
        double projectedAvailableExistingInvestmentsFinalBalance,
        double projectedAvailableFutureLumpSumsFinalBalance,
        double projectedAccumulationFinalBalance,
        Double finiteTargetProjectedFinalBalance,
        double targetDecumulationFinalBalance,
        double personalDecumulationStartBalance,
        double personalDecumulationFinalBalance,
        double totalCapitalInflows,
        double terminalCapitalInflow,
        double totalShortfall,
        Integer depletionMonth,
        List<AccumulationPoint> accumulationProjection,
        List<DecumulationPoint> decumulationProjection,
        List<ExistingInvestmentResult> existingInvestments,
        List<PeriodicIncomeResult> periodicIncomes,
        List<FutureLumpSumResult> futureLumpSums
) {
    public FireCalculationResult {
        accumulationProjection = List.copyOf(accumulationProjection);
        decumulationProjection = List.copyOf(decumulationProjection);
        existingInvestments = List.copyOf(existingInvestments);
        periodicIncomes = List.copyOf(periodicIncomes);
        futureLumpSums = List.copyOf(futureLumpSums);
    }
}
