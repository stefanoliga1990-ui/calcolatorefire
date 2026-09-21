package com.example.calcolatorefire.domain;

public record FutureLumpSumResult(
        int resourceIndex,
        String name,
        AmountBasis amountBasis,
        int receiptMonth,
        double receiptAge,
        double nominalAmountAtReceipt,
        double balanceAtFire,
        Integer fireReceiptMonth
) {
}
