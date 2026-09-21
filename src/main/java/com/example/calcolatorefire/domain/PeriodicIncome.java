package com.example.calcolatorefire.domain;

public record PeriodicIncome(
        String name,
        double monthlyAmountToday,
        double annualGrowthRate,
        int startAge,
        Integer endAge,
        boolean investBeforeFire,
        boolean offsetDuringFire
) implements AdditionalResource {
}
