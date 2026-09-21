package com.example.calcolatorefire.domain;

public record ExistingInvestment(
        String name,
        double currentCapital,
        double initialMonthlyContribution,
        Integer contributionStartAge,
        Integer contributionEndAge,
        double annualReturnRate,
        double annualContributionGrowthRate,
        boolean availableAtFire
) implements AdditionalResource {
}
