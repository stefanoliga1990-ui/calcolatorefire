package com.example.calcolatorefire.api;

import com.example.calcolatorefire.domain.FireCalculationInput;
import com.example.calcolatorefire.domain.FireMethod;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record FireCalculationRequest(
        @NotNull(message = "Il metodo è obbligatorio")
        FireMethod method,

        @NotNull(message = "L'età attuale è obbligatoria")
        @PositiveOrZero(message = "L'età attuale non può essere negativa")
        Integer currentAge,

        @NotNull(message = "L'età FIRE è obbligatoria")
        @PositiveOrZero(message = "L'età FIRE non può essere negativa")
        Integer fireAge,

        @NotNull(message = "La durata del FIRE è obbligatoria")
        @Positive(message = "La durata del FIRE deve essere positiva")
        Integer fireDurationYears,

        @NotNull(message = "La spesa mensile è obbligatoria")
        @PositiveOrZero(message = "La spesa mensile non può essere negativa")
        Double monthlyExpenseToday,

        @NotNull(message = "L'inflazione è obbligatoria")
        @DecimalMin(value = "-1.0", inclusive = false, message = "L'inflazione deve essere maggiore di -100%")
        Double annualInflationRate,

        @NotNull(message = "Il rendimento FIRE è obbligatorio")
        @DecimalMin(value = "-1.0", inclusive = false, message = "Il rendimento FIRE deve essere maggiore di -100%")
        Double annualFireReturnRate,

        Double annualSafeWithdrawalRate,

        @NotNull(message = "Il capitale finale è obbligatorio")
        @PositiveOrZero(message = "Il capitale finale non può essere negativo")
        Double terminalCapitalToday,

        @NotNull(message = "Il patrimonio corrente è obbligatorio")
        @PositiveOrZero(message = "Il patrimonio corrente non può essere negativo")
        Double currentCapital,

        @NotNull(message = "Il rendimento di accumulo è obbligatorio")
        @DecimalMin(value = "-1.0", inclusive = false, message = "Il rendimento di accumulo deve essere maggiore di -100%")
        Double annualAccumulationReturnRate,

        @NotNull(message = "La crescita del PAC è obbligatoria")
        @DecimalMin(value = "-1.0", inclusive = false, message = "La crescita del PAC deve essere maggiore di -100%")
        Double annualContributionGrowthRate
) {

    public FireCalculationInput toDomain() {
        return new FireCalculationInput(
                method,
                currentAge,
                fireAge,
                fireDurationYears,
                monthlyExpenseToday,
                annualInflationRate,
                annualFireReturnRate,
                annualSafeWithdrawalRate,
                terminalCapitalToday,
                currentCapital,
                annualAccumulationReturnRate,
                annualContributionGrowthRate
        );
    }
}
