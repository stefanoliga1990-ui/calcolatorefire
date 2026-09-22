package com.example.calcolatorefire.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class FiscalAccumulationProjectorGoldenTest {

    private static final double MONEY_TOLERANCE = 0.01;
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private final FiscalCalculator calculator = new FiscalCalculator();
    private final FiscalAccumulationProjector projector = new FiscalAccumulationProjector();

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenScenarios")
    void matchesFiscalAccumulationGoldenScenario(String scenarioId, GoldenScenario scenario) {
        Input input = scenario.input();
        FiscalAccumulationPlan plan = new FiscalAccumulationPlan(
                scenarioId,
                calculator.initialState(input.balance(), input.taxBasis()),
                input.monthlyReturnRate(),
                input.monthlyContributions(),
                input.monthlyNetInflows(),
                true
        );

        FiscalAccumulationProjection result = projector.project(
                plan,
                new FiscalSettings(0.26, input.annualStampDutyRate())
        );
        Expected expected = scenario.expected();

        assertEquals(expected.firstMonthClosingBalance(), result.points().get(1).closingState().balance(),
                MONEY_TOLERANCE, label(scenarioId, "firstMonthClosingBalance"));
        assertEquals(expected.finalBalance(), result.finalState().balance(), MONEY_TOLERANCE,
                label(scenarioId, "finalBalance"));
        assertEquals(expected.finalTaxBasis(), result.finalState().taxBasis(), MONEY_TOLERANCE,
                label(scenarioId, "finalTaxBasis"));
        assertEquals(expected.latentGain(), result.finalState().latentGain(), MONEY_TOLERANCE,
                label(scenarioId, "latentGain"));
        assertEquals(expected.totalContributions(), result.totalContributions(), MONEY_TOLERANCE,
                label(scenarioId, "totalContributions"));
        assertEquals(expected.totalNetInflows(), result.totalNetInflows(), MONEY_TOLERANCE,
                label(scenarioId, "totalNetInflows"));
        assertEquals(expected.totalInvestmentReturns(), result.totalInvestmentReturns(), MONEY_TOLERANCE,
                label(scenarioId, "totalInvestmentReturns"));
        assertEquals(expected.totalStampDuty(), result.totalStampDuty(), MONEY_TOLERANCE,
                label(scenarioId, "totalStampDuty"));
        assertEquals(input.monthlyContributions().size() + 1, result.points().size(),
                label(scenarioId, "points"));
    }

    private static Stream<Arguments> goldenScenarios() throws IOException {
        try (InputStream input = FiscalAccumulationProjectorGoldenTest.class
                .getResourceAsStream("/golden-tax-accumulation-scenarios.json")) {
            Objects.requireNonNull(input, "Missing golden-tax-accumulation-scenarios.json");
            List<GoldenScenario> scenarios = OBJECT_MAPPER.readValue(input, new TypeReference<>() {
            });
            return scenarios.stream().map(scenario -> Arguments.of(scenario.scenarioId(), scenario));
        }
    }

    private static String label(String scenarioId, String field) {
        return scenarioId + " - " + field;
    }

    private record GoldenScenario(String scenarioId, Input input, Expected expected) {
    }

    private record Input(
            double balance,
            double taxBasis,
            double monthlyReturnRate,
            double annualStampDutyRate,
            List<Double> monthlyContributions,
            List<Double> monthlyNetInflows
    ) {
    }

    private record Expected(
            double firstMonthClosingBalance,
            double finalBalance,
            double finalTaxBasis,
            double latentGain,
            double totalContributions,
            double totalNetInflows,
            double totalInvestmentReturns,
            double totalStampDuty
    ) {
    }
}
