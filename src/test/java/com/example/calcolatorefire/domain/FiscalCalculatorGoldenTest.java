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

class FiscalCalculatorGoldenTest {

    private static final double MONEY_TOLERANCE = 0.01;
    private static final double RATE_TOLERANCE = 1e-12;
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private final FiscalCalculator calculator = new FiscalCalculator();

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenScenarios")
    void matchesFiscalPrimitiveGoldenScenario(String scenarioId, GoldenScenario scenario) {
        Input input = scenario.input();
        FiscalPortfolioState openingState = calculator.initialState(input.balance(), input.taxBasis());
        FiscalDecumulationMonthResult result = calculator.decumulateMonth(
                openingState,
                input.netNeed(),
                0.0,
                0.0,
                new FiscalSettings(input.capitalGainsTaxRate(), input.annualStampDutyRate())
        );
        Expected expected = scenario.expected();

        assertEquals(expected.taxableGainRatio(), result.taxableGainRatio(), RATE_TOLERANCE,
                label(scenarioId, "taxableGainRatio"));
        assertEquals(expected.grossSale(), result.grossSale(), MONEY_TOLERANCE,
                label(scenarioId, "grossSale"));
        assertEquals(expected.capitalGainsTax(), result.capitalGainsTax(), MONEY_TOLERANCE,
                label(scenarioId, "capitalGainsTax"));
        assertEquals(expected.netProceeds(), result.netProceeds(), MONEY_TOLERANCE,
                label(scenarioId, "netProceeds"));
        assertEquals(expected.remainingTaxBasis(), result.remainingTaxBasis(), MONEY_TOLERANCE,
                label(scenarioId, "remainingTaxBasis"));
        assertEquals(expected.stampDuty(), result.stampDuty(), MONEY_TOLERANCE,
                label(scenarioId, "stampDuty"));
        assertEquals(expected.closingBalance(), result.closingState().balance(), MONEY_TOLERANCE,
                label(scenarioId, "closingBalance"));
    }

    private static Stream<Arguments> goldenScenarios() throws IOException {
        try (InputStream input = FiscalCalculatorGoldenTest.class
                .getResourceAsStream("/golden-tax-scenarios.json")) {
            Objects.requireNonNull(input, "Missing golden-tax-scenarios.json");
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
            double netNeed,
            double capitalGainsTaxRate,
            double annualStampDutyRate
    ) {
    }

    private record Expected(
            double taxableGainRatio,
            double grossSale,
            double capitalGainsTax,
            double netProceeds,
            double remainingTaxBasis,
            double stampDuty,
            double closingBalance
    ) {
    }
}
