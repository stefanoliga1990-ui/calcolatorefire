package com.example.calcolatorefire.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the complete accumulation phase while keeping market value and tax
 * basis separate for every portfolio.
 */
public final class FiscalAccumulationProjector {

    private final FiscalCalculator fiscalCalculator;

    public FiscalAccumulationProjector() {
        this(new FiscalCalculator());
    }

    FiscalAccumulationProjector(FiscalCalculator fiscalCalculator) {
        this.fiscalCalculator = fiscalCalculator;
    }

    public FiscalAccumulationProjection project(
            FiscalAccumulationPlan plan,
            FiscalSettings settings
    ) {
        if (plan == null) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_RESOURCE,
                    "Il piano di accumulo fiscale è obbligatorio."
            );
        }
        if (settings == null) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_CAPITAL_GAINS_TAX_RATE,
                    "Le impostazioni fiscali sono obbligatorie."
            );
        }

        List<FiscalAccumulationProjectionPoint> points = new ArrayList<>(plan.months() + 1);
        FiscalPortfolioState state = plan.initialState();
        FiscalSettings effectiveSettings = plan.stampDutyApplicable()
                ? settings
                : new FiscalSettings(settings.capitalGainsTaxRate(), 0.0);
        points.add(new FiscalAccumulationProjectionPoint(
                0,
                state,
                0.0,
                0.0,
                0.0,
                state.balance(),
                0.0,
                state
        ));

        double totalContributions = 0.0;
        double totalNetInflows = 0.0;
        double totalInvestmentReturns = 0.0;
        double totalStampDuty = 0.0;
        for (int index = 0; index < plan.months(); index++) {
            FiscalAccumulationMonthResult month = fiscalCalculator.accumulateMonth(
                    state,
                    plan.monthlyReturnRate(),
                    plan.monthlyContributions().get(index),
                    plan.monthlyNetInflows().get(index),
                    effectiveSettings
            );
            totalContributions += month.contribution();
            totalNetInflows += month.netInflows();
            totalInvestmentReturns += month.investmentReturn();
            totalStampDuty += month.stampDuty();
            requireFiniteTotal(totalContributions, "versamenti totali");
            requireFiniteTotal(totalNetInflows, "apporti netti totali");
            requireFiniteTotal(totalInvestmentReturns, "rendimenti totali");
            requireFiniteTotal(totalStampDuty, "bollo totale");

            points.add(new FiscalAccumulationProjectionPoint(
                    index + 1,
                    month.openingState(),
                    month.investmentReturn(),
                    month.contribution(),
                    month.netInflows(),
                    month.grossBalance(),
                    month.stampDuty(),
                    month.closingState()
            ));
            state = month.closingState();
        }

        return new FiscalAccumulationProjection(
                plan.name(),
                plan.availableAtFire(),
                plan.initialState(),
                state,
                totalContributions,
                totalNetInflows,
                totalInvestmentReturns,
                totalStampDuty,
                points
        );
    }

    public FiscalAccumulationPortfolioResult projectPortfolio(
            List<FiscalAccumulationPlan> plans,
            FiscalSettings settings
    ) {
        if (plans == null || plans.isEmpty()) {
            throw new FireCalculationException(
                    CalculationErrorCode.INVALID_RESOURCE,
                    "È richiesto almeno un portafoglio per la proiezione fiscale."
            );
        }
        int months = -1;
        List<FiscalAccumulationProjection> projections = new ArrayList<>(plans.size());
        double availableBalance = 0.0;
        double availableTaxBasis = 0.0;
        double totalStampDuty = 0.0;
        double availableStampDuty = 0.0;

        for (FiscalAccumulationPlan plan : plans) {
            if (plan == null) {
                throw new FireCalculationException(
                        CalculationErrorCode.INVALID_RESOURCE,
                        "La lista dei portafogli fiscali non può contenere valori nulli."
                );
            }
            if (months < 0) {
                months = plan.months();
            } else if (months != plan.months()) {
                throw new FireCalculationException(
                        CalculationErrorCode.INVALID_RESOURCE_PERIOD,
                        "Tutti i portafogli fiscali devono terminare nello stesso mese FIRE."
                );
            }

            FiscalAccumulationProjection projection = project(plan, settings);
            projections.add(projection);
            totalStampDuty += projection.totalStampDuty();
            if (projection.availableAtFire()) {
                availableBalance += projection.finalState().balance();
                availableTaxBasis += projection.finalState().taxBasis();
                availableStampDuty += projection.totalStampDuty();
            }
            requireFiniteTotal(totalStampDuty, "bollo aggregato");
            requireFiniteTotal(availableBalance, "patrimonio disponibile al FIRE");
            requireFiniteTotal(availableTaxBasis, "costo fiscale disponibile al FIRE");
            requireFiniteTotal(availableStampDuty, "bollo del patrimonio disponibile al FIRE");
        }

        return new FiscalAccumulationPortfolioResult(
                projections,
                new FiscalPortfolioState(availableBalance, availableTaxBasis),
                totalStampDuty,
                availableStampDuty
        );
    }

    private static void requireFiniteTotal(double amount, String label) {
        if (!Double.isFinite(amount)) {
            throw new FireCalculationException(
                    CalculationErrorCode.FISCAL_SOLUTION_NOT_FOUND,
                    "Il calcolo fiscale non produce un valore finito per " + label + "."
            );
        }
    }
}
