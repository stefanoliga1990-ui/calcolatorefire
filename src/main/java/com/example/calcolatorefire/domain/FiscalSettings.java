package com.example.calcolatorefire.domain;

public record FiscalSettings(
        double capitalGainsTaxRate,
        double annualStampDutyRate
) {

    public static final double DEFAULT_CAPITAL_GAINS_TAX_RATE = 0.26;
    public static final double DEFAULT_ANNUAL_STAMP_DUTY_RATE = 0.002;

    public FiscalSettings {
        validateRate(
                capitalGainsTaxRate,
                CalculationErrorCode.INVALID_CAPITAL_GAINS_TAX_RATE,
                "L'aliquota sulle plusvalenze deve essere finita e compresa tra 0% incluso e 100% escluso."
        );
        validateRate(
                annualStampDutyRate,
                CalculationErrorCode.INVALID_STAMP_DUTY_RATE,
                "L'imposta di bollo deve essere finita e compresa tra 0% incluso e 100% escluso."
        );
    }

    public static FiscalSettings defaults() {
        return new FiscalSettings(DEFAULT_CAPITAL_GAINS_TAX_RATE, DEFAULT_ANNUAL_STAMP_DUTY_RATE);
    }

    public static FiscalSettings disabled() {
        return new FiscalSettings(0.0, 0.0);
    }

    private static void validateRate(double rate, CalculationErrorCode code, String message) {
        if (!Double.isFinite(rate) || rate < 0.0 || rate >= 1.0) {
            throw new FireCalculationException(code, message);
        }
    }
}
