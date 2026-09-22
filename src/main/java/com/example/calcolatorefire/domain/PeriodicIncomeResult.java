package com.example.calcolatorefire.domain;

import java.util.List;

public record PeriodicIncomeResult(
        int resourceIndex,
        String name,
        boolean investBeforeFire,
        boolean offsetDuringFire,
        List<PeriodicIncomePoint> projection
) {
    public PeriodicIncomeResult {
        projection = List.copyOf(projection);
    }
}
