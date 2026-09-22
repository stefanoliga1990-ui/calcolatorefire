package com.example.calcolatorefire.domain;

final class AdditionalResourceValidator {

    private AdditionalResourceValidator() {
    }

    static void validate(FireCalculationInput input) {
        if (input.additionalResources().isEmpty()) {
            return;
        }

        int horizonEndAge;
        try {
            horizonEndAge = Math.addExact(input.fireAge(), input.fireDurationYears());
        } catch (ArithmeticException exception) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_RESOURCE_PERIOD,
                    "L'orizzonte delle risorse aggiuntive è troppo grande."
            );
        }

        for (int index = 0; index < input.additionalResources().size(); index++) {
            AdditionalResource resource = input.additionalResources().get(index);
            String label = "La risorsa aggiuntiva " + (index + 1);

            if (resource instanceof ExistingInvestment investment) {
                validateExistingInvestment(investment, input.currentAge(), input.fireAge(), label);
            } else if (resource instanceof PeriodicIncome income) {
                validatePeriodicIncome(income, input.currentAge(), input.fireAge(), horizonEndAge, label);
            } else if (resource instanceof FutureLumpSum lumpSum) {
                validateFutureLumpSum(lumpSum, input.currentAge(), horizonEndAge, label);
            } else {
                throw new FireCalculationException(
                        CalculationErrorCode.INVALID_RESOURCE,
                        label + " ha un tipo non riconosciuto."
                );
            }
        }
    }

    private static void validateExistingInvestment(
            ExistingInvestment investment,
            int currentAge,
            int fireAge,
            String label
    ) {
        validateName(investment.name(), label);
        validateAmount(investment.currentCapital(), label + ": il patrimonio attuale");
        validateAmount(investment.initialMonthlyContribution(), label + ": il versamento mensile");
        validateRate(investment.annualReturnRate(), label + ": il rendimento annuo");
        validateRate(investment.annualContributionGrowthRate(), label + ": la crescita dei versamenti");

        Integer startAge = investment.contributionStartAge();
        Integer endAge = investment.contributionEndAge();
        if (startAge == null && endAge == null && investment.initialMonthlyContribution() == 0.0) {
            return;
        }
        if (startAge == null || endAge == null) {
            throw periodError(label + ": le età iniziale e finale dei versamenti devono essere entrambe valorizzate.");
        }
        if (startAge < currentAge || endAge > fireAge || startAge >= endAge) {
            throw periodError(label
                    + ": i versamenti devono iniziare non prima dell'età attuale e terminare entro l'ingresso nel FIRE.");
        }
    }

    private static void validatePeriodicIncome(
            PeriodicIncome income,
            int currentAge,
            int fireAge,
            int horizonEndAge,
            String label
    ) {
        validateName(income.name(), label);
        validateAmount(income.monthlyAmountToday(), label + ": l'importo mensile");
        validateRate(income.annualGrowthRate(), label + ": la crescita annua");

        if (income.startAge() < currentAge || income.startAge() > CalculationLimits.MAX_AGE) {
            throw periodError(label + ": l'età iniziale deve essere compresa tra l'età attuale e "
                    + CalculationLimits.MAX_AGE + " anni.");
        }
        if (income.endAge() != null
                && (income.endAge() <= income.startAge()
                || income.endAge() > CalculationLimits.MAX_AGE)) {
            throw periodError(label + ": l'età finale deve essere successiva all'età iniziale e non superare "
                    + CalculationLimits.MAX_AGE + " anni.");
        }
        if (!income.investBeforeFire() && !income.offsetDuringFire()) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_RESOURCE,
                    label + ": la rendita deve essere usata prima o durante il FIRE."
            );
        }

        boolean overlapsAccumulation = income.investBeforeFire()
                && income.startAge() < fireAge
                && (income.endAge() == null || income.endAge() > currentAge);
        boolean overlapsFire = income.offsetDuringFire()
                && income.startAge() < horizonEndAge
                && (income.endAge() == null || income.endAge() > fireAge);
        if (!overlapsAccumulation && !overlapsFire) {
            throw periodError(label + ": la rendita non è attiva in una fase nella quale è configurata per essere usata.");
        }
    }

    private static void validateFutureLumpSum(
            FutureLumpSum lumpSum,
            int currentAge,
            int horizonEndAge,
            String label
    ) {
        validateName(lumpSum.name(), label);
        validateAmount(lumpSum.amount(), label + ": l'importo");
        validateRate(lumpSum.annualReturnRateAfterReceipt(), label + ": il rendimento dopo la ricezione");
        if (lumpSum.amountBasis() == null) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_RESOURCE,
                    label + ": la base dell'importo è obbligatoria."
            );
        }
        if (lumpSum.receiptAge() < currentAge || lumpSum.receiptAge() > horizonEndAge) {
            throw periodError(label + ": l'età di ricezione deve rientrare nell'orizzonte della simulazione.");
        }
    }

    private static void validateName(String name, String label) {
        if (name != null && name.length() > 100) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_RESOURCE,
                    label + ": il nome non può superare 100 caratteri."
            );
        }
    }

    private static void validateAmount(double value, String label) {
        if (!Double.isFinite(value) || value < 0.0 || value > CalculationLimits.MAX_AMOUNT) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_AMOUNT,
                    label + " deve essere compreso tra 0 e "
                            + CalculationLimits.MAX_AMOUNT_DECIMAL + " euro."
            );
        }
    }

    private static void validateRate(double value, String label) {
        if (!Double.isFinite(value) || value <= -1.0 || value > CalculationLimits.MAX_ANNUAL_RATE) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_RATE,
                    label + " deve essere maggiore di -100% e non superiore a 100%."
            );
        }
    }

    private static FireCalculationException periodError(String message) {
        return new FireCalculationException(CalculationErrorCode.INVALID_RESOURCE_PERIOD, message);
    }
}
