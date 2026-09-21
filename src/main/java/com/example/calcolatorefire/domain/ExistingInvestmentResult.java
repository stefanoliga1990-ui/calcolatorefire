package com.example.calcolatorefire.domain;

import java.util.List;

public record ExistingInvestmentResult(
        int resourceIndex,
        String name,
        boolean availableAtFire,
        double totalNominalContributions,
        double finalBalance,
        List<ExistingInvestmentPoint> projection
) {
    public ExistingInvestmentResult {
        projection = List.copyOf(projection);
    }
}
