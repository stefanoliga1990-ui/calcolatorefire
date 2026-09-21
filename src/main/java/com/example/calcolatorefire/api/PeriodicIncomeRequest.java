package com.example.calcolatorefire.api;

import com.example.calcolatorefire.domain.PeriodicIncome;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record PeriodicIncomeRequest(
        @Size(max = 100, message = "Il nome non può superare 100 caratteri")
        String name,

        @NotNull(message = "L'importo mensile della rendita è obbligatorio")
        @PositiveOrZero(message = "L'importo mensile della rendita non può essere negativo")
        Double monthlyAmountToday,

        @NotNull(message = "La crescita annua della rendita è obbligatoria")
        @DecimalMin(value = "-1.0", inclusive = false, message = "La crescita annua della rendita deve essere maggiore di -100%")
        Double annualGrowthRate,

        @NotNull(message = "L'età iniziale della rendita è obbligatoria")
        @PositiveOrZero(message = "L'età iniziale della rendita non può essere negativa")
        Integer startAge,

        @PositiveOrZero(message = "L'età finale della rendita non può essere negativa")
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
