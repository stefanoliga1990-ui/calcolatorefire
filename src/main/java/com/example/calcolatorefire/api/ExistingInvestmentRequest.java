package com.example.calcolatorefire.api;

import com.example.calcolatorefire.domain.ExistingInvestment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ExistingInvestmentRequest(
        @Size(max = 100, message = "Il nome non può superare 100 caratteri")
        String name,

        @NotNull(message = "Il patrimonio attuale della risorsa è obbligatorio")
        @PositiveOrZero(message = "Il patrimonio attuale della risorsa non può essere negativo")
        Double currentCapital,

        @NotNull(message = "Il versamento mensile della risorsa è obbligatorio")
        @PositiveOrZero(message = "Il versamento mensile della risorsa non può essere negativo")
        Double initialMonthlyContribution,

        @PositiveOrZero(message = "L'età iniziale dei versamenti non può essere negativa")
        Integer contributionStartAge,

        @PositiveOrZero(message = "L'età finale dei versamenti non può essere negativa")
        Integer contributionEndAge,

        @NotNull(message = "Il rendimento annuo della risorsa è obbligatorio")
        @DecimalMin(value = "-1.0", inclusive = false, message = "Il rendimento annuo della risorsa deve essere maggiore di -100%")
        Double annualReturnRate,

        @NotNull(message = "La crescita dei versamenti della risorsa è obbligatoria")
        @DecimalMin(value = "-1.0", inclusive = false, message = "La crescita dei versamenti della risorsa deve essere maggiore di -100%")
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
