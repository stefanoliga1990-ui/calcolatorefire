package com.example.calcolatorefire.domain;

public record FireCalculationInput(
        FireMethod method,
        int currentAge,
        int fireAge,
        int fireDurationYears,
        double monthlyExpenseToday,
        double annualInflationRate,
        double annualFireReturnRate,
        Double annualSafeWithdrawalRate,
        double terminalCapitalToday,
        double currentCapital,
        double annualAccumulationReturnRate,
        double annualContributionGrowthRate
) {
}
