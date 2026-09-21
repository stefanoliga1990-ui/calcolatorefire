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

class FireCalculatorPeriodicIncomeCoverageTest {

    private static final double MONEY_TOLERANCE = 0.01;
    private static final double RATE_TOLERANCE = 1e-12;

    private final FireCalculator calculator = new FireCalculator();

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void usageFlagsRouteIncomeToAccumulationFireOrBoth(FireMethod method) {
        PeriodicIncome beforeOnly = income("Prima", 100, 0, 40, null, true, false);
        PeriodicIncome fireOnly = income("Durante", 100, 0, 40, null, false, true);
        PeriodicIncome both = income("Entrambe", 100, 0, 40, null, true, true);

        FireCalculationResult beforeResult = calculator.calculate(input(
                method, 40, 50, 1, 200, 0, 0, 0, 0, List.of(beforeOnly)));
        FireCalculationResult fireResult = calculator.calculate(input(
                method, 40, 50, 1, 200, 0, 0, 0, 0, List.of(fireOnly)));
        FireCalculationResult bothResult = calculator.calculate(input(
                method, 40, 50, 1, 200, 0, 0, 0, 0, List.of(both)));

        assertMoney(12_000, beforeResult.totalNominalAdditionalIncomeInvested());
        assertMoney(0, beforeResult.firstMonthlyAdditionalIncome());
        assertMoney(0, fireResult.totalNominalAdditionalIncomeInvested());
        assertMoney(100, fireResult.firstMonthlyAdditionalIncome());
        assertMoney(12_000, bothResult.totalNominalAdditionalIncomeInvested());
        assertMoney(100, bothResult.firstMonthlyAdditionalIncome());
        assertMoney(fireResult.selectedTarget(), bothResult.selectedTarget());
        assertTrue(bothResult.initialMonthlyContribution() < fireResult.initialMonthlyContribution());
    }

    @Test
    void investedIncomeKeepsEndOfMonthTimingWithPositiveReturn() {
        PeriodicIncome resource = income("Entrata investita", 100, 0, 40, 41, true, false);
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 40, 41, 1, 0, 0, 0, 0, 0.12, List.of(resource)));
        double monthlyReturn = Math.pow(1.12, 1.0 / 12.0) - 1.0;
        AccumulationPoint first = result.accumulationProjection().get(1);
        AccumulationPoint second = result.accumulationProjection().get(2);

        assertMoney(0, first.openingBalance());
        assertMoney(0, first.investmentReturn());
        assertMoney(100, first.additionalIncomeContribution());
        assertMoney(100, first.closingBalance());
        assertMoney(100, second.openingBalance());
        assertMoney(100 * monthlyReturn, second.investmentReturn());
        assertMoney(100, second.additionalIncomeContribution());
        assertMoney(200 + 100 * monthlyReturn, second.closingBalance());
    }

    @Test
    void futureIncomeGrowsFromTodayAndStartsAtItsIncludedBoundary() {
        PeriodicIncome resource = income("Entrata futura", 100, 0.02, 45, null, true, false);
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 40, 50, 1, 0, 0, 0, 0, 0, List.of(resource)));
        double expectedAtStart = 100 * Math.pow(1.02, 5);

        assertMoney(0, result.accumulationProjection().get(60).additionalIncomeContribution());
        assertMoney(expectedAtStart,
                result.accumulationProjection().get(61).additionalIncomeContribution());
        assertMoney(expectedAtStart,
                result.accumulationProjection().get(61).closingBalance());
    }

    @Test
    void incomeStartingAtFireReducesTheFirstWithdrawal() {
        PeriodicIncome resource = income("Dal FIRE", 500, 0, 50, null, false, true);
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 40, 50, 10, 2_000, 0, 0, 1_000_000, 0, List.of(resource)));

        assertMoney(500, result.firstMonthlyAdditionalIncome());
        assertMoney(1_500, result.firstMonthlyNetWithdrawal());
        assertMoney(500, result.decumulationProjection().get(1).additionalIncome());
    }

    @Test
    void incomeEndingAtFireIsExcludedFromTheFirstWithdrawal() {
        PeriodicIncome resource = income("Fino al FIRE", 500, 0, 40, 50, true, true);
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 40, 50, 10, 2_000, 0, 0, 1_000_000, 0, List.of(resource)));

        assertMoney(60_000, result.totalNominalAdditionalIncomeInvested());
        assertMoney(0, result.firstMonthlyAdditionalIncome());
        assertMoney(2_000, result.firstMonthlyNetWithdrawal());
    }

    @Test
    void incomeEndingAtHorizonIsIncludedInTheLastFireMonth() {
        PeriodicIncome resource = income("Per tutto il FIRE", 500, 0, 50, 60, false, true);
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 40, 50, 10, 2_000, 0, 0, 1_000_000, 0, List.of(resource)));

        assertMoney(500, result.decumulationProjection().get(1).additionalIncome());
        assertMoney(500, result.decumulationProjection().get(120).additionalIncome());
        assertMoney(1_500, result.decumulationProjection().get(120).scheduledWithdrawal());
    }

    @ParameterizedTest(name = "income amount {1} with {0}")
    @MethodSource("expenseOffsetScenarios")
    void incomeBelowEqualOrAboveExpenseNeverCreatesANegativeWithdrawal(
            FireMethod method,
            double incomeAmount,
            double expectedTarget,
            double expectedWithdrawal
    ) {
        PeriodicIncome resource = income("Rendita", incomeAmount, 0, 50, null, false, true);
        FireCalculationResult result = calculator.calculate(input(
                method, 50, 50, 1, 2_000, 0, 0, 1_000_000, 0, List.of(resource)));

        assertMoney(expectedTarget, result.selectedTarget());
        assertMoney(expectedWithdrawal, result.firstMonthlyNetWithdrawal());
        assertMoney(expectedWithdrawal, result.decumulationProjection().get(1).scheduledWithdrawal());
        assertTrue(result.firstMonthlyNetWithdrawal() >= 0.0);
    }

    @ParameterizedTest(name = "income growth {0}")
    @ValueSource(doubles = {-0.12, 0.0, 0.02, 0.12})
    void incomeGrowthUsesTheMonthlyEquivalentDuringAccumulation(double annualGrowth) {
        PeriodicIncome resource = income("Rendita crescente", 100, annualGrowth, 40, 41, true, false);
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 40, 41, 1, 0, 0, 0, 0, 0, List.of(resource)));
        double monthlyGrowth = Math.pow(1.0 + annualGrowth, 1.0 / 12.0) - 1.0;
        double expectedTotal = geometricSum(100, monthlyGrowth, 12);

        assertMoney(expectedTotal, result.totalNominalAdditionalIncomeInvested());
        assertMoney(expectedTotal, result.projectedInvestedIncomeFinalBalance());
        assertMoney(100, result.accumulationProjection().get(1).additionalIncomeContribution());
        assertMoney(100 * Math.pow(1.0 + monthlyGrowth, 11),
                result.accumulationProjection().get(12).additionalIncomeContribution());
    }

    @ParameterizedTest(name = "FIRE income growth {0}")
    @ValueSource(doubles = {-0.12, 0.0, 0.02, 0.12})
    void incomeAndExpenseKeepTheirIndependentGrowthDuringFire(double annualGrowth) {
        PeriodicIncome resource = income("Rendita FIRE", 100, annualGrowth, 50, null, false, true);
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 50, 50, 2, 1_000, 0.02, 0, 1_000_000, 0, List.of(resource)));
        DecumulationPoint monthThirteen = result.decumulationProjection().get(13);
        double expectedExpense = 1_000 * 1.02;
        double expectedIncome = 100 * (1.0 + annualGrowth);

        assertMoney(expectedExpense, monthThirteen.grossExpense());
        assertMoney(expectedIncome, monthThirteen.additionalIncome());
        assertMoney(Math.max(0, expectedExpense - expectedIncome), monthThirteen.scheduledWithdrawal());
    }

    @Test
    void multiplePermanentIncomesProduceSuccessiveSwrBridgeRegimes() {
        PeriodicIncome first = income("Rendita a 55", 500, 0, 55, null, false, true);
        PeriodicIncome second = income("Rendita a 60", 300, 0, 60, null, false, true);
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.SWR, 50, 50, 30, 2_000, 0, 0, 570_000, 0,
                List.of(first, second)));

        assertMoney(600_000, result.safeWithdrawalRateBaseTarget());
        assertMoney(570_000, result.safeWithdrawalRateTarget());
        assertMoney(2_000, result.decumulationProjection().get(60).scheduledWithdrawal());
        assertMoney(1_500, result.decumulationProjection().get(61).scheduledWithdrawal());
        assertMoney(1_500, result.decumulationProjection().get(120).scheduledWithdrawal());
        assertMoney(1_200, result.decumulationProjection().get(121).scheduledWithdrawal());
    }

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void multipleIncomesAreAdditiveAndIndependentFromListOrder(FireMethod method) {
        PeriodicIncome first = income("Affitto", 200, 0, 40, null, true, true);
        PeriodicIncome second = income("Pensione", 300, 0, 55, null, false, true);
        FireCalculationResult ordered = calculator.calculate(input(
                method, 40, 50, 20, 2_000, 0, 0, 0, 0, List.of(first, second)));
        FireCalculationResult reversed = calculator.calculate(input(
                method, 40, 50, 20, 2_000, 0, 0, 0, 0, List.of(second, first)));

        assertMoney(ordered.selectedTarget(), reversed.selectedTarget());
        assertMoney(ordered.initialMonthlyContribution(), reversed.initialMonthlyContribution());
        assertMoney(ordered.totalNominalAdditionalIncomeInvested(),
                reversed.totalNominalAdditionalIncomeInvested());
        assertMoney(ordered.projectedInvestedIncomeFinalBalance(),
                reversed.projectedInvestedIncomeFinalBalance());
        assertMoney(ordered.firstMonthlyAdditionalIncome(), reversed.firstMonthlyAdditionalIncome());
        assertMoney(ordered.decumulationProjection().get(61).scheduledWithdrawal(),
                reversed.decumulationProjection().get(61).scheduledWithdrawal());
    }

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void splittingAnIncomeIntoEquivalentPartsPreservesResults(FireMethod method) {
        PeriodicIncome combined = income("Unica", 200, 0.02, 40, null, true, true);
        PeriodicIncome firstHalf = income("Metà A", 100, 0.02, 40, null, true, true);
        PeriodicIncome secondHalf = income("Metà B", 100, 0.02, 40, null, true, true);
        FireCalculationResult oneResource = calculator.calculate(input(
                method, 40, 50, 20, 2_000, 0.02, 0.04, 0, 0.05, List.of(combined)));
        FireCalculationResult twoResources = calculator.calculate(input(
                method, 40, 50, 20, 2_000, 0.02, 0.04, 0, 0.05,
                List.of(firstHalf, secondHalf)));

        assertMoney(oneResource.selectedTarget(), twoResources.selectedTarget());
        assertMoney(oneResource.initialMonthlyContribution(), twoResources.initialMonthlyContribution());
        assertMoney(oneResource.totalNominalAdditionalIncomeInvested(),
                twoResources.totalNominalAdditionalIncomeInvested());
        assertMoney(oneResource.projectedInvestedIncomeFinalBalance(),
                twoResources.projectedInvestedIncomeFinalBalance());
        assertMoney(oneResource.personalDecumulationFinalBalance(),
                twoResources.personalDecumulationFinalBalance());
    }

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void zeroIncomeIsMathematicallyNeutral(FireMethod method) {
        PeriodicIncome zero = income("Nessun importo", 0, 0.02, 40, null, true, true);
        FireCalculationResult withoutResource = calculator.calculate(input(
                method, 40, 50, 20, 2_000, 0.02, 0.04, 10_000, 0.05, List.of()));
        FireCalculationResult withZeroResource = calculator.calculate(input(
                method, 40, 50, 20, 2_000, 0.02, 0.04, 10_000, 0.05, List.of(zero)));

        assertMoney(withoutResource.selectedTarget(), withZeroResource.selectedTarget());
        assertMoney(withoutResource.initialMonthlyContribution(),
                withZeroResource.initialMonthlyContribution());
        assertMoney(withoutResource.projectedAccumulationFinalBalance(),
                withZeroResource.projectedAccumulationFinalBalance());
        assertMoney(withoutResource.personalDecumulationFinalBalance(),
                withZeroResource.personalDecumulationFinalBalance());
    }

    @ParameterizedTest(name = "invalid periodic income: {0}")
    @MethodSource("invalidIncomeScenarios")
    void rejectsInvalidIncomeConfiguration(
            String label,
            PeriodicIncome resource,
            CalculationErrorCode expectedCode
    ) {
        FireCalculationException exception = assertThrows(
                FireCalculationException.class,
                () -> calculator.calculate(input(
                        FireMethod.FINITE, 40, 50, 10, 1_000, 0, 0, 0, 0,
                        List.of(resource)))
        );

        assertEquals(expectedCode, exception.code(), label);
    }

    private static Stream<Arguments> expenseOffsetScenarios() {
        return Stream.of(
                Arguments.of(FireMethod.FINITE, 500, 18_000, 1_500),
                Arguments.of(FireMethod.FINITE, 2_000, 0, 0),
                Arguments.of(FireMethod.FINITE, 2_500, 0, 0),
                Arguments.of(FireMethod.SWR, 500, 450_000, 1_500),
                Arguments.of(FireMethod.SWR, 2_000, 0, 0),
                Arguments.of(FireMethod.SWR, 2_500, 0, 0)
        );
    }

    private static Stream<Arguments> invalidIncomeScenarios() {
        return Stream.of(
                Arguments.of("negative amount",
                        income("Negativa", -1, 0, 40, null, true, false),
                        CalculationErrorCode.INVALID_AMOUNT),
                Arguments.of("growth at minus one",
                        income("Crescita", 100, -1, 40, null, true, false),
                        CalculationErrorCode.INVALID_RATE),
                Arguments.of("unused",
                        income("Non usata", 100, 0, 40, null, false, false),
                        CalculationErrorCode.INVALID_RESOURCE),
                Arguments.of("starts before current age",
                        income("Passata", 100, 0, 39, null, true, false),
                        CalculationErrorCode.INVALID_RESOURCE_PERIOD),
                Arguments.of("empty interval",
                        income("Vuota", 100, 0, 42, 42, true, false),
                        CalculationErrorCode.INVALID_RESOURCE_PERIOD),
                Arguments.of("accumulation flag without accumulation overlap",
                        income("Troppo tardi", 100, 0, 50, null, true, false),
                        CalculationErrorCode.INVALID_RESOURCE_PERIOD),
                Arguments.of("fire flag without FIRE overlap",
                        income("Finita", 100, 0, 40, 50, false, true),
                        CalculationErrorCode.INVALID_RESOURCE_PERIOD),
                Arguments.of("starts at horizon end",
                        income("Fuori orizzonte", 100, 0, 60, null, false, true),
                        CalculationErrorCode.INVALID_RESOURCE_PERIOD)
        );
    }

    private static PeriodicIncome income(
            String name,
            double monthlyAmount,
            double annualGrowth,
            int startAge,
            Integer endAge,
            boolean investBeforeFire,
            boolean offsetDuringFire
    ) {
        return new PeriodicIncome(
                name,
                monthlyAmount,
                annualGrowth,
                startAge,
                endAge,
                investBeforeFire,
                offsetDuringFire
        );
    }

    private static FireCalculationInput input(
            FireMethod method,
            int currentAge,
            int fireAge,
            int fireYears,
            double monthlyExpense,
            double inflation,
            double fireReturn,
            double currentCapital,
            double accumulationReturn,
            List<AdditionalResource> resources
    ) {
        return new FireCalculationInput(
                method,
                currentAge,
                fireAge,
                fireYears,
                monthlyExpense,
                inflation,
                fireReturn,
                method == FireMethod.SWR ? 0.04 : null,
                0,
                currentCapital,
                accumulationReturn,
                0,
                resources
        );
    }

    private static double geometricSum(double first, double monthlyGrowth, int months) {
        if (Math.abs(monthlyGrowth) < RATE_TOLERANCE) {
            return first * months;
        }
        return first * (Math.pow(1.0 + monthlyGrowth, months) - 1.0) / monthlyGrowth;
    }

    private static void assertMoney(double expected, double actual) {
        assertEquals(expected, actual, MONEY_TOLERANCE);
    }
}
