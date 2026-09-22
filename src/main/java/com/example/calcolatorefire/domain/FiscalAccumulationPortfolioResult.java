package com.example.calcolatorefire.domain;

import java.util.List;

public record FiscalAccumulationPortfolioResult(
        List<FiscalAccumulationProjection> projections,
        FiscalPortfolioState availableAtFireState,
        double totalStampDuty,
        double availableAtFireStampDuty
) {

    public FiscalAccumulationPortfolioResult {
        projections = List.copyOf(projections);
    }
}
