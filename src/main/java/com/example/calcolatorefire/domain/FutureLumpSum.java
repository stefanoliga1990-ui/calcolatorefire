package com.example.calcolatorefire.domain;

public record FutureLumpSum(
        String name,
        double amount,
        AmountBasis amountBasis,
        int receiptAge,
        boolean investAfterReceipt,
        double annualReturnRateAfterReceipt
) implements AdditionalResource {
}
