package com.example.calcolatorefire.domain;

public record AccumulationPoint(
        int month,
        double age,
        double openingBalance,
        double investmentReturn,
        double contribution,
        double additionalIncomeContribution,
        double closingBalance,
        double cumulativeContributions,
        double cumulativeAdditionalIncome,
        double availableExistingInvestmentsBalance,
        double availableFutureLumpSumsBalance,
        double totalAvailableBalance
) {
}
