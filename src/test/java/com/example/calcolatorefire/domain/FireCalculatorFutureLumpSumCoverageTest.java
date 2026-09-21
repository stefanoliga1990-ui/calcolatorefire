package com.example.calcolatorefire.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class FireCalculatorFutureLumpSumCoverageTest {

    private static final double MONEY_TOLERANCE = 0.01;

    private final FireCalculator calculator = new FireCalculator();

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void todayBasisIsInflatedToReceiptWhileNominalBasisIsNot(FireMethod method) {
        FutureLumpSum today = lump("Euro di oggi", 100_000, AmountBasis.TODAY, 45, false, 0);
        FutureLumpSum nominal = lump("Euro nominali", 100_000, AmountBasis.NOMINAL, 45, false, 0);

        FireCalculationResult todayResult = calculator.calculate(input(
                method, 40, 50, 1, 0, 0.02, 0, 0, 0, 0, List.of(today)));
        FireCalculationResult nominalResult = calculator.calculate(input(
                method, 40, 50, 1, 0, 0.02, 0, 0, 0, 0, List.of(nominal)));
        double expectedTodayAmount = 100_000 * Math.pow(1.02, 5);

        assertMoney(expectedTodayAmount,
                todayResult.futureLumpSums().get(0).nominalAmountAtReceipt());
        assertMoney(100_000,
                nominalResult.futureLumpSums().get(0).nominalAmountAtReceipt());
        assertMoney(expectedTodayAmount,
                todayResult.projectedAvailableFutureLumpSumsFinalBalance());
        assertMoney(100_000,
                nominalResult.projectedAvailableFutureLumpSumsFinalBalance());
    }

    @Test
    void todayBasisDuringFireUsesTheAbsoluteReceiptMonth() {
        FutureLumpSum resource = lump(
                "Capitale reale futuro", 100_000, AmountBasis.TODAY, 55, false, 0);
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 40, 50, 10, 0, 0.02, 0, 0, 0, 0,
                List.of(resource)));
        double expectedAtReceipt = 100_000 * Math.pow(1.02, 15);

        assertMoney(expectedAtReceipt, result.futureLumpSums().get(0).nominalAmountAtReceipt());
        assertMoney(expectedAtReceipt, result.decumulationProjection().get(61).capitalInflow());
        assertMoney(expectedAtReceipt, result.totalCapitalInflows());
    }

    @ParameterizedTest(name = "{0} receipt at age {1}")
    @MethodSource("receiptBoundaryScenarios")
    void receiptBoundariesClassifyCapitalExactlyOnce(
            FireMethod method,
            int receiptAge,
            double expectedAtFire,
            double expectedTotalInflows,
            double expectedTerminalInflow,
            Integer expectedFireReceiptMonth
    ) {
        FutureLumpSum resource = lump(
                "Capitale al confine", 1_000, AmountBasis.NOMINAL, receiptAge, false, 0);
        FireCalculationResult result = calculator.calculate(input(
                method, 40, 50, 10, 0, 0, 0, 0, 0, 0, List.of(resource)));
        FutureLumpSumResult projection = result.futureLumpSums().get(0);

        assertMoney(expectedAtFire, result.projectedAvailableFutureLumpSumsFinalBalance());
        assertMoney(expectedTotalInflows, result.totalCapitalInflows());
        assertMoney(expectedTerminalInflow, result.terminalCapitalInflow());
        assertEquals(expectedFireReceiptMonth, projection.fireReceiptMonth());
        assertMoney(1_000, result.personalDecumulationFinalBalance());

        if (receiptAge <= 50) {
            assertMoney(0, result.decumulationProjection().get(1).capitalInflow());
        } else if (receiptAge < 60) {
            int fireMonth = (receiptAge - 50) * 12 + 1;
            assertMoney(1_000, result.decumulationProjection().get(fireMonth).capitalInflow());
        } else {
            assertMoney(0, result.decumulationProjection().get(120).capitalInflow());
            assertMoney(1_000, result.decumulationProjection().get(120).terminalCapitalInflow());
        }
    }

    @Test
    void capitalReceivedTodayIsVisibleAtMonthZeroAndCanGrowUntilFire() {
        FutureLumpSum resource = lump(
                "Disponibile oggi", 10_000, AmountBasis.NOMINAL, 40, true, 0.05);
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 40, 50, 1, 0, 0, 0, 0, 0, 0, List.of(resource)));
        double expectedAtFire = 10_000 * Math.pow(1.05, 10);

        assertMoney(10_000,
                result.accumulationProjection().get(0).availableFutureLumpSumsBalance());
        assertMoney(expectedAtFire,
                result.accumulationProjection().get(120).availableFutureLumpSumsBalance());
        assertMoney(expectedAtFire, result.futureLumpSums().get(0).balanceAtFire());
    }

    @ParameterizedTest(name = "post-receipt return {0}")
    @ValueSource(doubles = {-0.20, 0.0, 0.05})
    void investedCapitalBeforeFireUsesItsOwnMonthlyEquivalentReturn(double annualReturn) {
        FutureLumpSum resource = lump(
                "Investito prima del FIRE", 10_000, AmountBasis.NOMINAL, 45, true, annualReturn);
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 40, 50, 1, 0, 0, 0, 0, 0, 0, List.of(resource)));
        double expectedAtFire = 10_000 * Math.pow(1.0 + annualReturn, 5);

        assertMoney(expectedAtFire, result.futureLumpSums().get(0).balanceAtFire());
        assertMoney(expectedAtFire, result.projectedAvailableFutureLumpSumsFinalBalance());
    }

    @ParameterizedTest(name = "ignored configured return {0}")
    @ValueSource(doubles = {-0.80, 0.0, 0.80})
    void nonInvestedCapitalBeforeFireIgnoresConfiguredReturn(double configuredReturn) {
        FutureLumpSum resource = lump(
                "Tenuto liquido", 10_000, AmountBasis.NOMINAL, 45, false, configuredReturn);
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 40, 50, 1, 0, 0, 0, 0, 0, 0, List.of(resource)));

        assertMoney(10_000, result.futureLumpSums().get(0).balanceAtFire());
        assertMoney(10_000, result.projectedAvailableFutureLumpSumsFinalBalance());
    }

    @Test
    void capitalReceivedDuringFireIgnoresItsOwnReturnAndUsesFireReturn() {
        FutureLumpSum investedFlag = lump(
                "Flag attivo", 10_000, AmountBasis.NOMINAL, 55, true, 0.80);
        FutureLumpSum liquidFlag = lump(
                "Flag inattivo", 10_000, AmountBasis.NOMINAL, 55, false, -0.80);
        FireCalculationResult investedResult = calculator.calculate(input(
                FireMethod.FINITE, 40, 50, 10, 0, 0, 0.12, 0, 0, 0,
                List.of(investedFlag)));
        FireCalculationResult liquidResult = calculator.calculate(input(
                FireMethod.FINITE, 40, 50, 10, 0, 0, 0.12, 0, 0, 0,
                List.of(liquidFlag)));
        double expectedFinal = 10_000 * Math.pow(1.12, 5);

        assertMoney(expectedFinal, investedResult.personalDecumulationFinalBalance());
        assertMoney(expectedFinal, liquidResult.personalDecumulationFinalBalance());
        assertMoney(investedResult.personalDecumulationFinalBalance(),
                liquidResult.personalDecumulationFinalBalance());
    }

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void multipleCapitalInflowsAreAdditiveAndIndependentFromListOrder(FireMethod method) {
        FutureLumpSum now = lump("Oggi", 1_000, AmountBasis.NOMINAL, 40, false, 0);
        FutureLumpSum atFire = lump("Al FIRE", 2_000, AmountBasis.NOMINAL, 50, false, 0);
        FutureLumpSum duringFire = lump("Durante", 3_000, AmountBasis.NOMINAL, 55, false, 0);
        FutureLumpSum terminal = lump("Finale", 4_000, AmountBasis.NOMINAL, 60, false, 0);
        List<AdditionalResource> orderedResources = List.of(now, atFire, duringFire, terminal);
        List<AdditionalResource> reversedResources = List.of(terminal, duringFire, atFire, now);

        FireCalculationResult ordered = calculator.calculate(input(
                method, 40, 50, 10, 100, 0, 0, 0, 0, 0, orderedResources));
        FireCalculationResult reversed = calculator.calculate(input(
                method, 40, 50, 10, 100, 0, 0, 0, 0, 0, reversedResources));

        assertMoney(3_000, ordered.projectedAvailableFutureLumpSumsFinalBalance());
        assertMoney(7_000, ordered.totalCapitalInflows());
        assertMoney(4_000, ordered.terminalCapitalInflow());
        assertMoney(ordered.selectedTarget(), reversed.selectedTarget());
        assertMoney(ordered.capitalGap(), reversed.capitalGap());
        assertMoney(ordered.initialMonthlyContribution(), reversed.initialMonthlyContribution());
        assertMoney(ordered.projectedAccumulationFinalBalance(),
                reversed.projectedAccumulationFinalBalance());
        assertMoney(ordered.personalDecumulationFinalBalance(),
                reversed.personalDecumulationFinalBalance());
        assertMoney(3_000, lumpNamed(ordered, "Durante").nominalAmountAtReceipt());
    }

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void splittingAContemporaneousCapitalPreservesEveryAggregate(FireMethod method) {
        FutureLumpSum combined = lump(
                "Unico", 50_000, AmountBasis.NOMINAL, 55, false, 0);
        FutureLumpSum firstPart = lump(
                "Parte A", 20_000, AmountBasis.NOMINAL, 55, false, 0);
        FutureLumpSum secondPart = lump(
                "Parte B", 30_000, AmountBasis.NOMINAL, 55, false, 0);
        FireCalculationResult oneResource = calculator.calculate(input(
                method, 40, 50, 10, 1_000, 0, 0, 0, 0, 0, List.of(combined)));
        FireCalculationResult twoResources = calculator.calculate(input(
                method, 40, 50, 10, 1_000, 0, 0, 0, 0, 0,
                List.of(firstPart, secondPart)));

        assertMoney(oneResource.selectedTarget(), twoResources.selectedTarget());
        assertMoney(oneResource.initialMonthlyContribution(), twoResources.initialMonthlyContribution());
        assertMoney(oneResource.totalCapitalInflows(), twoResources.totalCapitalInflows());
        assertMoney(oneResource.decumulationProjection().get(61).capitalInflow(),
                twoResources.decumulationProjection().get(61).capitalInflow());
        assertMoney(oneResource.personalDecumulationFinalBalance(),
                twoResources.personalDecumulationFinalBalance());
    }

    @ParameterizedTest(name = "terminal inflow {0}")
    @MethodSource("terminalCapitalScenarios")
    void terminalInflowSatisfiesButCannotRetroactivelyExceedDesiredCapital(
            double terminalInflow,
            double expectedTarget,
            double expectedFinalBalance
    ) {
        FutureLumpSum resource = lump(
                "Capitale terminale", terminalInflow, AmountBasis.NOMINAL, 60, false, 0);
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 50, 50, 10, 1_000, 0, 0,
                50_000, expectedTarget, 0, List.of(resource)));

        assertMoney(expectedTarget, result.finiteTarget());
        assertMoney(terminalInflow, result.terminalCapitalInflow());
        assertMoney(expectedFinalBalance, result.targetDecumulationFinalBalance());
        assertMoney(expectedFinalBalance, result.finiteTargetProjectedFinalBalance());
        assertMoney(expectedFinalBalance, result.personalDecumulationFinalBalance());
        assertMoney(0, result.decumulationProjection().get(120).capitalInflow());
        assertMoney(terminalInflow,
                result.decumulationProjection().get(120).terminalCapitalInflow());
    }

    @Test
    void terminalCapitalDoesNotReduceTheSwrTarget() {
        FutureLumpSum terminal = lump(
                "Solo alla fine", 300_000, AmountBasis.NOMINAL, 80, false, 0);
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.SWR, 50, 50, 30, 2_000, 0, 0, 0,
                600_000, 0, List.of(terminal)));

        assertMoney(600_000, result.safeWithdrawalRateBaseTarget());
        assertMoney(600_000, result.safeWithdrawalRateTarget());
        assertMoney(300_000, result.terminalCapitalInflow());
    }

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void zeroCapitalIsMathematicallyNeutral(FireMethod method) {
        FutureLumpSum zero = lump("Zero", 0, AmountBasis.TODAY, 55, true, 0.50);
        FireCalculationResult withoutResource = calculator.calculate(input(
                method, 40, 50, 10, 1_000, 0.02, 0.04, 0, 10_000, 0.05,
                List.of()));
        FireCalculationResult withZeroResource = calculator.calculate(input(
                method, 40, 50, 10, 1_000, 0.02, 0.04, 0, 10_000, 0.05,
                List.of(zero)));

        assertMoney(withoutResource.selectedTarget(), withZeroResource.selectedTarget());
        assertMoney(withoutResource.initialMonthlyContribution(),
                withZeroResource.initialMonthlyContribution());
        assertMoney(withoutResource.projectedAccumulationFinalBalance(),
                withZeroResource.projectedAccumulationFinalBalance());
        assertMoney(withoutResource.personalDecumulationFinalBalance(),
                withZeroResource.personalDecumulationFinalBalance());
    }

    @ParameterizedTest(name = "invalid future capital: {0}")
    @MethodSource("invalidLumpSumScenarios")
    void rejectsInvalidCapitalConfiguration(
            String label,
            FutureLumpSum resource,
            CalculationErrorCode expectedCode
    ) {
        FireCalculationException exception = assertThrows(
                FireCalculationException.class,
                () -> calculator.calculate(input(
                        FireMethod.FINITE, 40, 50, 10, 1_000, 0, 0, 0, 0, 0,
                        List.of(resource)))
        );

        assertEquals(expectedCode, exception.code(), label);
    }

    private static Stream<Arguments> receiptBoundaryScenarios() {
        return Stream.of(FireMethod.FINITE, FireMethod.SWR)
                .flatMap(method -> Stream.of(
                        Arguments.of(method, 40, 1_000, 0, 0, null),
                        Arguments.of(method, 45, 1_000, 0, 0, null),
                        Arguments.of(method, 50, 1_000, 0, 0, null),
                        Arguments.of(method, 55, 0, 1_000, 0, 61),
                        Arguments.of(method, 60, 0, 1_000, 1_000, 121)
                ));
    }

    private static Stream<Arguments> terminalCapitalScenarios() {
        return Stream.of(
                Arguments.of(25_000, 145_000, 50_000),
                Arguments.of(50_000, 120_000, 50_000),
                Arguments.of(75_000, 120_000, 75_000)
        );
    }

    private static Stream<Arguments> invalidLumpSumScenarios() {
        return Stream.of(
                Arguments.of("negative amount",
                        lump("Negativo", -1, AmountBasis.NOMINAL, 45, false, 0),
                        CalculationErrorCode.INVALID_AMOUNT),
                Arguments.of("missing basis",
                        lump("Senza base", 1_000, null, 45, false, 0),
                        CalculationErrorCode.INVALID_RESOURCE),
                Arguments.of("before current age",
                        lump("Passato", 1_000, AmountBasis.NOMINAL, 39, false, 0),
                        CalculationErrorCode.INVALID_RESOURCE_PERIOD),
                Arguments.of("after horizon",
                        lump("Troppo tardi", 1_000, AmountBasis.NOMINAL, 61, false, 0),
                        CalculationErrorCode.INVALID_RESOURCE_PERIOD),
                Arguments.of("return at minus one",
                        lump("Tasso", 1_000, AmountBasis.NOMINAL, 45, true, -1),
                        CalculationErrorCode.INVALID_RATE)
        );
    }

    private static FutureLumpSum lump(
            String name,
            double amount,
            AmountBasis basis,
            int receiptAge,
            boolean investAfterReceipt,
            double annualReturn
    ) {
        return new FutureLumpSum(
                name,
                amount,
                basis,
                receiptAge,
                investAfterReceipt,
                annualReturn
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
            double terminalCapital,
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
                terminalCapital,
                currentCapital,
                accumulationReturn,
                0,
                resources
        );
    }

    private static FutureLumpSumResult lumpNamed(
            FireCalculationResult result,
            String name
    ) {
        return result.futureLumpSums().stream()
                .filter(lump -> name.equals(lump.name()))
                .findFirst()
                .orElseThrow();
    }

    private static void assertMoney(double expected, double actual) {
        assertEquals(expected, actual, MONEY_TOLERANCE);
    }
}
