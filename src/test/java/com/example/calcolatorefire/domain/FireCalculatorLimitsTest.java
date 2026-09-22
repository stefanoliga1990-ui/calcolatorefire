package com.example.calcolatorefire.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FireCalculatorLimitsTest {

    private final FireCalculator calculator = new FireCalculator();

    @Test
    void acceptsEveryConfiguredUpperBoundary() {
        FireCalculationInput input = new FireCalculationInput(
                FireMethod.FINITE, 40, 50, 1, CalculationLimits.MAX_AMOUNT,
                CalculationLimits.MAX_ANNUAL_RATE, CalculationLimits.MAX_ANNUAL_RATE,
                null, CalculationLimits.MAX_AMOUNT, CalculationLimits.MAX_AMOUNT,
                CalculationLimits.MAX_ANNUAL_RATE, CalculationLimits.MAX_ANNUAL_RATE,
                List.of());

        assertDoesNotThrow(() -> calculator.calculate(input));
    }

    @ParameterizedTest(name = "rejects amount above maximum: {0}")
    @MethodSource("amountsAboveMaximum")
    void rejectsAmountsAboveMaximum(String field, UnaryOperator<FireCalculationInput> change) {
        FireCalculationException exception = assertThrows(
                FireCalculationException.class,
                () -> calculator.calculate(change.apply(baseInput())));

        assertEquals(CalculationErrorCode.INVALID_AMOUNT, exception.code(), field);
    }

    @ParameterizedTest(name = "rejects rate above maximum: {0}")
    @MethodSource("ratesAboveMaximum")
    void rejectsRatesAboveMaximum(String field, UnaryOperator<FireCalculationInput> change) {
        FireCalculationException exception = assertThrows(
                FireCalculationException.class,
                () -> calculator.calculate(change.apply(baseInput())));

        assertEquals(CalculationErrorCode.INVALID_RATE, exception.code(), field);
    }

    @Test
    void rejectsAgeAboveMaximum() {
        FireCalculationInput input = copy(baseInput(), 131, 131, 1,
                1_000, 0.02, 0.04, 0, 0, 0.05, 0);

        FireCalculationException exception = assertThrows(
                FireCalculationException.class,
                () -> calculator.calculate(input));

        assertEquals(CalculationErrorCode.INVALID_AGE_ORDER, exception.code());
    }

    @Test
    void rejectsAdditionalResourceAmountAboveMaximum() {
        ExistingInvestment resource = new ExistingInvestment(
                "Troppo grande", CalculationLimits.MAX_AMOUNT + 1, 0,
                null, null, 0.05, 0, true);
        FireCalculationInput input = withResources(List.of(resource));

        FireCalculationException exception = assertThrows(
                FireCalculationException.class,
                () -> calculator.calculate(input));

        assertEquals(CalculationErrorCode.INVALID_AMOUNT, exception.code());
    }

    @Test
    void rejectsAdditionalResourceRateAboveMaximum() {
        PeriodicIncome resource = new PeriodicIncome(
                "Crescita eccessiva", 100, CalculationLimits.MAX_ANNUAL_RATE + 0.01,
                40, null, true, true);
        FireCalculationInput input = withResources(List.of(resource));

        FireCalculationException exception = assertThrows(
                FireCalculationException.class,
                () -> calculator.calculate(input));

        assertEquals(CalculationErrorCode.INVALID_RATE, exception.code());
    }

    @Test
    void rejectsAdditionalResourceAgeAboveMaximum() {
        PeriodicIncome resource = new PeriodicIncome(
                "Età eccessiva", 100, 0, 40, 131, true, true);
        FireCalculationInput input = withResources(List.of(resource));

        FireCalculationException exception = assertThrows(
                FireCalculationException.class,
                () -> calculator.calculate(input));

        assertEquals(CalculationErrorCode.INVALID_RESOURCE_PERIOD, exception.code());
    }

    private static Stream<Arguments> amountsAboveMaximum() {
        double invalid = CalculationLimits.MAX_AMOUNT + 1;
        return Stream.of(
                Arguments.of("monthly expense", (UnaryOperator<FireCalculationInput>) input -> copy(
                        input, input.currentAge(), input.fireAge(), input.fireDurationYears(), invalid,
                        input.annualInflationRate(), input.annualFireReturnRate(),
                        input.terminalCapitalToday(), input.currentCapital(),
                        input.annualAccumulationReturnRate(), input.annualContributionGrowthRate())),
                Arguments.of("terminal capital", (UnaryOperator<FireCalculationInput>) input -> copy(
                        input, input.currentAge(), input.fireAge(), input.fireDurationYears(),
                        input.monthlyExpenseToday(), input.annualInflationRate(), input.annualFireReturnRate(),
                        invalid, input.currentCapital(), input.annualAccumulationReturnRate(),
                        input.annualContributionGrowthRate())),
                Arguments.of("current capital", (UnaryOperator<FireCalculationInput>) input -> copy(
                        input, input.currentAge(), input.fireAge(), input.fireDurationYears(),
                        input.monthlyExpenseToday(), input.annualInflationRate(), input.annualFireReturnRate(),
                        input.terminalCapitalToday(), invalid, input.annualAccumulationReturnRate(),
                        input.annualContributionGrowthRate()))
        );
    }

    private static Stream<Arguments> ratesAboveMaximum() {
        double invalid = CalculationLimits.MAX_ANNUAL_RATE + 0.01;
        return Stream.of(
                Arguments.of("inflation", (UnaryOperator<FireCalculationInput>) input -> copy(
                        input, input.currentAge(), input.fireAge(), input.fireDurationYears(),
                        input.monthlyExpenseToday(), invalid, input.annualFireReturnRate(),
                        input.terminalCapitalToday(), input.currentCapital(),
                        input.annualAccumulationReturnRate(), input.annualContributionGrowthRate())),
                Arguments.of("fire return", (UnaryOperator<FireCalculationInput>) input -> copy(
                        input, input.currentAge(), input.fireAge(), input.fireDurationYears(),
                        input.monthlyExpenseToday(), input.annualInflationRate(), invalid,
                        input.terminalCapitalToday(), input.currentCapital(),
                        input.annualAccumulationReturnRate(), input.annualContributionGrowthRate())),
                Arguments.of("accumulation return", (UnaryOperator<FireCalculationInput>) input -> copy(
                        input, input.currentAge(), input.fireAge(), input.fireDurationYears(),
                        input.monthlyExpenseToday(), input.annualInflationRate(), input.annualFireReturnRate(),
                        input.terminalCapitalToday(), input.currentCapital(), invalid,
                        input.annualContributionGrowthRate())),
                Arguments.of("contribution growth", (UnaryOperator<FireCalculationInput>) input -> copy(
                        input, input.currentAge(), input.fireAge(), input.fireDurationYears(),
                        input.monthlyExpenseToday(), input.annualInflationRate(), input.annualFireReturnRate(),
                        input.terminalCapitalToday(), input.currentCapital(),
                        input.annualAccumulationReturnRate(), invalid))
        );
    }

    private static FireCalculationInput baseInput() {
        return new FireCalculationInput(
                FireMethod.FINITE, 40, 50, 30, 1_000,
                0.02, 0.04, null, 0, 0, 0.05, 0, List.of());
    }

    private static FireCalculationInput withResources(List<AdditionalResource> resources) {
        FireCalculationInput base = baseInput();
        return new FireCalculationInput(
                base.method(), base.currentAge(), base.fireAge(), base.fireDurationYears(),
                base.monthlyExpenseToday(), base.annualInflationRate(), base.annualFireReturnRate(),
                base.annualSafeWithdrawalRate(), base.terminalCapitalToday(), base.currentCapital(),
                base.annualAccumulationReturnRate(), base.annualContributionGrowthRate(), resources);
    }

    private static FireCalculationInput copy(
            FireCalculationInput input,
            int currentAge,
            int fireAge,
            int fireDuration,
            double expense,
            double inflation,
            double fireReturn,
            double terminalCapital,
            double currentCapital,
            double accumulationReturn,
            double contributionGrowth
    ) {
        return new FireCalculationInput(
                input.method(), currentAge, fireAge, fireDuration, expense,
                inflation, fireReturn, input.annualSafeWithdrawalRate(),
                terminalCapital, currentCapital, accumulationReturn, contributionGrowth,
                input.additionalResources());
    }
}
