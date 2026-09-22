package com.example.calcolatorefire.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class FiscalAccumulationProjectorTest {

    private static final double TOLERANCE = 1e-9;

    private final FiscalCalculator calculator = new FiscalCalculator();
    private final FiscalAccumulationProjector projector = new FiscalAccumulationProjector();

    @Test
    void growingPacAddsEveryContributionToBalanceAndTaxBasis() {
        FiscalAccumulationPlan plan = FiscalAccumulationPlan.growingPac(
                "Patrimonio principale",
                calculator.initialState(0.0, null),
                12,
                0.0,
                100.0,
                0.01,
                null,
                true
        );

        FiscalAccumulationProjection result = projector.project(plan, FiscalSettings.disabled());

        double expected = 100.0 * (Math.pow(1.01, 12) - 1.0) / 0.01;
        assertEquals(expected, result.totalContributions(), TOLERANCE);
        assertEquals(expected, result.finalState().balance(), TOLERANCE);
        assertEquals(expected, result.finalState().taxBasis(), TOLERANCE);
        assertEquals(100.0, result.points().get(1).contribution(), TOLERANCE);
        assertEquals(100.0 * Math.pow(1.01, 11), result.points().get(12).contribution(), TOLERANCE);
    }

    @Test
    void netIncomeAndCapitalInflowsIncreaseTaxBasisAtEndOfMonth() {
        FiscalAccumulationPlan plan = FiscalAccumulationPlan.growingPac(
                "Patrimonio principale",
                calculator.initialState(10_000.0, 7_000.0),
                3,
                0.01,
                0.0,
                0.0,
                List.of(500.0, 0.0, 2_000.0),
                true
        );

        FiscalAccumulationProjection result = projector.project(plan, FiscalSettings.disabled());

        assertEquals(9_500.0, result.finalState().taxBasis(), TOLERANCE);
        assertEquals(2_500.0, result.totalNetInflows(), TOLERANCE);
        assertEquals(100.0, result.points().get(1).investmentReturn(), TOLERANCE);
        assertEquals(500.0, result.points().get(1).netInflows(), TOLERANCE);
    }

    @Test
    void existingInvestmentUsesItsOwnContributionWindowAndTaxBasis() {
        FiscalAccumulationPlan plan = FiscalAccumulationPlan.existingInvestment(
                "ETF esistente",
                calculator.initialState(20_000.0, 15_000.0),
                8,
                0.0,
                200.0,
                0.01,
                2,
                6,
                true
        );

        FiscalAccumulationProjection result = projector.project(plan, FiscalSettings.disabled());

        double expectedContributions = 200.0 + 202.0 + 204.02 + 206.0602;
        assertEquals(0.0, result.points().get(2).contribution(), TOLERANCE);
        assertEquals(200.0, result.points().get(3).contribution(), TOLERANCE);
        assertEquals(206.0602, result.points().get(6).contribution(), TOLERANCE);
        assertEquals(0.0, result.points().get(7).contribution(), TOLERANCE);
        assertEquals(expectedContributions, result.totalContributions(), TOLERANCE);
        assertEquals(15_000.0 + expectedContributions, result.finalState().taxBasis(), TOLERANCE);
    }

    @Test
    void portfolioAggregationExcludesInvestmentsUnavailableAtFire() {
        FiscalAccumulationPlan main = FiscalAccumulationPlan.growingPac(
                "Principale",
                calculator.initialState(10_000.0, 8_000.0),
                1,
                0.0,
                1_000.0,
                0.0,
                null,
                true
        );
        FiscalAccumulationPlan available = FiscalAccumulationPlan.existingInvestment(
                "Disponibile",
                calculator.initialState(5_000.0, null),
                1,
                0.0,
                0.0,
                0.0,
                0,
                0,
                true
        );
        FiscalAccumulationPlan unavailable = FiscalAccumulationPlan.existingInvestment(
                "Non disponibile",
                calculator.initialState(50_000.0, 40_000.0),
                1,
                0.0,
                0.0,
                0.0,
                0,
                0,
                false
        );

        FiscalAccumulationPortfolioResult result = projector.projectPortfolio(
                List.of(main, available, unavailable),
                FiscalSettings.disabled()
        );

        assertEquals(16_000.0, result.availableAtFireState().balance(), TOLERANCE);
        assertEquals(14_000.0, result.availableAtFireState().taxBasis(), TOLERANCE);
        assertEquals(3, result.projections().size());
    }

    @Test
    void zeroMonthProjectionPreservesInitialState() {
        FiscalPortfolioState opening = calculator.initialState(80_000.0, 100_000.0);
        FiscalAccumulationPlan plan = FiscalAccumulationPlan.growingPac(
                "Principale", opening, 0, 0.01, 500.0, 0.01, null, true
        );

        FiscalAccumulationProjection result = projector.project(plan, FiscalSettings.defaults());

        assertEquals(opening, result.finalState());
        assertEquals(1, result.points().size());
        assertEquals(0.0, result.totalStampDuty(), TOLERANCE);
    }

    @Test
    void planCopiesSchedulesDefensively() {
        List<Double> contributions = new ArrayList<>(List.of(100.0));
        FiscalAccumulationPlan plan = new FiscalAccumulationPlan(
                "Principale",
                calculator.initialState(0.0, null),
                0.0,
                contributions,
                List.of(0.0),
                true
        );

        contributions.set(0, 999.0);

        assertEquals(100.0, plan.monthlyContributions().get(0), TOLERANCE);
        assertThrows(UnsupportedOperationException.class,
                () -> plan.monthlyContributions().set(0, 999.0));
    }

    @Test
    void rejectsSchedulesWithDifferentLengths() {
        FireCalculationException exception = assertThrows(
                FireCalculationException.class,
                () -> new FiscalAccumulationPlan(
                        "Principale",
                        calculator.initialState(0.0, null),
                        0.0,
                        List.of(100.0),
                        Collections.emptyList(),
                        true
                )
        );

        assertEquals(CalculationErrorCode.INVALID_RESOURCE_PERIOD, exception.code());
    }

    @Test
    void rejectsPortfolioPlansWithDifferentFireMonths() {
        FiscalAccumulationPlan oneMonth = FiscalAccumulationPlan.growingPac(
                "Uno", calculator.initialState(0.0, null), 1, 0.0, 0.0, 0.0, null, true
        );
        FiscalAccumulationPlan twoMonths = FiscalAccumulationPlan.growingPac(
                "Due", calculator.initialState(0.0, null), 2, 0.0, 0.0, 0.0, null, true
        );

        FireCalculationException exception = assertThrows(
                FireCalculationException.class,
                () -> projector.projectPortfolio(List.of(oneMonth, twoMonths), FiscalSettings.defaults())
        );

        assertEquals(CalculationErrorCode.INVALID_RESOURCE_PERIOD, exception.code());
    }
}
