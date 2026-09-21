package com.example.calcolatorefire.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class FireCalculatorExistingInvestmentCoverageTest {

    private static final double MONEY_TOLERANCE = 0.01;
    private static final double RATE_TOLERANCE = 1e-12;

    private final FireCalculator calculator = new FireCalculator();

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void capitalPacAndTheirCombinationRemainSeparateFromTheNewPac(FireMethod method) {
        FireCalculationResult capitalOnly = calculator.calculate(input(
                method, 40, 41, 1, 300, 0,
                List.of(investment("Solo capitale", 1_000, 0, null, null, 0, 0, true))
        ));
        FireCalculationResult pacOnly = calculator.calculate(input(
                method, 40, 41, 1, 300, 0,
                List.of(investment("Solo PAC", 0, 100, 40, 41, 0, 0, true))
        ));
        FireCalculationResult capitalAndPac = calculator.calculate(input(
                method, 40, 41, 1, 300, 0,
                List.of(investment("Capitale e PAC", 1_000, 100, 40, 41, 0, 0, true))
        ));

        assertInvestmentTotals(capitalOnly, 0, 1_000);
        assertInvestmentTotals(pacOnly, 1_200, 1_200);
        assertInvestmentTotals(capitalAndPac, 1_200, 2_200);
        assertTrue(capitalAndPac.initialMonthlyContribution() < capitalOnly.initialMonthlyContribution());
        assertTrue(capitalAndPac.initialMonthlyContribution() < pacOnly.initialMonthlyContribution());
        assertMoney(capitalAndPac.selectedTarget(), capitalAndPac.projectedAccumulationFinalBalance());
    }

    @ParameterizedTest(name = "contributions from age {0} to {1}")
    @MethodSource("contributionWindows")
    void contributionPeriodIncludesStartAndExcludesEnd(
            int startAge,
            int endAge,
            int firstContributionMonth,
            int lastContributionMonth,
            double expectedTotal
    ) {
        ExistingInvestment resource = investment(
                "PAC con finestra", 0, 100, startAge, endAge, 0, 0, true);
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 40, 45, 1, 0, 0, List.of(resource)));
        ExistingInvestmentResult projection = result.existingInvestments().get(0);

        assertMoney(expectedTotal, projection.totalNominalContributions());
        assertMoney(expectedTotal, projection.finalBalance());
        if (firstContributionMonth > 1) {
            assertMoney(0, projection.projection().get(firstContributionMonth - 1).contribution());
        }
        assertMoney(100, projection.projection().get(firstContributionMonth).contribution());
        assertMoney(100, projection.projection().get(lastContributionMonth).contribution());
        if (lastContributionMonth < result.accumulationMonths()) {
            assertMoney(0, projection.projection().get(lastContributionMonth + 1).contribution());
        }
    }

    @ParameterizedTest(name = "investment return {0}")
    @ValueSource(doubles = {-0.20, 0.0, 0.05})
    void annualInvestmentReturnCompoundsWithTheMonthlyEquivalent(double annualReturn) {
        ExistingInvestment resource = investment(
                "Capitale", 10_000, 0, null, null, annualReturn, 0, true);
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 40, 50, 1, 0, 0, List.of(resource)));
        double expected = 10_000 * Math.pow(1.0 + annualReturn, 10);

        assertMoney(expected, result.existingInvestments().get(0).finalBalance());
        assertMoney(expected, result.projectedAvailableExistingInvestmentsFinalBalance());
    }

    @ParameterizedTest(name = "contribution growth {0}")
    @ValueSource(doubles = {-0.12, 0.0, 0.12})
    void annualContributionGrowthUsesItsMonthlyEquivalent(double annualGrowth) {
        ExistingInvestment resource = investment(
                "PAC crescente", 0, 100, 40, 41, 0, annualGrowth, true);
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 40, 41, 1, 0, 0, List.of(resource)));
        double monthlyGrowth = Math.pow(1.0 + annualGrowth, 1.0 / 12.0) - 1.0;
        double expectedContributions = geometricSum(100, monthlyGrowth, 12);

        assertMoney(expectedContributions, result.totalNominalExistingInvestmentContributions());
        assertMoney(expectedContributions, result.existingInvestments().get(0).finalBalance());
        assertMoney(100, result.existingInvestments().get(0).projection().get(1).contribution());
        assertMoney(100 * Math.pow(1.0 + monthlyGrowth, 11),
                result.existingInvestments().get(0).projection().get(12).contribution());
    }

    @Test
    void everyExistingInvestmentMonthReconcilesIndependently() {
        ExistingInvestment resource = investment(
                "PAC autonomo", 5_000, 200, 40, 43, 0.03, 0.02, true);
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 40, 45, 1, 0, 0, List.of(resource)));
        ExistingInvestmentResult investment = result.existingInvestments().get(0);
        double monthlyReturn = Math.pow(1.03, 1.0 / 12.0) - 1.0;
        double monthlyGrowth = Math.pow(1.02, 1.0 / 12.0) - 1.0;
        double cumulativeContributions = 0.0;

        for (int month = 1; month <= result.accumulationMonths(); month++) {
            ExistingInvestmentPoint previous = investment.projection().get(month - 1);
            ExistingInvestmentPoint point = investment.projection().get(month);
            double expectedContribution = month <= 36
                    ? 200 * Math.pow(1.0 + monthlyGrowth, month - 1)
                    : 0.0;
            cumulativeContributions += expectedContribution;

            assertMoney(previous.closingBalance(), point.openingBalance());
            assertMoney(point.openingBalance() * monthlyReturn, point.investmentReturn());
            assertMoney(expectedContribution, point.contribution());
            assertMoney(point.openingBalance() + point.investmentReturn() + point.contribution(),
                    point.closingBalance());
            assertMoney(cumulativeContributions, point.cumulativeContributions());
        }

        assertMoney(investment.finalBalance(), result.projectedAvailableExistingInvestmentsFinalBalance());
        assertMoney(investment.totalNominalContributions(), result.totalNominalExistingInvestmentContributions());
    }

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void availabilityChangesThePacGapButNotTheInvestmentProjection(FireMethod method) {
        ExistingInvestment available = investment(
                "Disponibile", 600, 0, null, null, 0, 0, true);
        ExistingInvestment unavailable = investment(
                "Non disponibile", 600, 0, null, null, 0, 0, false);
        FireCalculationResult withAvailable = calculator.calculate(input(
                method, 40, 41, 1, 100, 0, List.of(available)));
        FireCalculationResult withUnavailable = calculator.calculate(input(
                method, 40, 41, 1, 100, 0, List.of(unavailable)));

        assertMoney(withAvailable.selectedTarget(), withUnavailable.selectedTarget());
        assertMoney(600, withAvailable.existingInvestments().get(0).finalBalance());
        assertMoney(600, withUnavailable.existingInvestments().get(0).finalBalance());
        assertMoney(600, withAvailable.projectedAvailableExistingInvestmentsFinalBalance());
        assertMoney(0, withUnavailable.projectedAvailableExistingInvestmentsFinalBalance());
        assertMoney(withUnavailable.capitalGap() - 600, withAvailable.capitalGap());
        assertMoney(withUnavailable.initialMonthlyContribution() - 50,
                withAvailable.initialMonthlyContribution());
    }

    @Test
    void unavailablePacIsReportedButExcludedFromAvailableCapital() {
        ExistingInvestment resource = investment(
                "PAC con altro scopo", 500, 100, 40, 41, 0, 0, false);

        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 40, 41, 1, 300, 0, List.of(resource)));

        assertMoney(3_600, result.selectedTarget());
        assertMoney(3_600, result.capitalGap());
        assertMoney(300, result.initialMonthlyContribution());
        assertMoney(1_200, result.totalNominalExistingInvestmentContributions());
        assertMoney(1_700, result.existingInvestments().get(0).finalBalance());
        assertMoney(0, result.projectedAvailableExistingInvestmentsFinalBalance());
        assertMoney(3_600, result.projectedAccumulationFinalBalance());
    }

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void availableInvestmentCanFundAZeroMonthScenario(FireMethod method) {
        double requiredCapital = method == FireMethod.FINITE ? 1_200 : 30_000;
        ExistingInvestment resource = investment(
                "Capitale immediato", requiredCapital, 0, null, null, 0, 0, true);

        FireCalculationResult result = calculator.calculate(input(
                method, 50, 50, 1, 100, 0, List.of(resource)));

        assertEquals(0, result.accumulationMonths());
        assertMoney(requiredCapital, result.selectedTarget());
        assertMoney(0, result.capitalGap());
        assertMoney(0, result.initialMonthlyContribution());
        assertMoney(requiredCapital, result.projectedAccumulationFinalBalance());
    }

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void unavailableInvestmentCannotFundAZeroMonthScenario(FireMethod method) {
        double nominalBalance = method == FireMethod.FINITE ? 1_200 : 30_000;
        ExistingInvestment resource = investment(
                "Capitale escluso", nominalBalance, 0, null, null, 0, 0, false);

        FireCalculationException exception = assertThrows(
                FireCalculationException.class,
                () -> calculator.calculate(input(
                        method, 50, 50, 1, 100, 0, List.of(resource)))
        );

        assertEquals(CalculationErrorCode.UNREACHABLE_WITH_ZERO_MONTHS, exception.code());
    }

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void multipleInvestmentsAreAdditiveAndIndependentFromListOrder(FireMethod method) {
        ExistingInvestment first = investment(
                "Primo", 1_000, 100, 40, 41, 0, 0, true);
        ExistingInvestment second = investment(
                "Secondo", 2_000, 50, 40, 41, 0, 0, true);

        FireCalculationResult ordered = calculator.calculate(input(
                method, 40, 41, 1, 1_000, 0, List.of(first, second)));
        FireCalculationResult reversed = calculator.calculate(input(
                method, 40, 41, 1, 1_000, 0, List.of(second, first)));

        assertMoney(4_800, ordered.projectedAvailableExistingInvestmentsFinalBalance());
        assertMoney(1_800, ordered.totalNominalExistingInvestmentContributions());
        assertMoney(ordered.selectedTarget(), reversed.selectedTarget());
        assertMoney(ordered.capitalGap(), reversed.capitalGap());
        assertMoney(ordered.initialMonthlyContribution(), reversed.initialMonthlyContribution());
        assertMoney(ordered.projectedAccumulationFinalBalance(), reversed.projectedAccumulationFinalBalance());
        assertMoney(ordered.projectedAvailableExistingInvestmentsFinalBalance(),
                reversed.projectedAvailableExistingInvestmentsFinalBalance());
        assertMoney(2_200, investmentNamed(ordered, "Primo").finalBalance());
        assertMoney(2_600, investmentNamed(ordered, "Secondo").finalBalance());
    }

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void splittingAnEquivalentInvestmentPreservesEveryAggregate(FireMethod method) {
        ExistingInvestment combined = investment(
                "Unico", 3_000, 150, 40, 41, 0.05, 0.02, true);
        ExistingInvestment firstPart = investment(
                "Parte A", 1_000, 50, 40, 41, 0.05, 0.02, true);
        ExistingInvestment secondPart = investment(
                "Parte B", 2_000, 100, 40, 41, 0.05, 0.02, true);

        FireCalculationResult oneResource = calculator.calculate(input(
                method, 40, 41, 1, 1_000, 0, List.of(combined)));
        FireCalculationResult twoResources = calculator.calculate(input(
                method, 40, 41, 1, 1_000, 0, List.of(firstPart, secondPart)));

        assertMoney(oneResource.projectedAvailableExistingInvestmentsFinalBalance(),
                twoResources.projectedAvailableExistingInvestmentsFinalBalance());
        assertMoney(oneResource.totalNominalExistingInvestmentContributions(),
                twoResources.totalNominalExistingInvestmentContributions());
        assertMoney(oneResource.capitalGap(), twoResources.capitalGap());
        assertMoney(oneResource.initialMonthlyContribution(), twoResources.initialMonthlyContribution());
        assertMoney(oneResource.projectedAccumulationFinalBalance(),
                twoResources.projectedAccumulationFinalBalance());
        assertEquals(1, oneResource.existingInvestments().size());
        assertEquals(2, twoResources.existingInvestments().size());
    }

    @ParameterizedTest(name = "invalid contribution period: {0}")
    @MethodSource("invalidContributionPeriods")
    void rejectsInvalidContributionPeriods(String label, ExistingInvestment resource) {
        FireCalculationException exception = assertThrows(
                FireCalculationException.class,
                () -> calculator.calculate(input(
                        FireMethod.FINITE, 40, 45, 1, 0, 0, List.of(resource)))
        );

        assertEquals(CalculationErrorCode.INVALID_RESOURCE_PERIOD, exception.code(), label);
    }

    @ParameterizedTest(name = "invalid investment rate: {0}")
    @MethodSource("invalidInvestmentRates")
    void rejectsInvestmentRatesAtOrBelowMinusOne(String label, ExistingInvestment resource) {
        FireCalculationException exception = assertThrows(
                FireCalculationException.class,
                () -> calculator.calculate(input(
                        FireMethod.FINITE, 40, 45, 1, 0, 0, List.of(resource)))
        );

        assertEquals(CalculationErrorCode.INVALID_RATE, exception.code(), label);
    }

    private static Stream<Arguments> contributionWindows() {
        return Stream.of(
                Arguments.of(40, 45, 1, 60, 6_000),
                Arguments.of(40, 42, 1, 24, 2_400),
                Arguments.of(42, 44, 25, 48, 2_400),
                Arguments.of(43, 45, 37, 60, 2_400)
        );
    }

    private static Stream<Arguments> invalidContributionPeriods() {
        return Stream.of(
                Arguments.of("missing end", investment("PAC", 0, 100, 40, null, 0, 0, true)),
                Arguments.of("before current age", investment("PAC", 0, 100, 39, 41, 0, 0, true)),
                Arguments.of("after FIRE", investment("PAC", 0, 100, 40, 46, 0, 0, true)),
                Arguments.of("empty interval", investment("PAC", 0, 100, 42, 42, 0, 0, true))
        );
    }

    private static Stream<Arguments> invalidInvestmentRates() {
        return Stream.of(
                Arguments.of("return", investment("Capitale", 1_000, 0, null, null, -1, 0, true)),
                Arguments.of("contribution growth", investment("PAC", 0, 100, 40, 45, 0, -1, true))
        );
    }

    private static ExistingInvestment investment(
            String name,
            double currentCapital,
            double monthlyContribution,
            Integer startAge,
            Integer endAge,
            double annualReturn,
            double annualContributionGrowth,
            boolean availableAtFire
    ) {
        return new ExistingInvestment(
                name,
                currentCapital,
                monthlyContribution,
                startAge,
                endAge,
                annualReturn,
                annualContributionGrowth,
                availableAtFire
        );
    }

    private static FireCalculationInput input(
            FireMethod method,
            int currentAge,
            int fireAge,
            int fireYears,
            double monthlyExpense,
            double mainCapital,
            List<AdditionalResource> resources
    ) {
        return new FireCalculationInput(
                method,
                currentAge,
                fireAge,
                fireYears,
                monthlyExpense,
                0,
                0,
                method == FireMethod.SWR ? 0.04 : null,
                0,
                mainCapital,
                0,
                0,
                resources
        );
    }

    private static ExistingInvestmentResult investmentNamed(
            FireCalculationResult result,
            String name
    ) {
        return result.existingInvestments().stream()
                .filter(investment -> name.equals(investment.name()))
                .findFirst()
                .orElseThrow();
    }

    private static double geometricSum(double first, double monthlyGrowth, int months) {
        if (Math.abs(monthlyGrowth) < RATE_TOLERANCE) {
            return first * months;
        }
        return first * (Math.pow(1.0 + monthlyGrowth, months) - 1.0) / monthlyGrowth;
    }

    private static void assertInvestmentTotals(
            FireCalculationResult result,
            double expectedContributions,
            double expectedFinalBalance
    ) {
        assertEquals(1, result.existingInvestments().size());
        assertMoney(expectedContributions, result.totalNominalExistingInvestmentContributions());
        assertMoney(expectedContributions, result.existingInvestments().get(0).totalNominalContributions());
        assertMoney(expectedFinalBalance, result.existingInvestments().get(0).finalBalance());
        assertMoney(expectedFinalBalance, result.projectedAvailableExistingInvestmentsFinalBalance());
    }

    private static void assertMoney(double expected, double actual) {
        assertEquals(expected, actual, MONEY_TOLERANCE);
    }
}
