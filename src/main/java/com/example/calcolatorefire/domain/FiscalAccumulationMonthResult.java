package com.example.calcolatorefire.domain;

public record FiscalAccumulationMonthResult(
        FiscalPortfolioState openingState,
        double investmentReturn,
        double contribution,
        double netInflows,
        double grossBalance,
        double stampDuty,
        FiscalPortfolioState closingState
) {
}
