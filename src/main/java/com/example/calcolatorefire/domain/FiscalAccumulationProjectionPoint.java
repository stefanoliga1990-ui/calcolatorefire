package com.example.calcolatorefire.domain;

public record FiscalAccumulationProjectionPoint(
        int month,
        FiscalPortfolioState openingState,
        double investmentReturn,
        double contribution,
        double netInflows,
        double grossBalance,
        double stampDuty,
        FiscalPortfolioState closingState
) {
}
