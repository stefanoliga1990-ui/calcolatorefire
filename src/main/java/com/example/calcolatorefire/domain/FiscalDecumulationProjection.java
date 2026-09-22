package com.example.calcolatorefire.domain;

import java.util.List;

public record FiscalDecumulationProjection(
        FiscalPortfolioState initialState,
        FiscalPortfolioState finalState,
        double totalGrossSales,
        double totalCapitalGainsTax,
        double totalNetProceeds,
        double totalStampDuty,
        double totalShortfall,
        Integer depletionMonth,
        List<FiscalDecumulationProjectionPoint> points
) {

    public FiscalDecumulationProjection {
        points = List.copyOf(points);
    }
}
