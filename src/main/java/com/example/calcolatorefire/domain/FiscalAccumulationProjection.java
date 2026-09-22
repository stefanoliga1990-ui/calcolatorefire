package com.example.calcolatorefire.domain;

import java.util.List;

public record FiscalAccumulationProjection(
        String name,
        boolean availableAtFire,
        FiscalPortfolioState initialState,
        FiscalPortfolioState finalState,
        double totalContributions,
        double totalNetInflows,
        double totalInvestmentReturns,
        double totalStampDuty,
        List<FiscalAccumulationProjectionPoint> points
) {

    public FiscalAccumulationProjection {
        points = List.copyOf(points);
    }
}
