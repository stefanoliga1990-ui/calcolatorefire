package com.example.calcolatorefire.domain;

public record FiscalFireCalculationResult(
        int accumulationMonths,
        int fireMonths,
        FiscalSettings settings,
        Double finiteTarget,
        Double safeWithdrawalRateBaseTarget,
        Double safeWithdrawalRateTarget,
        double selectedTarget,
        double selectedTargetTaxBasis,
        double initialMonthlyContribution,
        FiscalAccumulationPortfolioResult accumulation,
        FiscalDecumulationProjection targetDecumulation,
        FiscalDecumulationProjection personalDecumulation
) {
}
