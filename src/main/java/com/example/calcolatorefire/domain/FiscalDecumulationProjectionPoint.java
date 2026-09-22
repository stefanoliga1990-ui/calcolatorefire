package com.example.calcolatorefire.domain;

public record FiscalDecumulationProjectionPoint(
        int month,
        FiscalPortfolioState openingState,
        double grossExpense,
        double additionalIncome,
        double requestedNetAmount,
        double netCapitalInflow,
        double taxableGainRatio,
        double requiredGrossSale,
        double grossSale,
        double capitalGainsTax,
        double netProceeds,
        double shortfall,
        double investmentReturn,
        double stampDuty,
        double terminalCapitalInflow,
        FiscalPortfolioState closingState
) {
}
