package com.example.calcolatorefire.domain;

public record ExistingInvestmentPoint(
        int month,
        double age,
        double openingBalance,
        double investmentReturn,
        double contribution,
        double closingBalance,
        double cumulativeContributions
) {
}
