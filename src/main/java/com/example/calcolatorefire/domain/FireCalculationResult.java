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
        Double safeWithdrawalRateTarget,
        double selectedTarget,
        double recommendedTarget,
        double selectedTargetToday,
        double recommendedTargetToday,
        double projectedCurrentCapitalAtFire,
        double capitalGap,
        double initialMonthlyContribution,
        double totalNominalContributions,
        double projectedAccumulationFinalBalance,
        Double finiteTargetProjectedFinalBalance,
        double targetDecumulationFinalBalance,
        double personalDecumulationStartBalance,
        double personalDecumulationFinalBalance,
        double totalShortfall,
        Integer depletionMonth,
        List<AccumulationPoint> accumulationProjection,
        List<DecumulationPoint> decumulationProjection
) {
    public FireCalculationResult {
        accumulationProjection = List.copyOf(accumulationProjection);
        decumulationProjection = List.copyOf(decumulationProjection);
    }
}
