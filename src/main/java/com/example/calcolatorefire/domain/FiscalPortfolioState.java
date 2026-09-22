package com.example.calcolatorefire.domain;

public record FiscalPortfolioState(
        double balance,
        double taxBasis
) {

    public FiscalPortfolioState {
        if (!Double.isFinite(balance) || balance < 0.0) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_AMOUNT,
                    "Il saldo fiscale del portafoglio deve essere finito e non negativo."
            );
        }
        if (!Double.isFinite(taxBasis) || taxBasis < 0.0) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_TAX_BASIS,
                    "Il costo fiscale deve essere finito e non negativo."
            );
        }
    }

    public double latentGain() {
        return Math.max(0.0, balance - taxBasis);
    }

    public double taxableGainRatio() {
        return balance == 0.0 ? 0.0 : latentGain() / balance;
    }
}
