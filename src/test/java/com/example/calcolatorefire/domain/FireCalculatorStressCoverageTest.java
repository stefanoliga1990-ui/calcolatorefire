package com.example.calcolatorefire.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class FireCalculatorStressCoverageTest {

    private static final double MONEY_TOLERANCE = 0.01;

    private final FireCalculator calculator = new FireCalculator();

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void handlesOneHundredAndFortyYearsOfMonthlyProjections(FireMethod method) {
        List<AdditionalResource> resources = List.of(
                new ExistingInvestment("PAC lungo", 25_000, 100, 20, 80, 0.04, 0.01, true),
                new PeriodicIncome("Rendita lunga", 250, 0.015, 20, null, true, true),
                new FutureLumpSum("Capitale lontano", 100_000, AmountBasis.TODAY, 120, false, 0)
        );
        FireCalculationResult result = calculator.calculate(input(
                method, 20, 80, 80, 2_000, 0.02, 0.04,
                500_000, 10_000, 0.05, 0.01, resources));

        assertEquals(720, result.accumulationMonths());
        assertEquals(960, result.fireMonths());
        assertEquals(721, result.accumulationProjection().size());
        assertEquals(961, result.decumulationProjection().size());
        assertEquals(721, result.existingInvestments().get(0).projection().size());
        assertFiniteResult(result);
    }

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void handlesOneHundredAndFiftyOverlappingResources(FireMethod method) {
        List<AdditionalResource> resources = manyResources(30, 50, 40, 50);
        FireCalculationResult result = calculator.calculate(input(
                method, 30, 50, 40, 2_500, 0.02, 0.04,
                100_000, 50_000, 0.05, 0.01, resources));

        assertEquals(150, resources.size());
        assertEquals(50, result.existingInvestments().size());
        assertEquals(50, result.futureLumpSums().size());
        assertEquals(149, result.futureLumpSums().get(49).resourceIndex());
        assertTrue(result.totalNominalAdditionalIncomeInvested() > 0);
        assertTrue(result.totalCapitalInflows() > 0);
        assertFiniteResult(result);
    }

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void keepsCentFractionsFiniteAcrossAllResourceTypes(FireMethod method) {
        List<AdditionalResource> resources = List.of(
                new ExistingInvestment("Micro investimento", 0.01, 0.01, 40, 41,
                        0.0001, -0.0001, true),
                new PeriodicIncome("Micro rendita", 0.01, 0.0001, 40, null, true, true),
                new FutureLumpSum("Micro capitale", 0.01, AmountBasis.TODAY, 41, false, 0)
        );
        FireCalculationResult result = calculator.calculate(input(
                method, 40, 41, 2, 0.05, 0.0001, 0.0001,
                0.01, 0.01, 0.0001, -0.0001, resources));

        assertTrue(result.selectedTarget() > 0);
        assertTrue(result.projectedAccumulationFinalBalance() > 0);
        assertFiniteResult(result);
    }

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void keepsBillionEuroScenariosFinite(FireMethod method) {
        List<AdditionalResource> resources = List.of(
                new ExistingInvestment("Grande investimento", 2_000_000_000.0, 2_000_000.0,
                        30, 60, 0.12, 0.03, true),
                new PeriodicIncome("Grande rendita", 2_000_000.0, 0.03,
                        30, null, true, true),
                new FutureLumpSum("Grande capitale", 5_000_000_000.0,
                        AmountBasis.TODAY, 70, false, 0)
        );
        FireCalculationResult result = calculator.calculate(input(
                method, 30, 60, 50, 10_000_000.0, 0.04, 0.10,
                1_000_000_000.0, 3_000_000_000.0, 0.15, 0.03, resources));

        assertTrue(result.selectedTarget() > 1_000_000_000.0);
        assertTrue(result.projectedAccumulationFinalBalance() > 1_000_000_000.0);
        assertFiniteResult(result);
    }

    @ParameterizedTest
    @MethodSource("methodsAndBoundaryRates")
    void acceptsRatesImmediatelyAboveTheLowerBoundary(FireMethod method, double boundaryRate) {
        List<FireCalculationInput> inputs = List.of(
                input(method, 40, 41, 1, 100, 0, boundaryRate,
                        0, 0, 0, 0, List.of()),
                input(method, 40, 41, 1, 100, 0, 0,
                        0, 0, boundaryRate, 0, List.of()),
                input(method, 40, 41, 1, 100, 0, 0,
                        0, 0, 0, boundaryRate, List.of()),
                input(method, 40, 41, 1, 100, boundaryRate, 0,
                        0, 0, 0, 0, List.of())
        );

        for (FireCalculationInput input : inputs) {
            assertFiniteResult(calculator.calculate(input));
        }
    }

    private static Stream<Arguments> methodsAndBoundaryRates() {
        return Stream.of(FireMethod.values())
                .flatMap(method -> Stream.of(-0.999999, -0.99, -0.90)
                        .map(rate -> Arguments.of(method, rate)));
    }

    @ParameterizedTest
    @ValueSource(ints = {178_956_971, Integer.MAX_VALUE})
    void rejectsHorizonsThatCannotBeRepresentedInMonths(int years) {
        FireCalculationInput input = new FireCalculationInput(
                FireMethod.FINITE, 0, 1, years, 1_000,
                0.02, 0.04, null, 0, 0, 0.05, 0, List.of());

        FireCalculationException exception = assertThrows(
                FireCalculationException.class,
                () -> calculator.calculate(input));

        assertEquals(CalculationErrorCode.INVALID_FIRE_DURATION, exception.code());
    }

    private static List<AdditionalResource> manyResources(
            int currentAge,
            int fireAge,
            int fireYears,
            int resourcesPerType
    ) {
        List<AdditionalResource> resources = new ArrayList<>(resourcesPerType * 3);
        for (int index = 0; index < resourcesPerType; index++) {
            resources.add(new ExistingInvestment(
                    "Investimento " + index,
                    1_000 + index * 100,
                    10 + index,
                    currentAge,
                    fireAge,
                    0.02 + index * 0.0005,
                    index % 2 == 0 ? 0 : 0.01,
                    index % 3 != 0
            ));
        }
        for (int index = 0; index < resourcesPerType; index++) {
            resources.add(new PeriodicIncome(
                    "Rendita " + index,
                    10 + index,
                    index % 2 == 0 ? 0 : 0.01,
                    currentAge + index % 10,
                    null,
                    true,
                    true
            ));
        }
        int horizonEndAge = fireAge + fireYears;
        for (int index = 0; index < resourcesPerType; index++) {
            int receiptAge = currentAge + index % (horizonEndAge - currentAge + 1);
            resources.add(new FutureLumpSum(
                    "Capitale " + index,
                    1_000 + index * 250,
                    index % 2 == 0 ? AmountBasis.TODAY : AmountBasis.NOMINAL,
                    receiptAge,
                    receiptAge < fireAge && index % 2 == 0,
                    0.03
            ));
        }
        return List.copyOf(resources);
    }

    private static FireCalculationInput input(
            FireMethod method,
            int currentAge,
            int fireAge,
            int fireYears,
            double monthlyExpense,
            double inflation,
            double fireReturn,
            double terminalCapital,
            double currentCapital,
            double accumulationReturn,
            double contributionGrowth,
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
                terminalCapital,
                currentCapital,
                accumulationReturn,
                contributionGrowth,
                resources
        );
    }

    private static void assertFiniteResult(FireCalculationResult result) {
        assertFinite(result.selectedTarget());
        assertFinite(result.selectedTargetToday());
        assertFinite(result.firstMonthlyWithdrawal());
        assertFinite(result.capitalGap());
        assertFinite(result.initialMonthlyContribution());
        assertFinite(result.projectedAccumulationFinalBalance());
        assertFinite(result.personalDecumulationFinalBalance());
        assertFinite(result.totalShortfall());
        for (AccumulationPoint point : result.accumulationProjection()) {
            assertFinite(point.openingBalance());
            assertFiniteSigned(point.investmentReturn());
            assertFinite(point.contribution());
            assertFinite(point.additionalIncomeContribution());
            assertFinite(point.closingBalance());
            assertFinite(point.totalAvailableBalance());
        }
        for (DecumulationPoint point : result.decumulationProjection()) {
            assertFinite(point.openingBalance());
            assertFinite(point.grossExpense());
            assertFinite(point.additionalIncome());
            assertFinite(point.scheduledWithdrawal());
            assertFinite(point.capitalInflow());
            assertFinite(point.actualWithdrawal());
            assertFinite(point.shortfall());
            assertFiniteSigned(point.investmentReturn());
            assertFinite(point.closingBalance());
        }
    }

    private static void assertFinite(double value) {
        assertTrue(Double.isFinite(value), () -> "Valore non finito: " + value);
        assertTrue(value >= -MONEY_TOLERANCE, () -> "Valore monetario negativo: " + value);
    }

    private static void assertFiniteSigned(double value) {
        assertTrue(Double.isFinite(value), () -> "Valore non finito: " + value);
    }
}
