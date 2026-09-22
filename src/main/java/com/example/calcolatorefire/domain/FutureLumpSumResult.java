package com.example.calcolatorefire.domain;

import java.util.List;

public record FutureLumpSumResult(
        int resourceIndex,
        String name,
        AmountBasis amountBasis,
        int receiptMonth,
        double receiptAge,
        double nominalAmountAtReceipt,
        double balanceAtFire,
        Integer fireReceiptMonth,
        List<FutureLumpSumPoint> projection
) {
    public FutureLumpSumResult {
        projection = List.copyOf(projection);
    }
}
