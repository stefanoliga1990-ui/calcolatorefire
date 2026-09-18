package com.example.calcolatorefire.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FireCalculatorGoldenTest {

    private static final double MONEY_TOLERANCE = 0.01;
    private final FireCalculator calculator = new FireCalculator();

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenScenarios")
    void matchesGoldenScenario(String scenarioId, FireCalculationInput input, Expected expected) {
        FireCalculationResult result = calculator.calculate(input);

        assertEquals(expected.accumulationMonths(), result.accumulationMonths(), scenarioId);
        assertEquals(expected.fireMonths(), result.fireMonths(), scenarioId);
        assertMoney(expected.firstWithdrawal(), result.firstMonthlyWithdrawal(), scenarioId);
        assertOptionalMoney(expected.finiteTarget(), result.finiteTarget(), scenarioId);
        assertOptionalMoney(expected.swrTarget(), result.safeWithdrawalRateTarget(), scenarioId);
        assertMoney(expected.selectedTarget(), result.selectedTarget(), scenarioId);
        assertMoney(expected.recommendedTarget(), result.recommendedTarget(), scenarioId);
        assertMoney(expected.initialContribution(), result.initialMonthlyContribution(), scenarioId);
        assertMoney(expected.accumulationFinal(), result.projectedAccumulationFinalBalance(), scenarioId);
        assertMoney(expected.terminalNominal(), result.terminalCapitalNominalAtEnd(), scenarioId);
        assertOptionalMoney(expected.finiteBaseFinal(), result.finiteTargetProjectedFinalBalance(), scenarioId);
        assertMoney(expected.targetDecumulationFinal(), result.targetDecumulationFinalBalance(), scenarioId);
        assertMoney(expected.personalDecumulationStart(), result.personalDecumulationStartBalance(), scenarioId);
        assertMoney(expected.personalDecumulationFinal(), result.personalDecumulationFinalBalance(), scenarioId);
        assertEquals(result.accumulationMonths() + 1, result.accumulationProjection().size(), scenarioId);
        assertEquals(result.fireMonths() + 1, result.decumulationProjection().size(), scenarioId);
        assertEquals(0.0, result.totalShortfall(), MONEY_TOLERANCE, scenarioId);
        assertNull(result.depletionMonth(), scenarioId);
    }

    @Test
    void appliesWithdrawalBeforeMonthlyReturn() {
        FireCalculationResult result = calculator.calculate(baseInput());
        DecumulationPoint firstMonth = result.decumulationProjection().get(1);

        assertMoney(result.firstMonthlyWithdrawal(), firstMonth.actualWithdrawal(), "first withdrawal");
        assertMoney(
                (firstMonth.openingBalance() - firstMonth.actualWithdrawal())
                        * result.monthlyFireReturnRate(),
                firstMonth.investmentReturn(),
                "return after withdrawal"
        );
    }

    @Test
    void rejectsInsufficientCapitalWhenNoAccumulationMonthsExist() {
        FireCalculationInput input = new FireCalculationInput(
                FireMethod.FINITE, 50, 50, 35, 1_600, 0.02, 0.05,
                0.04, 0.10, 0, 0, 0.07, 0
        );

        FireCalculationException exception = assertThrows(
                FireCalculationException.class,
                () -> calculator.calculate(input)
        );
        assertEquals(CalculationErrorCode.UNREACHABLE_WITH_ZERO_MONTHS, exception.code());
    }

    @Test
    void acceptsZeroAccumulationMonthsWhenCapitalIsAlreadySufficient() {
        FireCalculationInput input = new FireCalculationInput(
                FireMethod.FINITE, 50, 50, 35, 1_000, 0.02, 0.04,
                0.04, 0.10, 0, 1_000_000, 0.05, 0
        );

        FireCalculationResult result = calculator.calculate(input);

        assertEquals(0, result.accumulationMonths());
        assertEquals(0.0, result.initialMonthlyContribution(), MONEY_TOLERANCE);
        assertEquals(1, result.accumulationProjection().size());
        assertMoney(1_000_000, result.projectedAccumulationFinalBalance(), "zero-month balance");
    }

    @Test
    void reportsValidationCodeForInvalidAgeOrder() {
        FireCalculationInput invalid = new FireCalculationInput(
                FireMethod.FINITE, 51, 50, 35, 1_000, 0.02, 0.04,
                0.04, 0.10, 0, 10_000, 0.05, 0
        );

        FireCalculationException exception = assertThrows(
                FireCalculationException.class,
                () -> calculator.calculate(invalid)
        );
        assertEquals(CalculationErrorCode.INVALID_AGE_ORDER, exception.code());
    }

    @Test
    void finiteMethodDoesNotRequireOrCalculateSwr() {
        FireCalculationInput input = new FireCalculationInput(
                FireMethod.FINITE, 36, 50, 35, 1_600, 0.02, 0.05,
                null, 0.10, 0, 10_000, 0.07, 0
        );
        FireCalculationResult result = calculator.calculate(input);
        assertNull(result.safeWithdrawalRateTarget());
        assertMoney(557_770.7040, result.finiteTarget(), "finite target without SWR");
    }

    @Test
    void swrMethodRequiresSwr() {
        FireCalculationInput input = new FireCalculationInput(
                FireMethod.SWR, 36, 50, 35, 1_600, 0.02, 0.05,
                null, 0.10, 0, 10_000, 0.07, 0
        );
        FireCalculationException exception = assertThrows(
                FireCalculationException.class, () -> calculator.calculate(input));
        assertEquals(CalculationErrorCode.INVALID_SWR, exception.code());
    }

    private static FireCalculationInput baseInput() {
        return new FireCalculationInput(
                FireMethod.FINITE, 36, 50, 35, 1_600, 0.02, 0.05,
                0.04, 0.10, 0, 10_000, 0.07, 0
        );
    }

    private static void assertMoney(double expected, double actual, String message) {
        assertEquals(expected, actual, MONEY_TOLERANCE, message);
    }

    private static void assertOptionalMoney(Double expected, Double actual, String message) {
        if (expected == null) {
            assertNull(actual, message);
        } else {
            assertMoney(expected, actual, message);
        }
    }

    private static Stream<Arguments> goldenScenarios() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                FireCalculatorGoldenTest.class.getResourceAsStream("/golden-scenarios.csv"),
                StandardCharsets.UTF_8
        ))) {
            List<String> lines = reader.lines().filter(line -> !line.isBlank()).toList();
            String[] headers = lines.get(0).split(",", -1);
            Map<String, Integer> columns = new HashMap<>();
            for (int index = 0; index < headers.length; index++) {
                columns.put(headers[index], index);
            }

            return lines.subList(1, lines.size()).stream()
                    .map(line -> parseScenario(line, columns));
        }
    }

    private static Arguments parseScenario(String line, Map<String, Integer> columns) {
        String[] values = line.split(",", -1);
        FireCalculationInput input = new FireCalculationInput(
                FireMethod.valueOf(text(values, columns, "method")),
                integer(values, columns, "current_age"),
                integer(values, columns, "fire_age"),
                integer(values, columns, "fire_years"),
                decimal(values, columns, "expense_monthly_today"),
                decimal(values, columns, "inflation_annual"),
                decimal(values, columns, "fire_return_annual"),
                nullableDecimal(values, columns, "swr_annual"),
                decimal(values, columns, "safety_margin"),
                decimal(values, columns, "terminal_capital_today"),
                decimal(values, columns, "current_capital"),
                decimal(values, columns, "accumulation_return_annual"),
                decimal(values, columns, "contribution_growth_annual")
        );
        Expected expected = new Expected(
                integer(values, columns, "expected_accumulation_months"),
                integer(values, columns, "expected_fire_months"),
                decimal(values, columns, "expected_first_withdrawal"),
                nullableDecimal(values, columns, "expected_finite_target"),
                nullableDecimal(values, columns, "expected_swr_target"),
                decimal(values, columns, "expected_selected_target"),
                decimal(values, columns, "expected_recommended_target"),
                decimal(values, columns, "expected_initial_monthly_contribution"),
                decimal(values, columns, "expected_accumulation_final"),
                decimal(values, columns, "expected_terminal_nominal"),
                nullableDecimal(values, columns, "expected_finite_base_final"),
                decimal(values, columns, "expected_target_decumulation_final"),
                decimal(values, columns, "expected_personal_decumulation_start"),
                decimal(values, columns, "expected_personal_decumulation_final")
        );
        return Arguments.of(text(values, columns, "scenario_id"), input, expected);
    }

    private static String text(String[] values, Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        if (index == null || index >= values.length) {
            throw new IllegalArgumentException("Missing CSV column: " + name + "; values=" + Arrays.toString(values));
        }
        return values[index];
    }

    private static int integer(String[] values, Map<String, Integer> columns, String name) {
        return Integer.parseInt(text(values, columns, name));
    }

    private static double decimal(String[] values, Map<String, Integer> columns, String name) {
        return Double.parseDouble(text(values, columns, name));
    }

    private static Double nullableDecimal(String[] values, Map<String, Integer> columns, String name) {
        String value = text(values, columns, name);
        return value.isBlank() ? null : Double.parseDouble(value);
    }

    private record Expected(
            int accumulationMonths,
            int fireMonths,
            double firstWithdrawal,
            Double finiteTarget,
            Double swrTarget,
            double selectedTarget,
            double recommendedTarget,
            double initialContribution,
            double accumulationFinal,
            double terminalNominal,
            Double finiteBaseFinal,
            double targetDecumulationFinal,
            double personalDecumulationStart,
            double personalDecumulationFinal
    ) {
    }
}
