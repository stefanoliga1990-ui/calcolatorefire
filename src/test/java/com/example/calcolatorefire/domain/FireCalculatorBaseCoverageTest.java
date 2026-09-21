package com.example.calcolatorefire.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class FireCalculatorBaseCoverageTest {

    private static final double MONEY_TOLERANCE = 0.01;
    private static final double RATE_TOLERANCE = 1e-12;

    private final FireCalculator calculator = new FireCalculator();

    @ParameterizedTest(name = "annual rate {0}")
    @ValueSource(doubles = {-0.999999, -0.25, 0.0, 0.05})
    void convertsEveryAnnualRateToItsMonthlyEquivalent(double annualRate) {
        FireCalculationInput input = new FireCalculationInput(
                FireMethod.FINITE, 40, 41, 1, 0, annualRate, annualRate,
                null, 0, 1_000, annualRate, annualRate
        );

        FireCalculationResult result = calculator.calculate(input);
        double expectedMonthlyRate = Math.pow(1.0 + annualRate, 1.0 / 12.0) - 1.0;

        assertEquals(expectedMonthlyRate, result.monthlyInflationRate(), RATE_TOLERANCE);
        assertEquals(expectedMonthlyRate, result.monthlyFireReturnRate(), RATE_TOLERANCE);
        assertEquals(expectedMonthlyRate, result.monthlyAccumulationReturnRate(), RATE_TOLERANCE);
        assertEquals(expectedMonthlyRate, result.monthlyContributionGrowthRate(), RATE_TOLERANCE);
        assertEquals(0.0, result.monthlyRealFireReturnRate(), RATE_TOLERANCE);
        assertTrue(Double.isFinite(result.projectedAccumulationFinalBalance()));
        assertTrue(result.projectedAccumulationFinalBalance() >= 0.0);
    }

    @ParameterizedTest(name = "{0}: {1} -> {2}")
    @MethodSource("horizonScenarios")
    void respectsIntegerAgeBoundariesAndProjectionLengths(
            FireMethod method,
            int currentAge,
            int fireAge
    ) {
        FireCalculationInput input = new FireCalculationInput(
                method, currentAge, fireAge, 1, 0, 0, 0,
                method == FireMethod.SWR ? 0.04 : null, 0, 0, 0, 0
        );

        FireCalculationResult result = calculator.calculate(input);
        int expectedAccumulationMonths = 12 * (fireAge - currentAge);

        assertEquals(expectedAccumulationMonths, result.accumulationMonths());
        assertEquals(expectedAccumulationMonths + 1, result.accumulationProjection().size());
        assertEquals(13, result.decumulationProjection().size());
        assertEquals(currentAge, result.accumulationProjection().get(0).age(), RATE_TOLERANCE);
        assertEquals(fireAge, result.accumulationProjection().get(expectedAccumulationMonths).age(), RATE_TOLERANCE);
        assertEquals(fireAge + 1.0, result.decumulationProjection().get(12).age(), RATE_TOLERANCE);
    }

    @Test
    void growingAndDecliningPacBothReachTheSameTarget() {
        FireCalculationResult declining = calculator.calculate(simplePacInput(-0.12, 0.0));
        FireCalculationResult constant = calculator.calculate(simplePacInput(0.0, 0.0));
        FireCalculationResult growing = calculator.calculate(simplePacInput(0.12, 0.0));

        assertMoney(1_200, declining.selectedTarget());
        assertMoney(1_200, constant.selectedTarget());
        assertMoney(1_200, growing.selectedTarget());
        assertMoney(1_200, declining.projectedAccumulationFinalBalance());
        assertMoney(1_200, constant.projectedAccumulationFinalBalance());
        assertMoney(1_200, growing.projectedAccumulationFinalBalance());

        assertTrue(declining.initialMonthlyContribution() > constant.initialMonthlyContribution());
        assertTrue(constant.initialMonthlyContribution() > growing.initialMonthlyContribution());
        assertMoney(100, constant.initialMonthlyContribution());
        assertTrue(lastContribution(declining) < declining.initialMonthlyContribution());
        assertTrue(lastContribution(growing) > growing.initialMonthlyContribution());
    }

    @ParameterizedTest(name = "equal annual return and growth {0}")
    @ValueSource(doubles = {-0.12, 0.0, 0.05})
    void equalAccumulationReturnAndPacGrowthUseTheLimitCase(double annualRate) {
        FireCalculationResult result = calculator.calculate(simplePacInput(annualRate, annualRate));
        double monthlyRate = Math.pow(1.0 + annualRate, 1.0 / 12.0) - 1.0;
        double expectedFactor = 12.0 * Math.pow(1.0 + monthlyRate, 11);

        assertMoney(1_200 / expectedFactor, result.initialMonthlyContribution());
        assertMoney(1_200, result.projectedAccumulationFinalBalance());
    }

    @Test
    void moreSpendingRaisesBothFiniteAndSwrTargets() {
        FireCalculationResult finiteLow = calculator.calculate(directionalInput(FireMethod.FINITE, 1_000, 0.04, 30, 0.04, 0));
        FireCalculationResult finiteHigh = calculator.calculate(directionalInput(FireMethod.FINITE, 1_500, 0.04, 30, 0.04, 0));
        FireCalculationResult swrLow = calculator.calculate(directionalInput(FireMethod.SWR, 1_000, 0.04, 30, 0.04, 0));
        FireCalculationResult swrHigh = calculator.calculate(directionalInput(FireMethod.SWR, 1_500, 0.04, 30, 0.04, 0));

        assertTrue(finiteHigh.selectedTarget() > finiteLow.selectedTarget());
        assertTrue(swrHigh.selectedTarget() > swrLow.selectedTarget());
        assertEquals(1.5, finiteHigh.selectedTarget() / finiteLow.selectedTarget(), RATE_TOLERANCE);
        assertEquals(1.5, swrHigh.selectedTarget() / swrLow.selectedTarget(), RATE_TOLERANCE);
    }

    @Test
    void aHigherFireReturnReducesOnlyTheFiniteTargetFormula() {
        FireCalculationResult finiteZero = calculator.calculate(directionalInput(FireMethod.FINITE, 1_000, 0.04, 30, 0.0, 0));
        FireCalculationResult finitePositive = calculator.calculate(directionalInput(FireMethod.FINITE, 1_000, 0.04, 30, 0.06, 0));
        FireCalculationResult swrZero = calculator.calculate(directionalInput(FireMethod.SWR, 1_000, 0.04, 30, 0.0, 0));
        FireCalculationResult swrPositive = calculator.calculate(directionalInput(FireMethod.SWR, 1_000, 0.04, 30, 0.06, 0));

        assertTrue(finitePositive.selectedTarget() < finiteZero.selectedTarget());
        assertMoney(swrZero.selectedTarget(), swrPositive.selectedTarget());
    }

    @Test
    void moreCurrentCapitalReducesAndCanEliminateThePac() {
        FireCalculationResult noCapital = calculator.calculate(simplePacInput(0, 0, 0));
        FireCalculationResult halfTarget = calculator.calculate(simplePacInput(0, 0, 600));
        FireCalculationResult funded = calculator.calculate(simplePacInput(0, 0, 1_200));

        assertMoney(100, noCapital.initialMonthlyContribution());
        assertMoney(50, halfTarget.initialMonthlyContribution());
        assertMoney(0, funded.initialMonthlyContribution());
        assertTrue(funded.projectedAccumulationFinalBalance() >= funded.selectedTarget());
    }

    @Test
    void longerFireRaisesFiniteTargetButDoesNotChangeSwrTarget() {
        FireCalculationResult finiteTwenty = calculator.calculate(directionalInput(FireMethod.FINITE, 1_000, 0.04, 20, 0.04, 0));
        FireCalculationResult finiteForty = calculator.calculate(directionalInput(FireMethod.FINITE, 1_000, 0.04, 40, 0.04, 0));
        FireCalculationResult swrTwenty = calculator.calculate(directionalInput(FireMethod.SWR, 1_000, 0.04, 20, 0.04, 0));
        FireCalculationResult swrForty = calculator.calculate(directionalInput(FireMethod.SWR, 1_000, 0.04, 40, 0.04, 0));

        assertTrue(finiteForty.selectedTarget() > finiteTwenty.selectedTarget());
        assertMoney(swrTwenty.selectedTarget(), swrForty.selectedTarget());
        assertEquals(240, finiteTwenty.fireMonths());
        assertEquals(480, finiteForty.fireMonths());
    }

    @ParameterizedTest(name = "SWR {0}")
    @ValueSource(doubles = {0.02, 0.04, 0.10})
    void swrWithoutResourcesIsAlwaysAnnualizedFirstWithdrawalDividedByRate(double swr) {
        FireCalculationResult result = calculator.calculate(
                directionalInput(FireMethod.SWR, 1_000, swr, 35, 0.05, 0));

        assertMoney(result.firstMonthlyWithdrawal() * 12.0 / swr, result.safeWithdrawalRateBaseTarget());
        assertMoney(result.safeWithdrawalRateBaseTarget(), result.safeWithdrawalRateTarget());
        assertMoney(result.safeWithdrawalRateTarget(), result.selectedTarget());
        assertNull(result.finiteTarget());
    }

    @Test
    void everyMonthReconcilesAccumulationAndFiniteDecumulation() {
        FireCalculationInput input = new FireCalculationInput(
                FireMethod.FINITE, 35, 50, 40, 1_800, 0.02, 0.04,
                null, 100_000, 50_000, 0.06, 0.02
        );

        FireCalculationResult result = calculator.calculate(input);
        double cumulativeContributions = 0.0;
        for (int month = 1; month <= result.accumulationMonths(); month++) {
            AccumulationPoint previous = result.accumulationProjection().get(month - 1);
            AccumulationPoint point = result.accumulationProjection().get(month);
            cumulativeContributions += point.contribution();

            assertEquals(previous.closingBalance(), point.openingBalance(), MONEY_TOLERANCE, "acc opening month " + month);
            assertMoney(point.openingBalance() * result.monthlyAccumulationReturnRate(), point.investmentReturn());
            assertMoney(point.openingBalance() + point.investmentReturn() + point.contribution(), point.closingBalance());
            assertMoney(cumulativeContributions, point.cumulativeContributions());
            assertMoney(point.closingBalance(), point.totalAvailableBalance());
        }

        for (int month = 1; month <= result.fireMonths(); month++) {
            DecumulationPoint previous = result.decumulationProjection().get(month - 1);
            DecumulationPoint point = result.decumulationProjection().get(month);

            assertEquals(previous.closingBalance(), point.openingBalance(), MONEY_TOLERANCE, "fire opening month " + month);
            assertMoney(point.grossExpense(), point.scheduledWithdrawal());
            assertMoney(point.scheduledWithdrawal(), point.actualWithdrawal());
            assertMoney(0, point.shortfall());
            assertMoney(
                    (point.openingBalance() - point.actualWithdrawal()) * result.monthlyFireReturnRate(),
                    point.investmentReturn()
            );
            assertMoney(
                    point.openingBalance() - point.actualWithdrawal() + point.investmentReturn(),
                    point.closingBalance()
            );
        }

        assertMoney(result.terminalCapitalNominalAtEnd(), result.finiteTargetProjectedFinalBalance());
        assertMoney(result.terminalCapitalNominalAtEnd(), result.targetDecumulationFinalBalance());
        assertMoney(result.terminalCapitalNominalAtEnd(), result.personalDecumulationFinalBalance());
        assertMoney(result.totalNominalContributions(), cumulativeContributions);
        assertEquals(result.accumulationMonths() + 1, result.accumulationProjection().size());
        assertEquals(result.fireMonths() + 1, result.decumulationProjection().size());
        assertTrue(result.existingInvestments().isEmpty());
        assertTrue(result.futureLumpSums().isEmpty());
        assertMoney(0, result.totalCapitalInflows());
        assertMoney(0, result.totalShortfall());
        assertNull(result.depletionMonth());
    }

    @ParameterizedTest(name = "invalid rate field: {0}")
    @MethodSource("invalidRateInputs")
    void rejectsEveryAnnualRateAtOrBelowMinusOne(String field, FireCalculationInput input) {
        FireCalculationException exception = assertThrows(
                FireCalculationException.class,
                () -> calculator.calculate(input)
        );

        assertEquals(CalculationErrorCode.INVALID_RATE, exception.code(), field);
    }

    @ParameterizedTest(name = "invalid SWR {0}")
    @ValueSource(doubles = {-0.01, 0.0})
    void rejectsNonPositiveSwr(double swr) {
        FireCalculationException exception = assertThrows(
                FireCalculationException.class,
                () -> calculator.calculate(directionalInput(FireMethod.SWR, 1_000, swr, 30, 0.04, 0))
        );

        assertEquals(CalculationErrorCode.INVALID_SWR, exception.code());
    }

    @Test
    void nearMinusOneFireReturnRemainsFiniteAndReconciles() {
        FireCalculationInput input = new FireCalculationInput(
                FireMethod.FINITE, 40, 41, 1, 10, 0, -0.999999,
                null, 0, 0, 0, 0
        );

        FireCalculationResult result = calculator.calculate(input);

        assertTrue(Double.isFinite(result.selectedTarget()));
        assertTrue(Double.isFinite(result.initialMonthlyContribution()));
        assertFalse(result.decumulationProjection().isEmpty());
        assertMoney(0, result.finiteTargetProjectedFinalBalance());
        assertMoney(0, result.targetDecumulationFinalBalance());
    }

    private static Stream<Arguments> horizonScenarios() {
        return Stream.of(FireMethod.FINITE, FireMethod.SWR)
                .flatMap(method -> Stream.of(
                        Arguments.of(method, 50, 50),
                        Arguments.of(method, 49, 50),
                        Arguments.of(method, 20, 60)
                ));
    }

    private static Stream<Arguments> invalidRateInputs() {
        return Stream.of(
                Arguments.of("inflation", rateValidationInput(-1.0, 0, 0, 0)),
                Arguments.of("fire return", rateValidationInput(0, -1.0, 0, 0)),
                Arguments.of("accumulation return", rateValidationInput(0, 0, -1.0, 0)),
                Arguments.of("contribution growth", rateValidationInput(0, 0, 0, -1.0))
        );
    }

    private static FireCalculationInput rateValidationInput(
            double inflation,
            double fireReturn,
            double accumulationReturn,
            double contributionGrowth
    ) {
        return new FireCalculationInput(
                FireMethod.FINITE, 40, 41, 1, 100, inflation, fireReturn,
                null, 0, 0, accumulationReturn, contributionGrowth
        );
    }

    private static FireCalculationInput simplePacInput(double annualGrowth, double annualReturn) {
        return simplePacInput(annualGrowth, annualReturn, 0);
    }

    private static FireCalculationInput simplePacInput(
            double annualGrowth,
            double annualReturn,
            double currentCapital
    ) {
        return new FireCalculationInput(
                FireMethod.FINITE, 40, 41, 1, 100, 0, 0,
                null, 0, currentCapital, annualReturn, annualGrowth, List.of()
        );
    }

    private static FireCalculationInput directionalInput(
            FireMethod method,
            double monthlyExpense,
            double swr,
            int fireYears,
            double fireReturn,
            double currentCapital
    ) {
        return new FireCalculationInput(
                method, 40, 50, fireYears, monthlyExpense, 0, fireReturn,
                method == FireMethod.SWR ? swr : null, 0, currentCapital, 0.05, 0, List.of()
        );
    }

    private static double lastContribution(FireCalculationResult result) {
        return result.accumulationProjection().get(result.accumulationMonths()).contribution();
    }

    private static void assertMoney(double expected, Double actual) {
        assertEquals(expected, actual, MONEY_TOLERANCE);
    }
}
