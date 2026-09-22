package com.example.calcolatorefire.domain;

public record FiscalDecumulationMonthResult(
        FiscalPortfolioState openingState,
        double netCapitalInflow,
        double availableBalance,
        double availableTaxBasis,
        double requestedNetAmount,
        double taxableGainRatio,
        double grossSale,
        double capitalGainsTax,
        double netProceeds,
        double shortfall,
        double remainingTaxBasis,
        double investmentReturn,
        double grossEndBalance,
        double stampDuty,
        FiscalPortfolioState closingState
) {
}
