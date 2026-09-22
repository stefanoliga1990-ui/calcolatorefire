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

class FiscalFireCalculatorGoldenTest {

    private static final double MONEY_TOLERANCE = 0.01;
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private final FiscalFireCalculator calculator = new FiscalFireCalculator();

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenScenarios")
    void matchesFiscalFireGoldenScenario(String scenarioId, GoldenScenario scenario) {
        Input values = scenario.input();
        FireCalculationInput baseInput = new FireCalculationInput(
                FireMethod.valueOf(values.method()),
                values.currentAge(),
                values.fireAge(),
                values.fireDurationYears(),
                values.monthlyExpenseToday(),
                0.0,
                0.0,
                values.annualSafeWithdrawalRate(),
                values.terminalCapitalToday(),
                values.currentCapital(),
                0.0,
                0.0
        );
        FiscalFireCalculationResult result = calculator.calculate(new FiscalFireCalculationInput(
                baseInput,
                new FiscalSettings(values.capitalGainsTaxRate(), values.annualStampDutyRate()),
                values.currentTaxBasis()
        ));
        Expected expected = scenario.expected();
        FiscalDecumulationProjectionPoint first = result.targetDecumulation().points().get(1);

        assertMoney(scenarioId, "selectedTarget", expected.selectedTarget(), result.selectedTarget());
        assertMoney(scenarioId, "selectedTargetTaxBasis", expected.selectedTargetTaxBasis(),
                result.selectedTargetTaxBasis());
        assertMoney(scenarioId, "initialMonthlyContribution", expected.initialMonthlyContribution(),
                result.initialMonthlyContribution());
        assertMoney(scenarioId, "accumulationBalance", expected.accumulationBalance(),
                result.accumulation().availableAtFireState().balance());
        assertMoney(scenarioId, "accumulationTaxBasis", expected.accumulationTaxBasis(),
                result.accumulation().availableAtFireState().taxBasis());
        assertMoney(scenarioId, "firstGrossSale", expected.firstGrossSale(), first.grossSale());
        assertMoney(scenarioId, "firstCapitalGainsTax", expected.firstCapitalGainsTax(),
                first.capitalGainsTax());
        assertMoney(scenarioId, "firstNetProceeds", expected.firstNetProceeds(), first.netProceeds());
        assertMoney(scenarioId, "targetFinalBalance", expected.targetFinalBalance(),
                result.targetDecumulation().finalState().balance());
        assertMoney(scenarioId, "personalFinalBalance", expected.personalFinalBalance(),
                result.personalDecumulation().finalState().balance());
        assertMoney(scenarioId, "targetTotalCapitalGainsTax", expected.targetTotalCapitalGainsTax(),
                result.targetDecumulation().totalCapitalGainsTax());
        assertMoney(scenarioId, "targetTotalStampDuty", expected.targetTotalStampDuty(),
                result.targetDecumulation().totalStampDuty());
    }

    private static Stream<Arguments> goldenScenarios() throws IOException {
        try (InputStream input = FiscalFireCalculatorGoldenTest.class
                .getResourceAsStream("/golden-tax-fire-scenarios.json")) {
            Objects.requireNonNull(input, "Missing golden-tax-fire-scenarios.json");
            List<GoldenScenario> scenarios = OBJECT_MAPPER.readValue(input, new TypeReference<>() {
            });
            return scenarios.stream().map(scenario -> Arguments.of(scenario.scenarioId(), scenario));
        }
    }

    private static void assertMoney(String scenarioId, String field, double expected, double actual) {
        assertEquals(expected, actual, MONEY_TOLERANCE, scenarioId + " - " + field);
    }

    private record GoldenScenario(String scenarioId, Input input, Expected expected) {
    }

    private record Input(
            String method,
            int currentAge,
            int fireAge,
            int fireDurationYears,
            double monthlyExpenseToday,
            double terminalCapitalToday,
            double currentCapital,
            Double currentTaxBasis,
            Double annualSafeWithdrawalRate,
            double capitalGainsTaxRate,
            double annualStampDutyRate
    ) {
    }

    private record Expected(
            double selectedTarget,
            double selectedTargetTaxBasis,
            double initialMonthlyContribution,
            double accumulationBalance,
            double accumulationTaxBasis,
            double firstGrossSale,
            double firstCapitalGainsTax,
            double firstNetProceeds,
            double targetFinalBalance,
            double personalFinalBalance,
            double targetTotalCapitalGainsTax,
            double targetTotalStampDuty
    ) {
    }
}
