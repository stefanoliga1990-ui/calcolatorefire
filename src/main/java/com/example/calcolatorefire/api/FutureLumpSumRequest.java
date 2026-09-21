package com.example.calcolatorefire.api;

import com.example.calcolatorefire.domain.AmountBasis;
import com.example.calcolatorefire.domain.FutureLumpSum;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record FutureLumpSumRequest(
        @Size(max = 100, message = "Il nome non può superare 100 caratteri")
        String name,

        @NotNull(message = "L'importo del capitale futuro è obbligatorio")
        @PositiveOrZero(message = "L'importo del capitale futuro non può essere negativo")
        Double amount,

        @NotNull(message = "La base dell'importo è obbligatoria")
        AmountBasis amountBasis,

        @NotNull(message = "L'età di ricezione è obbligatoria")
        @PositiveOrZero(message = "L'età di ricezione non può essere negativa")
        Integer receiptAge,

        @NotNull(message = "La scelta di investimento dopo la ricezione è obbligatoria")
        Boolean investAfterReceipt,

        @NotNull(message = "Il rendimento dopo la ricezione è obbligatorio")
        @DecimalMin(value = "-1.0", inclusive = false, message = "Il rendimento dopo la ricezione deve essere maggiore di -100%")
        Double annualReturnRateAfterReceipt
) implements AdditionalResourceRequest {

    @Override
    public FutureLumpSum toDomain() {
        return new FutureLumpSum(
                name,
                amount,
                amountBasis,
                receiptAge,
                investAfterReceipt,
                annualReturnRateAfterReceipt
        );
    }
}
