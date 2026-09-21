package com.example.calcolatorefire.domain;

public record DecumulationPoint(
        int month,
        double age,
        double openingBalance,
        double grossExpense,
        double additionalIncome,
        double scheduledWithdrawal,
        double capitalInflow,
        double terminalCapitalInflow,
        double actualWithdrawal,
        double shortfall,
        double investmentReturn,
        double closingBalance
) {
}
