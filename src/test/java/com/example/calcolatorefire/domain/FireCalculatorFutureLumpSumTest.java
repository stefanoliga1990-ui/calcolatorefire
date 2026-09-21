package com.example.calcolatorefire.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

class FireCalculatorFutureLumpSumTest {

    private static final double MONEY_TOLERANCE = 0.01;
    private final FireCalculator calculator = new FireCalculator();

    @Test
    void lumpSumReceivedBeforeFireUsesItsOwnReturnUntilFire() {
        FutureLumpSum lumpSum = new FutureLumpSum(
                "Capitale investito", 100_000, AmountBasis.NOMINAL, 45, true, 0.05
        );
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 40, 50, 1, 0, 0, null, 0, List.of(lumpSum)
        ));

        assertMoney(127_628.1563, result.projectedAvailableFutureLumpSumsFinalBalance());
        assertMoney(127_628.1563, result.projectedAccumulationFinalBalance());
        assertMoney(0, result.initialMonthlyContribution());
        assertEquals(1, result.futureLumpSums().size());
        assertMoney(100_000, result.futureLumpSums().get(0).nominalAmountAtReceipt());
        assertMoney(127_628.1563, result.futureLumpSums().get(0).balanceAtFire());
        assertNull(result.futureLumpSums().get(0).fireReceiptMonth());
    }

    @Test
    void amountInTodayEurosIsInflatedToItsReceiptDate() {
        FutureLumpSum lumpSum = new FutureLumpSum(
                "Capitale reale", 100_000, AmountBasis.TODAY, 45, false, 0.20
        );
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 40, 50, 1, 0, 0.02, null, 0, List.of(lumpSum)
        ));

        double expectedAtReceipt = 100_000 * Math.pow(1.02, 5);
        assertMoney(expectedAtReceipt, result.futureLumpSums().get(0).nominalAmountAtReceipt());
        assertMoney(expectedAtReceipt, result.projectedAvailableFutureLumpSumsFinalBalance());
    }

    @Test
    void lumpSumAtFireIsCountedInAccumulationAndNotAgainInFirstFireMonth() {
        FutureLumpSum lumpSum = new FutureLumpSum(
                "Capitale al FIRE", 100_000, AmountBasis.NOMINAL, 50, true, 0.10
        );
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 40, 50, 1, 0, 0, null, 0, List.of(lumpSum)
        ));

        assertMoney(100_000, result.projectedAvailableFutureLumpSumsFinalBalance());
        assertMoney(0, result.totalCapitalInflows());
        assertMoney(0, result.decumulationProjection().get(1).capitalInflow());
        assertNull(result.futureLumpSums().get(0).fireReceiptMonth());
    }

    @Test
    void lumpSumAvailableAtFireReducesOnlyTheRemainingPacGap() {
        FutureLumpSum lumpSum = new FutureLumpSum(
                "Capitale al FIRE", 6_000, AmountBasis.NOMINAL, 50, false, 0
        );
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 40, 50, 1, 1_000, 0, null, 0, List.of(lumpSum)
        ));

        assertMoney(12_000, result.selectedTarget());
        assertMoney(6_000, result.projectedAvailableFutureLumpSumsFinalBalance());
        assertMoney(6_000, result.capitalGap());
        assertMoney(50, result.initialMonthlyContribution());
        assertMoney(12_000, result.projectedAccumulationFinalBalance());
    }

    @Test
    void lumpSumAtCurrentFireBoundaryCanMakeZeroMonthScenarioReachable() {
        FutureLumpSum lumpSum = new FutureLumpSum(
                "Capitale immediato", 540_000, AmountBasis.NOMINAL, 50, false, 0
        );
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 50, 50, 30, 1_500, 0, null, 0, List.of(lumpSum)
        ));

        assertEquals(0, result.accumulationMonths());
        assertMoney(540_000, result.selectedTarget());
        assertMoney(0, result.capitalGap());
        assertMoney(540_000, result.projectedAccumulationFinalBalance());
    }

    @Test
    void finiteTargetUsesALumpSumReceivedDuringFireAtTheStartOfItsMonth() {
        FutureLumpSum lumpSum = new FutureLumpSum(
                "Eredità", 50_000, AmountBasis.NOMINAL, 55, true, 0.20
        );
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 50, 50, 10, 1_000, 0, null, 70_000, List.of(lumpSum)
        ));

        assertMoney(70_000, result.finiteTarget());
        assertMoney(50_000, result.decumulationProjection().get(61).capitalInflow());
        assertMoney(0, result.decumulationProjection().get(60).capitalInflow());
        assertMoney(50_000, result.totalCapitalInflows());
        assertMoney(0, result.targetDecumulationFinalBalance());
        assertEquals(61, result.futureLumpSums().get(0).fireReceiptMonth());
    }

    @Test
    void swrUsesFutureLumpSumForBridgeAndStableReserve() {
        FutureLumpSum lumpSum = new FutureLumpSum(
                "Capitale futuro", 300_000, AmountBasis.NOMINAL, 60, false, 0
        );
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.SWR, 50, 50, 30, 2_000, 0, 0.04, 540_000, List.of(lumpSum)
        ));

        assertMoney(600_000, result.safeWithdrawalRateBaseTarget());
        assertMoney(540_000, result.safeWithdrawalRateTarget());
        assertMoney(300_000, result.decumulationProjection().get(121).capitalInflow());
        assertMoney(0, result.totalShortfall());
    }

    @Test
    void lumpSumAtEndOfHorizonContributesOnlyToTerminalCapital() {
        FutureLumpSum lumpSum = new FutureLumpSum(
                "Capitale finale", 50_000, AmountBasis.NOMINAL, 60, false, 0
        );
        FireCalculationInput input = new FireCalculationInput(
                FireMethod.FINITE,
                50,
                50,
                10,
                1_000,
                0,
                0,
                null,
                50_000,
                120_000,
                0,
                0,
                List.of(lumpSum)
        );

        FireCalculationResult result = calculator.calculate(input);

        assertMoney(120_000, result.finiteTarget());
        assertMoney(50_000, result.terminalCapitalInflow());
        assertMoney(0, result.decumulationProjection().get(120).capitalInflow());
        assertMoney(50_000, result.decumulationProjection().get(120).terminalCapitalInflow());
        assertMoney(50_000, result.targetDecumulationFinalBalance());
        assertMoney(50_000, result.finiteTargetProjectedFinalBalance());
        assertEquals(121, result.futureLumpSums().get(0).fireReceiptMonth());
    }

    private static FireCalculationInput input(
            FireMethod method,
            int currentAge,
            int fireAge,
            int fireDurationYears,
            double monthlyExpense,
            double inflation,
            Double swr,
            double currentCapital,
            List<AdditionalResource> resources
    ) {
        return new FireCalculationInput(
                method,
                currentAge,
                fireAge,
                fireDurationYears,
                monthlyExpense,
                inflation,
                0,
                swr,
                0,
                currentCapital,
                0,
                0,
                resources
        );
    }

    private static void assertMoney(double expected, double actual) {
        assertEquals(expected, actual, MONEY_TOLERANCE);
    }
}
