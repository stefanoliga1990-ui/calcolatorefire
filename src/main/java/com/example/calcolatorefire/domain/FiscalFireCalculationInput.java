package com.example.calcolatorefire.domain;

import java.util.Map;

/** Fiscal inputs kept separate from the legacy calculation input. */
public record FiscalFireCalculationInput(
        FireCalculationInput fireInput,
        FiscalSettings settings,
        Double currentTaxBasis,
        Map<Integer, Double> existingInvestmentTaxBases
) {

    public FiscalFireCalculationInput {
        existingInvestmentTaxBases = existingInvestmentTaxBases == null
                ? Map.of()
                : Map.copyOf(existingInvestmentTaxBases);
    }

    public FiscalFireCalculationInput(
            FireCalculationInput fireInput,
            FiscalSettings settings,
            Double currentTaxBasis
    ) {
        this(fireInput, settings, currentTaxBasis, Map.of());
    }
}
