package com.example.calcolatorefire.api;

import com.example.calcolatorefire.domain.ExistingInvestment;
import com.example.calcolatorefire.domain.CalculationLimits;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ExistingInvestmentRequest(
        @Size(max = 100, message = "Il nome non può superare 100 caratteri")
        String name,

        @NotNull(message = "Il patrimonio attuale della risorsa è obbligatorio")
        @PositiveOrZero(message = "Il patrimonio attuale della risorsa non può essere negativo")
        @DecimalMax(value = CalculationLimits.MAX_AMOUNT_DECIMAL, message = "Il patrimonio attuale della risorsa non può superare 1000000000000 euro")
        Double currentCapital,

        @PositiveOrZero(message = "Il costo fiscale della risorsa non può essere negativo")
        @DecimalMax(value = CalculationLimits.MAX_AMOUNT_DECIMAL, message = "Il costo fiscale della risorsa non può superare 1000000000000 euro")
        Double taxBasis,

        @NotNull(message = "Il versamento mensile della risorsa è obbligatorio")
        @PositiveOrZero(message = "Il versamento mensile della risorsa non può essere negativo")
        @DecimalMax(value = CalculationLimits.MAX_AMOUNT_DECIMAL, message = "Il versamento mensile della risorsa non può superare 1000000000000 euro")
        Double initialMonthlyContribution,

        @PositiveOrZero(message = "L'età iniziale dei versamenti non può essere negativa")
        @Max(value = CalculationLimits.MAX_AGE, message = "L'età iniziale dei versamenti non può superare 130 anni")
        Integer contributionStartAge,

        @PositiveOrZero(message = "L'età finale dei versamenti non può essere negativa")
        @Max(value = CalculationLimits.MAX_AGE, message = "L'età finale dei versamenti non può superare 130 anni")
        Integer contributionEndAge,

        @NotNull(message = "Il rendimento annuo della risorsa è obbligatorio")
        @DecimalMin(value = "-1.0", inclusive = false, message = "Il rendimento annuo della risorsa deve essere maggiore di -100%")
        @DecimalMax(value = CalculationLimits.MAX_ANNUAL_RATE_DECIMAL, message = "Il rendimento annuo della risorsa non può superare 100%")
        Double annualReturnRate,

        @NotNull(message = "La crescita dei versamenti della risorsa è obbligatoria")
        @DecimalMin(value = "-1.0", inclusive = false, message = "La crescita dei versamenti della risorsa deve essere maggiore di -100%")
        @DecimalMax(value = CalculationLimits.MAX_ANNUAL_RATE_DECIMAL, message = "La crescita dei versamenti della risorsa non può superare 100%")
        Double annualContributionGrowthRate,

        @NotNull(message = "La disponibilità della risorsa al FIRE è obbligatoria")
        Boolean availableAtFire
) implements AdditionalResourceRequest {

    @Override
    public ExistingInvestment toDomain() {
        return new ExistingInvestment(
                name,
                currentCapital,
                initialMonthlyContribution,
                contributionStartAge,
                contributionEndAge,
                annualReturnRate,
                annualContributionGrowthRate,
                availableAtFire
        );
    }
}
