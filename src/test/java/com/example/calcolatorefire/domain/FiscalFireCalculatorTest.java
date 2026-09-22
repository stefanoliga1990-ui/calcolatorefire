package com.example.calcolatorefire.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FiscalFireCalculatorTest {

    private static final double MONEY_TOLERANCE = 0.01;

    private final FireCalculator legacyCalculator = new FireCalculator();
    private final FiscalFireCalculator calculator = new FiscalFireCalculator();

    @ParameterizedTest(name = "zero-tax compatibility - {0}")
    @MethodSource("legacyCompatibilityInputs")
    void disabledFiscalityMatchesCurrentCalculator(String label, FireCalculationInput input) {
        FireCalculationResult legacy = legacyCalculator.calculate(input);

        FiscalFireCalculationResult fiscal = calculator.calculate(
                new FiscalFireCalculationInput(input, FiscalSettings.disabled(), null)
        );

        assertEquals(legacy.selectedTarget(), fiscal.selectedTarget(), MONEY_TOLERANCE, label);
        assertEquals(legacy.initialMonthlyContribution(), fiscal.initialMonthlyContribution(),
                MONEY_TOLERANCE, label);
        assertEquals(legacy.projectedAccumulationFinalBalance(),
                fiscal.accumulation().availableAtFireState().balance(), MONEY_TOLERANCE, label);
        assertEquals(legacy.targetDecumulationFinalBalance(),
                fiscal.targetDecumulation().finalState().balance(), MONEY_TOLERANCE, label);
        assertEquals(legacy.personalDecumulationFinalBalance(),
                fiscal.personalDecumulation().finalState().balance(), MONEY_TOLERANCE, label);
        assertEquals(0.0, fiscal.personalDecumulation().totalCapitalGainsTax(), MONEY_TOLERANCE, label);
        assertEquals(0.0, fiscal.personalDecumulation().totalStampDuty(), MONEY_TOLERANCE, label);
    }

    @Test
    void finiteTargetGrossesUpWithdrawalsForLatentGains() {
        FireCalculationInput input = simpleInput(FireMethod.FINITE, 40, 40, 1, 1_000.0, 15_000.0, null);

        FiscalFireCalculationResult result = calculator.calculate(
                new FiscalFireCalculationInput(input, new FiscalSettings(0.26, 0.0), 10_500.0)
        );

        double expectedGrossWithdrawal = 1_000.0 / (1.0 - 0.26 * 0.30);
        assertEquals(expectedGrossWithdrawal * 12.0, result.selectedTarget(), MONEY_TOLERANCE);
        assertEquals(expectedGrossWithdrawal,
                result.targetDecumulation().points().get(1).grossSale(), MONEY_TOLERANCE);
        assertEquals(1_000.0,
                result.targetDecumulation().points().get(1).netProceeds(), MONEY_TOLERANCE);
        assertTrue(result.targetDecumulation().totalCapitalGainsTax() > 0.0);
        assertEquals(0.0, result.targetDecumulation().totalShortfall(), MONEY_TOLERANCE);
        assertEquals(0.0, result.targetDecumulation().finalState().balance(), MONEY_TOLERANCE);
    }

    @Test
    void swrUsesTheFirstGrossSaleRatherThanTheNetExpense() {
        FireCalculationInput input = simpleInput(FireMethod.SWR, 40, 40, 10, 1_000.0, 400_000.0, 0.04);

        FiscalFireCalculationResult result = calculator.calculate(
                new FiscalFireCalculationInput(input, new FiscalSettings(0.26, 0.0), 280_000.0)
        );

        double expectedGrossWithdrawal = 1_000.0 / (1.0 - 0.26 * 0.30);
        double expectedTarget = expectedGrossWithdrawal * 12.0 / 0.04;
        assertEquals(expectedTarget, result.safeWithdrawalRateBaseTarget(), MONEY_TOLERANCE);
        assertEquals(expectedTarget, result.safeWithdrawalRateTarget(), MONEY_TOLERANCE);
        assertEquals(expectedTarget, result.selectedTarget(), MONEY_TOLERANCE);
        assertEquals(expectedGrossWithdrawal,
                result.targetDecumulation().points().get(1).grossSale(), MONEY_TOLERANCE);
    }

    @Test
    void stampDutyIsIncludedInBothTargetAndPacSolution() {
        FireCalculationInput input = new FireCalculationInput(
                FireMethod.FINITE,
                40,
                41,
                1,
                0.0,
                0.0,
                0.0,
                null,
                100_000.0,
                100_000.0,
                0.0,
                0.0
        );

        FiscalFireCalculationResult result = calculator.calculate(
                new FiscalFireCalculationInput(input, new FiscalSettings(0.26, 0.002), null)
        );

        double expectedTarget = 100_000.0 / 0.998;
        assertEquals(expectedTarget, result.selectedTarget(), MONEY_TOLERANCE);
        assertTrue(result.initialMonthlyContribution() > 0.0);
        assertTrue(result.accumulation().availableAtFireStampDuty() > 0.0);
        assertTrue(result.targetDecumulation().totalStampDuty() > 0.0);
        assertEquals(100_000.0, result.targetDecumulation().finalState().balance(), MONEY_TOLERANCE);
    }

    @Test
    void existingInvestmentTaxBasisOverrideFlowsIntoFirePortfolio() {
        ExistingInvestment investment = new ExistingInvestment(
                "ETF",
                100_000.0,
                0.0,
                null,
                null,
                0.0,
                0.0,
                true
        );
        FireCalculationInput input = simpleInput(
                FireMethod.FINITE, 40, 40, 1, 1_000.0, 0.0, null, List.of(investment)
        );

        FiscalFireCalculationResult automaticBasis = calculator.calculate(
                new FiscalFireCalculationInput(input, new FiscalSettings(0.26, 0.0), null)
        );
        FiscalFireCalculationResult manualBasis = calculator.calculate(
                new FiscalFireCalculationInput(input, new FiscalSettings(0.26, 0.0), null,
                        Map.of(0, 70_000.0))
        );

        assertEquals(100_000.0, manualBasis.accumulation().availableAtFireState().balance(),
                MONEY_TOLERANCE);
        assertEquals(70_000.0, manualBasis.accumulation().availableAtFireState().taxBasis(),
                MONEY_TOLERANCE);
        assertTrue(manualBasis.selectedTarget() > automaticBasis.selectedTarget());
        assertTrue(manualBasis.personalDecumulation().totalCapitalGainsTax() > 0.0);
        assertEquals(0.0, automaticBasis.personalDecumulation().totalCapitalGainsTax(),
                MONEY_TOLERANCE);
    }

    @Test
    void investedIncomeAndFutureCapitalEnterWithFullTaxBasis() {
        PeriodicIncome income = new PeriodicIncome(
                "Affitto", 500.0, 0.0, 40, 41, true, false
        );
        FutureLumpSum lumpSum = new FutureLumpSum(
                "Liquidità", 12_000.0, AmountBasis.NOMINAL, 41, false, 0.0
        );
        FireCalculationInput input = simpleInput(
                FireMethod.FINITE, 40, 41, 1, 0.0, 0.0, null, List.of(income, lumpSum)
        );

        FiscalFireCalculationResult result = calculator.calculate(
                new FiscalFireCalculationInput(input, FiscalSettings.disabled(), null)
        );

        assertEquals(18_000.0, result.accumulation().availableAtFireState().balance(), MONEY_TOLERANCE);
        assertEquals(18_000.0, result.accumulation().availableAtFireState().taxBasis(), MONEY_TOLERANCE);
        assertEquals(0.0, result.initialMonthlyContribution(), MONEY_TOLERANCE);
    }

    @Test
    void uninvestedFutureCashDoesNotPayStampDutyBeforeFire() {
        FutureLumpSum cash = new FutureLumpSum(
                "Liquidità", 100_000.0, AmountBasis.NOMINAL, 40, false, 0.0
        );
        FutureLumpSum invested = new FutureLumpSum(
                "ETF", 100_000.0, AmountBasis.NOMINAL, 40, true, 0.0
        );
        FireCalculationInput cashInput = simpleInput(
                FireMethod.FINITE, 40, 41, 1, 0.0, 0.0, null, List.of(cash)
        );
        FireCalculationInput investedInput = simpleInput(
                FireMethod.FINITE, 40, 41, 1, 0.0, 0.0, null, List.of(invested)
        );

        FiscalFireCalculationResult cashResult = calculator.calculate(
                new FiscalFireCalculationInput(cashInput, FiscalSettings.defaults(), null)
        );
        FiscalFireCalculationResult investedResult = calculator.calculate(
                new FiscalFireCalculationInput(investedInput, FiscalSettings.defaults(), null)
        );

        assertEquals(100_000.0, cashResult.accumulation().availableAtFireState().balance(),
                MONEY_TOLERANCE);
        assertEquals(0.0, cashResult.accumulation().availableAtFireStampDuty(), MONEY_TOLERANCE);
        assertEquals(99_800.0, investedResult.accumulation().availableAtFireState().balance(),
                MONEY_TOLERANCE);
        assertEquals(200.0, investedResult.accumulation().availableAtFireStampDuty(), MONEY_TOLERANCE);
    }

    @Test
    void fiscalSwrBridgeUsesTaxedSalesBeforeTheStableIncomeRegime() {
        PeriodicIncome pension = new PeriodicIncome(
                "Pensione", 500.0, 0.0, 45, null, false, true
        );
        FireCalculationInput input = simpleInput(
                FireMethod.SWR, 40, 40, 10, 1_000.0, 400_000.0, 0.04, List.of(pension)
        );

        FiscalFireCalculationResult result = calculator.calculate(
                new FiscalFireCalculationInput(input, new FiscalSettings(0.26, 0.002), 280_000.0)
        );

        assertTrue(result.safeWithdrawalRateTarget() < result.safeWithdrawalRateBaseTarget());
        assertTrue(result.targetDecumulation().points().get(1).capitalGainsTax() > 0.0);
        assertEquals(0.0, result.targetDecumulation().totalShortfall(), MONEY_TOLERANCE);
        assertEquals(500.0, result.targetDecumulation().points().get(61).requestedNetAmount(),
                MONEY_TOLERANCE);
    }

    @Test
    void latentLossDoesNotCreateCapitalGainsTaxInTheFullProjection() {
        FireCalculationInput input = simpleInput(FireMethod.FINITE, 40, 40, 1, 1_000.0, 20_000.0, null);

        FiscalFireCalculationResult result = calculator.calculate(
                new FiscalFireCalculationInput(input, new FiscalSettings(0.26, 0.0), 25_000.0)
        );

        assertEquals(12_000.0, result.selectedTarget(), MONEY_TOLERANCE);
        assertEquals(0.0, result.personalDecumulation().totalCapitalGainsTax(), MONEY_TOLERANCE);
    }

    @Test
    void finiteTargetAccountsForTerminalCapitalInflowAtTheBoundary() {
        FutureLumpSum terminal = new FutureLumpSum(
                "Eredità", 20_000.0, AmountBasis.NOMINAL, 41, false, 0.0
        );
        FireCalculationInput input = new FireCalculationInput(
                FireMethod.FINITE,
                40,
                40,
                1,
                0.0,
                0.0,
                0.0,
                null,
                50_000.0,
                40_000.0,
                0.0,
                0.0,
                List.of(terminal)
        );

        FiscalFireCalculationResult result = calculator.calculate(
                new FiscalFireCalculationInput(input, FiscalSettings.defaults(), null)
        );

        assertTrue(result.selectedTarget() < 31_000.0);
        assertEquals(50_000.0, result.targetDecumulation().finalState().balance(), MONEY_TOLERANCE);
        FiscalDecumulationProjectionPoint last = result.targetDecumulation().points().get(12);
        assertEquals(20_000.0, last.terminalCapitalInflow(), MONEY_TOLERANCE);
        assertEquals(20_000.0, last.closingState().taxBasis() - last.openingState().taxBasis(),
                MONEY_TOLERANCE);
    }

    @Test
    void zeroAccumulationMonthsRejectInsufficientFiscalCapital() {
        FireCalculationInput input = simpleInput(FireMethod.FINITE, 40, 40, 1, 1_000.0, 12_000.0, null);

        FireCalculationException exception = assertThrows(
                FireCalculationException.class,
                () -> calculator.calculate(
                        new FiscalFireCalculationInput(input, new FiscalSettings(0.26, 0.002), 8_400.0)
                )
        );

        assertEquals(CalculationErrorCode.UNREACHABLE_WITH_ZERO_MONTHS, exception.code());
    }

    @Test
    void rejectsTaxBasisMappedToANonInvestmentResource() {
        PeriodicIncome income = new PeriodicIncome(
                "Rendita", 100.0, 0.0, 40, 41, true, false
        );
        FireCalculationInput input = simpleInput(
                FireMethod.FINITE, 40, 41, 1, 0.0, 0.0, null, List.of(income)
        );

        FireCalculationException exception = assertThrows(
                FireCalculationException.class,
                () -> calculator.calculate(new FiscalFireCalculationInput(
                        input, FiscalSettings.defaults(), null, Map.of(0, 10.0)
                ))
        );

        assertEquals(CalculationErrorCode.INVALID_RESOURCE, exception.code());
    }

    @Test
    void fiscalProjectionExposesMonthlyReconciliation() {
        FireCalculationInput input = simpleInput(FireMethod.FINITE, 40, 41, 2, 1_000.0, 10_000.0, null);

        FiscalFireCalculationResult result = calculator.calculate(
                new FiscalFireCalculationInput(input, FiscalSettings.defaults(), 7_000.0)
        );

        assertEquals(13, result.accumulation().projections().get(0).points().size());
        assertEquals(25, result.personalDecumulation().points().size());
        assertNotNull(result.targetDecumulation().points().get(1));
        for (FiscalDecumulationProjectionPoint point : result.personalDecumulation().points()) {
            assertTrue(Double.isFinite(point.closingState().balance()));
            assertTrue(Double.isFinite(point.closingState().taxBasis()));
        }
    }

    @Test
    void remainsFiniteAtTheMaximumSupportedHorizon() {
        FireCalculationInput input = new FireCalculationInput(
                FireMethod.FINITE,
                0,
                50,
                80,
                1_000.0,
                0.02,
                0.04,
                null,
                0.0,
                25_000.0,
                0.06,
                0.01
        );

        FiscalFireCalculationResult result = calculator.calculate(
                new FiscalFireCalculationInput(input, FiscalSettings.defaults(), 20_000.0)
        );

        assertTrue(Double.isFinite(result.selectedTarget()));
        assertTrue(Double.isFinite(result.initialMonthlyContribution()));
        assertEquals(601, result.accumulation().projections().get(0).points().size());
        assertEquals(961, result.personalDecumulation().points().size());
    }

    private static Stream<Arguments> legacyCompatibilityInputs() {
        ExistingInvestment investment = new ExistingInvestment(
                "ETF", 20_000.0, 150.0, 36, 40, 0.05, 0.01, true
        );
        PeriodicIncome income = new PeriodicIncome(
                "Affitto", 300.0, 0.02, 36, null, true, true
        );
        FutureLumpSum lumpSum = new FutureLumpSum(
                "Liquidità", 15_000.0, AmountBasis.NOMINAL, 39, true, 0.03
        );
        return Stream.of(
                Arguments.of("FINITE base", standardInput(FireMethod.FINITE, List.of())),
                Arguments.of("SWR base", standardInput(FireMethod.SWR, List.of())),
                Arguments.of("FINITE con risorse", standardInput(
                        FireMethod.FINITE, List.of(investment, income, lumpSum))),
                Arguments.of("SWR con risorse", standardInput(
                        FireMethod.SWR, List.of(investment, income, lumpSum)))
        );
    }

    private static FireCalculationInput standardInput(FireMethod method, List<AdditionalResource> resources) {
        return new FireCalculationInput(
                method,
                36,
                40,
                20,
                1_600.0,
                0.02,
                0.05,
                method == FireMethod.SWR ? 0.04 : null,
                25_000.0,
                10_000.0,
                0.07,
                0.01,
                resources
        );
    }

    private static FireCalculationInput simpleInput(
            FireMethod method,
            int currentAge,
            int fireAge,
            int duration,
            double expense,
            double capital,
            Double swr
    ) {
        return simpleInput(method, currentAge, fireAge, duration, expense, capital, swr, List.of());
    }

    private static FireCalculationInput simpleInput(
            FireMethod method,
            int currentAge,
            int fireAge,
            int duration,
            double expense,
            double capital,
            Double swr,
            List<AdditionalResource> resources
    ) {
        return new FireCalculationInput(
                method,
                currentAge,
                fireAge,
                duration,
                expense,
                0.0,
                0.0,
                swr,
                0.0,
                capital,
                0.0,
                0.0,
                resources
        );
    }
}
