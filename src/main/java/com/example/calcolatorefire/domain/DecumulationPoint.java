package com.example.calcolatorefire.domain;

public record DecumulationPoint(
        int month,
        double age,
        double openingBalance,
        double scheduledWithdrawal,
        double actualWithdrawal,
        double shortfall,
        double investmentReturn,
        double closingBalance
) {
}
