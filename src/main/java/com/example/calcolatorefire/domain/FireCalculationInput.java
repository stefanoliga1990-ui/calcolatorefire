package com.example.calcolatorefire.domain;

import java.util.List;

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
        double annualContributionGrowthRate,
        List<AdditionalResource> additionalResources
) {

    public FireCalculationInput {
        additionalResources = additionalResources == null ? List.of() : List.copyOf(additionalResources);
    }

    public FireCalculationInput(
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
        this(
                method,
                currentAge,
                fireAge,
                fireDurationYears,
                monthlyExpenseToday,
                annualInflationRate,
                annualFireReturnRate,
                annualSafeWithdrawalRate,
                terminalCapitalToday,
                currentCapital,
                annualAccumulationReturnRate,
                annualContributionGrowthRate,
                List.of()
        );
    }
}
