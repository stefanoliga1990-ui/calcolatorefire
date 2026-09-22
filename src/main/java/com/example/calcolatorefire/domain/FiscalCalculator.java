package com.example.calcolatorefire.domain;

public final class FiscalCalculator {

    public FiscalPortfolioState initialState(double balance, Double taxBasis) {
        validateInputAmount(balance, CalculationErrorCode.INVALID_AMOUNT, "patrimonio");
        double effectiveTaxBasis = taxBasis == null ? balance : taxBasis;
        validateInputAmount(effectiveTaxBasis, CalculationErrorCode.INVALID_TAX_BASIS, "costo fiscale");
        return new FiscalPortfolioState(balance, effectiveTaxBasis);
    }

    public double monthlyStampDutyRate(FiscalSettings settings) {
        requireSettings(settings);
        return 1.0 - Math.pow(1.0 - settings.annualStampDutyRate(), 1.0 / 12.0);
    }

    public FiscalAccumulationMonthResult accumulateMonth(
            FiscalPortfolioState openingState,
            double monthlyReturnRate,
            double contribution,
            double netInflows,
            FiscalSettings settings
    ) {
        requireState(openingState);
        requireSettings(settings);
        validateMonthlyReturnRate(monthlyReturnRate);
        validateInputAmount(contribution, CalculationErrorCode.INVALID_AMOUNT, "versamento");
        validateInputAmount(netInflows, CalculationErrorCode.INVALID_AMOUNT, "apporto netto");

        double investmentReturn = openingState.balance() * monthlyReturnRate;
        double grossBalance = openingState.balance() + investmentReturn + contribution + netInflows;
        requireFiniteValue(investmentReturn, "rendimento del mese");
        requireCalculatedAmount(grossBalance, "saldo prima del bollo");

        double stampDuty = grossBalance * monthlyStampDutyRate(settings);
        double closingBalance = Math.max(0.0, grossBalance - stampDuty);
        double closingTaxBasis = openingState.taxBasis() + contribution + netInflows;
        requireCalculatedAmount(stampDuty, "imposta di bollo");
        requireCalculatedAmount(closingTaxBasis, "costo fiscale finale");

        FiscalPortfolioState closingState = new FiscalPortfolioState(closingBalance, closingTaxBasis);
        return new FiscalAccumulationMonthResult(
                openingState,
                investmentReturn,
                contribution,
                netInflows,
                grossBalance,
                stampDuty,
                closingState
        );
    }

    public FiscalDecumulationMonthResult decumulateMonth(
            FiscalPortfolioState openingState,
            double requestedNetAmount,
            double netCapitalInflow,
            double monthlyReturnRate,
            FiscalSettings settings
    ) {
        requireState(openingState);
        requireSettings(settings);
        validateInputAmount(requestedNetAmount, CalculationErrorCode.INVALID_AMOUNT, "fabbisogno netto");
        validateInputAmount(netCapitalInflow, CalculationErrorCode.INVALID_AMOUNT, "capitale netto ricevuto");
        validateMonthlyReturnRate(monthlyReturnRate);

        double availableBalance = openingState.balance() + netCapitalInflow;
        double availableTaxBasis = openingState.taxBasis() + netCapitalInflow;
        requireCalculatedAmount(availableBalance, "saldo disponibile");
        requireCalculatedAmount(availableTaxBasis, "costo fiscale disponibile");

        double taxableGainRatio = availableBalance == 0.0
                ? 0.0
                : Math.max(0.0, availableBalance - availableTaxBasis) / availableBalance;
        double netSaleFactor = 1.0 - settings.capitalGainsTaxRate() * taxableGainRatio;
        double requiredGrossSale = requestedNetAmount / netSaleFactor;
        double grossSale = Math.min(availableBalance, requiredGrossSale);
        double capitalGainsTax = grossSale * taxableGainRatio * settings.capitalGainsTaxRate();
        double netProceeds = grossSale - capitalGainsTax;
        double shortfall = Math.max(0.0, requestedNetAmount - netProceeds);

        double remainingTaxBasis = remainingTaxBasis(availableBalance, availableTaxBasis, grossSale);
        double balanceAfterSale = Math.max(0.0, availableBalance - grossSale);
        double investmentReturn = balanceAfterSale * monthlyReturnRate;
        double grossEndBalance = balanceAfterSale + investmentReturn;
        double stampDuty = grossEndBalance * monthlyStampDutyRate(settings);
        double closingBalance = Math.max(0.0, grossEndBalance - stampDuty);
        if (closingBalance == 0.0) {
            remainingTaxBasis = 0.0;
        }

        requireCalculatedAmount(requiredGrossSale, "vendita lorda richiesta");
        requireCalculatedAmount(grossSale, "vendita lorda");
        requireCalculatedAmount(capitalGainsTax, "imposta sulla plusvalenza");
        requireCalculatedAmount(netProceeds, "ricavo netto");
        requireCalculatedAmount(shortfall, "shortfall");
        requireCalculatedAmount(remainingTaxBasis, "costo fiscale residuo");
        requireFiniteValue(investmentReturn, "rendimento del mese");
        requireCalculatedAmount(grossEndBalance, "saldo prima del bollo");
        requireCalculatedAmount(stampDuty, "imposta di bollo");

        FiscalPortfolioState closingState = new FiscalPortfolioState(closingBalance, remainingTaxBasis);
        return new FiscalDecumulationMonthResult(
                openingState,
                netCapitalInflow,
                availableBalance,
                availableTaxBasis,
                requestedNetAmount,
                taxableGainRatio,
                grossSale,
                capitalGainsTax,
                netProceeds,
                shortfall,
                remainingTaxBasis,
                investmentReturn,
                grossEndBalance,
                stampDuty,
                closingState
        );
    }

    private static double remainingTaxBasis(double balance, double taxBasis, double grossSale) {
        if (balance == 0.0 || grossSale >= balance) {
            return 0.0;
        }
        return Math.max(0.0, taxBasis * (1.0 - grossSale / balance));
    }

    private static void validateInputAmount(double amount, CalculationErrorCode code, String label) {
        if (!Double.isFinite(amount) || amount < 0.0 || amount > CalculationLimits.MAX_AMOUNT) {
            throw new FireCalculationException(
                    code,
                    "Il valore di " + label + " deve essere finito, non negativo e non superiore a "
                            + CalculationLimits.MAX_AMOUNT_DECIMAL + " euro."
            );
        }
    }

    private static void validateMonthlyReturnRate(double monthlyReturnRate) {
        if (!Double.isFinite(monthlyReturnRate) || monthlyReturnRate <= -1.0) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_RATE,
                    "Il rendimento mensile deve essere finito e maggiore di -100%."
            );
        }
    }

    private static void requireCalculatedAmount(double amount, String label) {
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new FireCalculationException(
                    CalculationErrorCode.FISCAL_SOLUTION_NOT_FOUND,
                    "Il calcolo fiscale non produce un valore valido per " + label + "."
            );
        }
    }

    private static void requireFiniteValue(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new FireCalculationException(
                    CalculationErrorCode.FISCAL_SOLUTION_NOT_FOUND,
                    "Il calcolo fiscale non produce un valore finito per " + label + "."
            );
        }
    }

    private static void requireState(FiscalPortfolioState state) {
        if (state == null) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_TAX_BASIS,
                    "Lo stato fiscale del portafoglio è obbligatorio."
            );
        }
    }

    private static void requireSettings(FiscalSettings settings) {
        if (settings == null) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_CAPITAL_GAINS_TAX_RATE,
                    "Le impostazioni fiscali sono obbligatorie."
            );
        }
    }
}
