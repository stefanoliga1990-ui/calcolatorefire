package com.example.calcolatorefire.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Describes the monthly cash-flow schedule of one independently tracked
 * portfolio during accumulation.
 */
public record FiscalAccumulationPlan(
        String name,
        FiscalPortfolioState initialState,
        double monthlyReturnRate,
        List<Double> monthlyContributions,
        List<Double> monthlyNetInflows,
        boolean availableAtFire
) {

    private static final int MAX_ACCUMULATION_MONTHS = CalculationLimits.MAX_AGE * 12;

    public FiscalAccumulationPlan {
        if (name == null || name.isBlank()) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_RESOURCE,
                    "Il nome del portafoglio fiscale è obbligatorio."
            );
        }
        if (initialState == null) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_TAX_BASIS,
                    "Lo stato fiscale iniziale del portafoglio è obbligatorio."
            );
        }
        validateMonthlyRate(monthlyReturnRate, "rendimento mensile");
        monthlyContributions = immutableAmounts(monthlyContributions, "versamenti mensili");
        monthlyNetInflows = immutableAmounts(monthlyNetInflows, "apporti netti mensili");
        if (monthlyContributions.size() != monthlyNetInflows.size()) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_RESOURCE_PERIOD,
                    "Versamenti e apporti netti devono coprire lo stesso numero di mesi."
            );
        }
        if (monthlyContributions.size() > MAX_ACCUMULATION_MONTHS) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_RESOURCE_PERIOD,
                    "Il piano di accumulo fiscale supera l'orizzonte massimo supportato."
            );
        }
    }

    public int months() {
        return monthlyContributions.size();
    }

    public static FiscalAccumulationPlan growingPac(
            String name,
            FiscalPortfolioState initialState,
            int months,
            double monthlyReturnRate,
            double initialMonthlyContribution,
            double monthlyContributionGrowthRate,
            List<Double> monthlyNetInflows,
            boolean availableAtFire
    ) {
        validateMonths(months);
        validateAmount(initialMonthlyContribution, "versamento mensile iniziale");
        validateMonthlyRate(monthlyContributionGrowthRate, "crescita mensile del versamento");
        List<Double> inflows = monthlyNetInflows == null
                ? Collections.nCopies(months, 0.0)
                : monthlyNetInflows;
        if (inflows.size() != months) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_RESOURCE_PERIOD,
                    "Gli apporti netti devono coprire tutti i mesi di accumulo."
            );
        }

        List<Double> contributions = new ArrayList<>(months);
        for (int month = 0; month < months; month++) {
            double contribution = initialMonthlyContribution
                    * Math.pow(1.0 + monthlyContributionGrowthRate, month);
            validateAmount(contribution, "versamento mensile calcolato");
            contributions.add(contribution);
        }
        return new FiscalAccumulationPlan(
                name,
                initialState,
                monthlyReturnRate,
                contributions,
                inflows,
                availableAtFire
        );
    }

    public static FiscalAccumulationPlan existingInvestment(
            String name,
            FiscalPortfolioState initialState,
            int months,
            double monthlyReturnRate,
            double initialMonthlyContribution,
            double monthlyContributionGrowthRate,
            int contributionStartMonth,
            int contributionEndMonth,
            boolean availableAtFire
    ) {
        validateMonths(months);
        validateAmount(initialMonthlyContribution, "versamento mensile iniziale");
        validateMonthlyRate(monthlyContributionGrowthRate, "crescita mensile del versamento");
        if (contributionStartMonth < 0
                || contributionEndMonth < contributionStartMonth
                || contributionEndMonth > months) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_RESOURCE_PERIOD,
                    "Il periodo del PAC esistente non è valido."
            );
        }

        List<Double> contributions = new ArrayList<>(months);
        for (int month = 0; month < months; month++) {
            double contribution = month >= contributionStartMonth && month < contributionEndMonth
                    ? initialMonthlyContribution
                            * Math.pow(1.0 + monthlyContributionGrowthRate,
                                    month - contributionStartMonth)
                    : 0.0;
            validateAmount(contribution, "versamento mensile calcolato");
            contributions.add(contribution);
        }
        return new FiscalAccumulationPlan(
                name,
                initialState,
                monthlyReturnRate,
                contributions,
                Collections.nCopies(months, 0.0),
                availableAtFire
        );
    }

    private static List<Double> immutableAmounts(List<Double> amounts, String label) {
        if (amounts == null) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_RESOURCE,
                    "La sequenza di " + label + " è obbligatoria."
            );
        }
        for (Double amount : amounts) {
            if (amount == null) {
                throw new FireCalculationException(
                        CalculationErrorCode.INVALID_AMOUNT,
                        "La sequenza di " + label + " non può contenere valori nulli."
                );
            }
            validateAmount(amount, label);
        }
        return List.copyOf(amounts);
    }

    private static void validateMonths(int months) {
        if (months < 0 || months > MAX_ACCUMULATION_MONTHS) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_RESOURCE_PERIOD,
                    "I mesi di accumulo devono essere compresi tra 0 e "
                            + MAX_ACCUMULATION_MONTHS + "."
            );
        }
    }

    private static void validateMonthlyRate(double rate, String label) {
        if (!Double.isFinite(rate) || rate <= -1.0) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_RATE,
                    "Il " + label + " deve essere finito e maggiore di -100%."
            );
        }
    }

    private static void validateAmount(double amount, String label) {
        if (!Double.isFinite(amount) || amount < 0.0 || amount > CalculationLimits.MAX_AMOUNT) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_AMOUNT,
                    "Il valore di " + label + " deve essere finito, non negativo e non superiore a "
                            + CalculationLimits.MAX_AMOUNT_DECIMAL + " euro."
            );
        }
    }
}
