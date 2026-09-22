package com.example.calcolatorefire.api;

import java.util.List;

import com.example.calcolatorefire.domain.CalculationLimits;
import com.example.calcolatorefire.domain.FireCalculationInput;
import com.example.calcolatorefire.domain.FireMethod;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

public record FireCalculationRequest(
        @NotNull(message = "Il metodo è obbligatorio")
        FireMethod method,

        @NotNull(message = "L'età attuale è obbligatoria")
        @PositiveOrZero(message = "L'età attuale non può essere negativa")
        @Max(value = CalculationLimits.MAX_AGE, message = "L'età attuale non può superare 130 anni")
        Integer currentAge,

        @NotNull(message = "L'età FIRE è obbligatoria")
        @PositiveOrZero(message = "L'età FIRE non può essere negativa")
        @Max(value = CalculationLimits.MAX_AGE, message = "L'età FIRE non può superare 130 anni")
        Integer fireAge,

        @NotNull(message = "La durata del FIRE è obbligatoria")
        @Positive(message = "La durata del FIRE deve essere positiva")
        @Max(value = CalculationLimits.MAX_AGE, message = "La durata del FIRE non può superare 130 anni")
        Integer fireDurationYears,

        @NotNull(message = "La spesa mensile è obbligatoria")
        @PositiveOrZero(message = "La spesa mensile non può essere negativa")
        @DecimalMax(value = CalculationLimits.MAX_AMOUNT_DECIMAL, message = "La spesa mensile non può superare 1000000000000 euro")
        Double monthlyExpenseToday,

        @NotNull(message = "L'inflazione è obbligatoria")
        @DecimalMin(value = "-1.0", inclusive = false, message = "L'inflazione deve essere maggiore di -100%")
        @DecimalMax(value = CalculationLimits.MAX_ANNUAL_RATE_DECIMAL, message = "L'inflazione non può superare 100%")
        Double annualInflationRate,

        @NotNull(message = "Il rendimento FIRE è obbligatorio")
        @DecimalMin(value = "-1.0", inclusive = false, message = "Il rendimento FIRE deve essere maggiore di -100%")
        @DecimalMax(value = CalculationLimits.MAX_ANNUAL_RATE_DECIMAL, message = "Il rendimento FIRE non può superare 100%")
        Double annualFireReturnRate,

        Double annualSafeWithdrawalRate,

        @NotNull(message = "Il capitale finale è obbligatorio")
        @PositiveOrZero(message = "Il capitale finale non può essere negativo")
        @DecimalMax(value = CalculationLimits.MAX_AMOUNT_DECIMAL, message = "Il capitale finale non può superare 1000000000000 euro")
        Double terminalCapitalToday,

        @NotNull(message = "Il patrimonio corrente è obbligatorio")
        @PositiveOrZero(message = "Il patrimonio corrente non può essere negativo")
        @DecimalMax(value = CalculationLimits.MAX_AMOUNT_DECIMAL, message = "Il patrimonio corrente non può superare 1000000000000 euro")
        Double currentCapital,

        @NotNull(message = "Il rendimento di accumulo è obbligatorio")
        @DecimalMin(value = "-1.0", inclusive = false, message = "Il rendimento di accumulo deve essere maggiore di -100%")
        @DecimalMax(value = CalculationLimits.MAX_ANNUAL_RATE_DECIMAL, message = "Il rendimento di accumulo non può superare 100%")
        Double annualAccumulationReturnRate,

        @NotNull(message = "La crescita del PAC è obbligatoria")
        @DecimalMin(value = "-1.0", inclusive = false, message = "La crescita del PAC deve essere maggiore di -100%")
        @DecimalMax(value = CalculationLimits.MAX_ANNUAL_RATE_DECIMAL, message = "La crescita del PAC non può superare 100%")
        Double annualContributionGrowthRate,

        @Size(max = CalculationLimits.MAX_ADDITIONAL_RESOURCES, message = "Non è possibile inserire più di 100 risorse aggiuntive")
        List<@NotNull(message = "La risorsa aggiuntiva non può essere nulla") @Valid AdditionalResourceRequest> additionalResources
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
                annualContributionGrowthRate,
                additionalResources == null
                        ? List.of()
                        : additionalResources.stream().map(AdditionalResourceRequest::toDomain).toList()
        );
    }
}
