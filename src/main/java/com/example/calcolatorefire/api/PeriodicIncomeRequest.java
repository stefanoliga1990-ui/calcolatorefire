package com.example.calcolatorefire.api;

import com.example.calcolatorefire.domain.PeriodicIncome;
import com.example.calcolatorefire.domain.CalculationLimits;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record PeriodicIncomeRequest(
        @Size(max = 100, message = "Il nome non può superare 100 caratteri")
        String name,

        @NotNull(message = "L'importo mensile della rendita è obbligatorio")
        @PositiveOrZero(message = "L'importo mensile della rendita non può essere negativo")
        @DecimalMax(value = CalculationLimits.MAX_AMOUNT_DECIMAL, message = "L'importo mensile della rendita non può superare 1000000000000 euro")
        Double monthlyAmountToday,

        @NotNull(message = "La crescita annua della rendita è obbligatoria")
        @DecimalMin(value = "-1.0", inclusive = false, message = "La crescita annua della rendita deve essere maggiore di -100%")
        @DecimalMax(value = CalculationLimits.MAX_ANNUAL_RATE_DECIMAL, message = "La crescita annua della rendita non può superare 100%")
        Double annualGrowthRate,

        @NotNull(message = "L'età iniziale della rendita è obbligatoria")
        @PositiveOrZero(message = "L'età iniziale della rendita non può essere negativa")
        @Max(value = CalculationLimits.MAX_AGE, message = "L'età iniziale della rendita non può superare 130 anni")
        Integer startAge,

        @PositiveOrZero(message = "L'età finale della rendita non può essere negativa")
        @Max(value = CalculationLimits.MAX_AGE, message = "L'età finale della rendita non può superare 130 anni")
        Integer endAge,

        @NotNull(message = "La scelta di investimento prima del FIRE è obbligatoria")
        Boolean investBeforeFire,

        @NotNull(message = "La scelta di utilizzo durante il FIRE è obbligatoria")
        Boolean offsetDuringFire
) implements AdditionalResourceRequest {

    @Override
    public PeriodicIncome toDomain() {
        return new PeriodicIncome(
                name,
                monthlyAmountToday,
                annualGrowthRate,
                startAge,
                endAge,
                investBeforeFire,
                offsetDuringFire
        );
    }
}
