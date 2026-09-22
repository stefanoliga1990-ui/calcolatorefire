package com.example.calcolatorefire.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FiscalCalculatorTest {

    private static final double TOLERANCE = 1e-9;

    private final FiscalCalculator calculator = new FiscalCalculator();

    @Test
    void exposesApprovedDefaultRates() {
        FiscalSettings settings = FiscalSettings.defaults();

        assertEquals(0.26, settings.capitalGainsTaxRate(), TOLERANCE);
        assertEquals(0.002, settings.annualStampDutyRate(), TOLERANCE);
    }

    @Test
    void defaultsTaxBasisToTheWholeCurrentPortfolio() {
        FiscalPortfolioState state = calculator.initialState(125_000.0, null);

        assertEquals(125_000.0, state.balance(), TOLERANCE);
        assertEquals(125_000.0, state.taxBasis(), TOLERANCE);
        assertEquals(0.0, state.taxableGainRatio(), TOLERANCE);
    }

    @Test
    void accumulationAddsContributionsToBasisButNotReturnsOrStampDuty() {
        FiscalPortfolioState opening = calculator.initialState(100_000.0, 70_000.0);
        FiscalSettings settings = FiscalSettings.defaults();

        FiscalAccumulationMonthResult result = calculator.accumulateMonth(
                opening,
                0.01,
                1_000.0,
                500.0,
                settings
        );

        double expectedGrossBalance = 102_500.0;
        double expectedStampDuty = expectedGrossBalance * calculator.monthlyStampDutyRate(settings);
        assertEquals(1_000.0, result.investmentReturn(), TOLERANCE);
        assertEquals(expectedGrossBalance, result.grossBalance(), TOLERANCE);
        assertEquals(expectedStampDuty, result.stampDuty(), TOLERANCE);
        assertEquals(expectedGrossBalance - expectedStampDuty, result.closingState().balance(), TOLERANCE);
        assertEquals(71_500.0, result.closingState().taxBasis(), TOLERANCE);
    }

    @Test
    void twelveMonthlyStampChargesMatchTheApprovedAnnualRate() {
        FiscalPortfolioState state = calculator.initialState(100_000.0, null);
        FiscalSettings settings = FiscalSettings.defaults();

        for (int month = 0; month < 12; month++) {
            state = calculator.accumulateMonth(state, 0.0, 0.0, 0.0, settings).closingState();
        }

        assertEquals(99_800.0, state.balance(), TOLERANCE);
        assertEquals(100_000.0, state.taxBasis(), TOLERANCE);
    }

    @Test
    void netCapitalInflowAddsEqualBalanceAndTaxBasisBeforeSale() {
        FiscalPortfolioState opening = calculator.initialState(100_000.0, 70_000.0);

        FiscalDecumulationMonthResult result = calculator.decumulateMonth(
                opening,
                0.0,
                10_000.0,
                0.0,
                FiscalSettings.disabled()
        );

        assertEquals(110_000.0, result.availableBalance(), TOLERANCE);
        assertEquals(80_000.0, result.availableTaxBasis(), TOLERANCE);
        assertEquals(30_000.0 / 110_000.0, result.taxableGainRatio(), TOLERANCE);
        assertEquals(80_000.0, result.closingState().taxBasis(), TOLERANCE);
    }

    @Test
    void acceptsNegativeMonthlyReturnsWithoutChangingTaxBasis() {
        FiscalPortfolioState opening = calculator.initialState(100_000.0, 70_000.0);

        FiscalAccumulationMonthResult accumulation = calculator.accumulateMonth(
                opening,
                -0.01,
                0.0,
                0.0,
                FiscalSettings.disabled()
        );
        FiscalDecumulationMonthResult decumulation = calculator.decumulateMonth(
                opening,
                0.0,
                0.0,
                -0.01,
                FiscalSettings.disabled()
        );

        assertEquals(99_000.0, accumulation.closingState().balance(), TOLERANCE);
        assertEquals(70_000.0, accumulation.closingState().taxBasis(), TOLERANCE);
        assertEquals(99_000.0, decumulation.closingState().balance(), TOLERANCE);
        assertEquals(70_000.0, decumulation.closingState().taxBasis(), TOLERANCE);
    }

    @Test
    void completeSaleZerosBalanceAndBasisAndReportsNetShortfall() {
        FiscalPortfolioState opening = calculator.initialState(500.0, 250.0);

        FiscalDecumulationMonthResult result = calculator.decumulateMonth(
                opening,
                1_000.0,
                0.0,
                0.0,
                FiscalSettings.defaults()
        );

        assertEquals(500.0, result.grossSale(), TOLERANCE);
        assertEquals(65.0, result.capitalGainsTax(), TOLERANCE);
        assertEquals(435.0, result.netProceeds(), TOLERANCE);
        assertEquals(565.0, result.shortfall(), TOLERANCE);
        assertEquals(0.0, result.closingState().balance(), TOLERANCE);
        assertEquals(0.0, result.closingState().taxBasis(), TOLERANCE);
    }

    @ParameterizedTest(name = "rejects invalid fiscal rate: {0}")
    @MethodSource("invalidFiscalRates")
    void rejectsInvalidFiscalRates(String label, CalculationErrorCode expectedCode, double gainTax, double stampDuty) {
        FireCalculationException exception = assertThrows(
                FireCalculationException.class,
                () -> new FiscalSettings(gainTax, stampDuty),
                label
        );

        assertEquals(expectedCode, exception.code());
    }

    @ParameterizedTest(name = "rejects invalid tax basis: {0}")
    @MethodSource("invalidTaxBases")
    void rejectsInvalidInitialTaxBasis(String label, double taxBasis) {
        FireCalculationException exception = assertThrows(
                FireCalculationException.class,
                () -> calculator.initialState(100_000.0, taxBasis),
                label
        );

        assertEquals(CalculationErrorCode.INVALID_TAX_BASIS, exception.code());
    }

    private static Stream<Arguments> invalidFiscalRates() {
        return Stream.of(
                Arguments.of("negative gain tax", CalculationErrorCode.INVALID_CAPITAL_GAINS_TAX_RATE, -0.01, 0.002),
                Arguments.of("100% gain tax", CalculationErrorCode.INVALID_CAPITAL_GAINS_TAX_RATE, 1.0, 0.002),
                Arguments.of("non-finite gain tax", CalculationErrorCode.INVALID_CAPITAL_GAINS_TAX_RATE,
                        Double.NaN, 0.002),
                Arguments.of("negative stamp duty", CalculationErrorCode.INVALID_STAMP_DUTY_RATE, 0.26, -0.001),
                Arguments.of("100% stamp duty", CalculationErrorCode.INVALID_STAMP_DUTY_RATE, 0.26, 1.0),
                Arguments.of("non-finite stamp duty", CalculationErrorCode.INVALID_STAMP_DUTY_RATE,
                        0.26, Double.POSITIVE_INFINITY)
        );
    }

    private static Stream<Arguments> invalidTaxBases() {
        return Stream.of(
                Arguments.of("negative", -1.0),
                Arguments.of("not finite", Double.NaN),
                Arguments.of("above input limit", CalculationLimits.MAX_AMOUNT + 1.0)
        );
    }
}
