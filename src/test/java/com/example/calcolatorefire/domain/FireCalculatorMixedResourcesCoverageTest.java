package com.example.calcolatorefire.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FireCalculatorMixedResourcesCoverageTest {

    private static final double MONEY_TOLERANCE = 0.01;

    private final FireCalculator calculator = new FireCalculator();

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void investmentAndIncomeCombineAcrossAccumulationAndFire(FireMethod method) {
        FireCalculationResult result = calculator.calculate(oneYearInput(
                method,
                List.of(simpleInvestment(), simpleIncome())
        ));
        double expectedTarget = targetWithIncome(method);
        double expectedAvailableBeforeNewPac = 2_400;

        assertMoney(expectedTarget, result.selectedTarget());
        assertMoney(1_200, result.projectedAvailableExistingInvestmentsFinalBalance());
        assertMoney(1_200, result.projectedInvestedIncomeFinalBalance());
        assertMoney(100, result.firstMonthlyAdditionalIncome());
        assertMoney(200, result.firstMonthlyNetWithdrawal());
        assertMoney(Math.max(0, expectedTarget - expectedAvailableBeforeNewPac) / 12,
                result.initialMonthlyContribution());
        assertMoney(Math.max(expectedTarget, expectedAvailableBeforeNewPac),
                result.projectedAccumulationFinalBalance());
    }

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void investmentAndCapitalAtFireReduceOnlyTheAccumulationGap(FireMethod method) {
        FireCalculationResult result = calculator.calculate(oneYearInput(
                method,
                List.of(simpleInvestment(), simpleLumpSum())
        ));
        double expectedTarget = targetWithoutIncome(method);
        double expectedAvailableBeforeNewPac = 1_800;

        assertMoney(expectedTarget, result.selectedTarget());
        assertMoney(1_200, result.projectedAvailableExistingInvestmentsFinalBalance());
        assertMoney(600, result.projectedAvailableFutureLumpSumsFinalBalance());
        assertMoney(0, result.totalCapitalInflows());
        assertMoney(Math.max(0, expectedTarget - expectedAvailableBeforeNewPac) / 12,
                result.initialMonthlyContribution());
    }

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void incomeAndCapitalAtFireChangeTargetAndGapOnceEach(FireMethod method) {
        FireCalculationResult result = calculator.calculate(oneYearInput(
                method,
                List.of(simpleIncome(), simpleLumpSum())
        ));
        double expectedTarget = targetWithIncome(method);
        double expectedAvailableBeforeNewPac = 1_800;

        assertMoney(expectedTarget, result.selectedTarget());
        assertMoney(1_200, result.projectedInvestedIncomeFinalBalance());
        assertMoney(600, result.projectedAvailableFutureLumpSumsFinalBalance());
        assertMoney(100, result.firstMonthlyAdditionalIncome());
        assertMoney(Math.max(0, expectedTarget - expectedAvailableBeforeNewPac) / 12,
                result.initialMonthlyContribution());
    }

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void allThreeResourceTypesProduceTheManuallyExpectedScenario(FireMethod method) {
        FireCalculationResult result = calculator.calculate(oneYearInput(
                method,
                List.of(simpleInvestment(), simpleIncome(), simpleLumpSum())
        ));
        double expectedTarget = targetWithIncome(method);
        double expectedAvailableBeforeNewPac = 3_000;
        double expectedPac = Math.max(0, expectedTarget - expectedAvailableBeforeNewPac) / 12;

        assertMoney(expectedTarget, result.selectedTarget());
        assertMoney(expectedPac, result.initialMonthlyContribution());
        assertMoney(Math.max(expectedTarget, expectedAvailableBeforeNewPac),
                result.projectedAccumulationFinalBalance());
        assertEquals(1, result.existingInvestments().size());
        assertEquals(1, result.futureLumpSums().size());
        assertMoney(600, result.futureLumpSums().get(0).balanceAtFire());
        assertNull(result.futureLumpSums().get(0).fireReceiptMonth());

        double expectedFinalBalance = method == FireMethod.FINITE ? 600 : 57_600;
        assertMoney(expectedFinalBalance, result.personalDecumulationFinalBalance());
        assertMoney(0, result.totalShortfall());
    }

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void fiveOverlappingResourcesKeepTheirOwnProvenance(FireMethod method) {
        ExistingInvestment availableInvestment = new ExistingInvestment(
                "PAC disponibile", 20_000, 100, 40, 50, 0.04, 0.02, true);
        ExistingInvestment excludedInvestment = new ExistingInvestment(
                "PAC escluso", 10_000, 50, 42, 48, 0.03, 0, false);
        PeriodicIncome rent = new PeriodicIncome(
                "Affitto", 200, 0.02, 40, null, true, true);
        PeriodicIncome pension = new PeriodicIncome(
                "Pensione", 300, 0, 55, null, false, true);
        FutureLumpSum futureCapital = new FutureLumpSum(
                "Capitale futuro", 50_000, AmountBasis.TODAY, 60, false, 0);
        List<AdditionalResource> resources = List.of(
                availableInvestment,
                excludedInvestment,
                rent,
                pension,
                futureCapital
        );

        FireCalculationResult result = calculator.calculate(realisticInput(method, resources));
        ExistingInvestmentResult available = investmentNamed(result, "PAC disponibile");
        ExistingInvestmentResult excluded = investmentNamed(result, "PAC escluso");
        FutureLumpSumResult capital = lumpNamed(result, "Capitale futuro");
        double expectedFirstRent = 200 * Math.pow(1.02, 10);
        double expectedCapitalAtReceipt = 50_000 * Math.pow(1.02, 20);

        assertEquals(2, result.existingInvestments().size());
        assertEquals(1, result.futureLumpSums().size());
        assertEquals(0, available.resourceIndex());
        assertEquals(1, excluded.resourceIndex());
        assertEquals(4, capital.resourceIndex());
        assertTrue(available.availableAtFire());
        assertFalse(excluded.availableAtFire());
        assertMoney(available.finalBalance(),
                result.projectedAvailableExistingInvestmentsFinalBalance());
        assertTrue(excluded.finalBalance() > 0);
        assertMoney(available.totalNominalContributions() + excluded.totalNominalContributions(),
                result.totalNominalExistingInvestmentContributions());
        assertTrue(result.projectedInvestedIncomeFinalBalance() > 0);
        assertMoney(expectedFirstRent, result.firstMonthlyAdditionalIncome());
        assertMoney(expectedCapitalAtReceipt, capital.nominalAmountAtReceipt());
        assertMoney(expectedCapitalAtReceipt, result.totalCapitalInflows());
        assertMoney(expectedCapitalAtReceipt,
                result.decumulationProjection().get(121).capitalInflow());
        double rentAtPensionStart = 200 * Math.pow(1.02, 15);
        assertMoney(rentAtPensionStart + 300,
                result.decumulationProjection().get(61).additionalIncome());
        assertTrue(Double.isFinite(result.selectedTarget()));
        assertTrue(Double.isFinite(result.initialMonthlyContribution()));
        assertTrue(result.projectedAccumulationFinalBalance() + MONEY_TOLERANCE
                >= result.selectedTarget());
    }

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void heterogeneousResourceOrderDoesNotChangeAggregates(FireMethod method) {
        ExistingInvestment investment = simpleInvestment();
        PeriodicIncome income = simpleIncome();
        FutureLumpSum lumpSum = new FutureLumpSum(
                "Durante il FIRE", 5_000, AmountBasis.NOMINAL, 46, false, 0);
        List<AdditionalResource> orderedResources = List.of(investment, income, lumpSum);
        List<AdditionalResource> reversedResources = List.of(lumpSum, income, investment);

        FireCalculationResult ordered = calculator.calculate(input(
                method, 40, 41, 10, 300, 0, 0, 0, 0, orderedResources));
        FireCalculationResult reversed = calculator.calculate(input(
                method, 40, 41, 10, 300, 0, 0, 0, 0, reversedResources));

        assertMoney(ordered.selectedTarget(), reversed.selectedTarget());
        assertMoney(ordered.capitalGap(), reversed.capitalGap());
        assertMoney(ordered.initialMonthlyContribution(), reversed.initialMonthlyContribution());
        assertMoney(ordered.projectedAccumulationFinalBalance(),
                reversed.projectedAccumulationFinalBalance());
        assertMoney(ordered.totalCapitalInflows(), reversed.totalCapitalInflows());
        assertMoney(ordered.personalDecumulationFinalBalance(),
                reversed.personalDecumulationFinalBalance());
        assertMoney(investmentNamed(ordered, "PAC esistente").finalBalance(),
                investmentNamed(reversed, "PAC esistente").finalBalance());
        assertMoney(lumpNamed(ordered, "Durante il FIRE").nominalAmountAtReceipt(),
                lumpNamed(reversed, "Durante il FIRE").nominalAmountAtReceipt());
    }

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void mixedNeutralResourcesDoNotChangeCoreCalculation(FireMethod method) {
        ExistingInvestment excluded = new ExistingInvestment(
                "Escluso", 100_000, 0, null, null, 0.05, 0, false);
        PeriodicIncome zeroIncome = new PeriodicIncome(
                "Rendita zero", 0, 0.02, 40, null, true, true);
        FutureLumpSum zeroCapital = new FutureLumpSum(
                "Capitale zero", 0, AmountBasis.TODAY, 45, true, 0.50);
        FireCalculationResult baseline = calculator.calculate(input(
                method, 40, 50, 20, 2_000, 0.02, 0.04, 10_000, 0.05, List.of()));
        FireCalculationResult withNeutralResources = calculator.calculate(input(
                method, 40, 50, 20, 2_000, 0.02, 0.04, 10_000, 0.05,
                List.of(excluded, zeroIncome, zeroCapital)));

        assertMoney(baseline.selectedTarget(), withNeutralResources.selectedTarget());
        assertMoney(baseline.capitalGap(), withNeutralResources.capitalGap());
        assertMoney(baseline.initialMonthlyContribution(),
                withNeutralResources.initialMonthlyContribution());
        assertMoney(baseline.projectedAccumulationFinalBalance(),
                withNeutralResources.projectedAccumulationFinalBalance());
        assertMoney(baseline.personalDecumulationFinalBalance(),
                withNeutralResources.personalDecumulationFinalBalance());
        assertMoney(0, withNeutralResources.projectedAvailableExistingInvestmentsFinalBalance());
        assertTrue(withNeutralResources.existingInvestments().get(0).finalBalance() > 100_000);
    }

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void mixedResourcesCanFundAZeroMonthScenario(FireMethod method) {
        double targetAfterIncome = method == FireMethod.FINITE ? 600 : 15_000;
        double investmentAmount = targetAfterIncome * 0.4;
        double lumpAmount = targetAfterIncome * 0.6;
        PeriodicIncome income = new PeriodicIncome(
                "Rendita FIRE", 50, 0, 50, null, false, true);
        ExistingInvestment investment = new ExistingInvestment(
                "Capitale disponibile", investmentAmount, 0, null, null, 0, 0, true);
        FutureLumpSum lumpSum = new FutureLumpSum(
                "Capitale immediato", lumpAmount, AmountBasis.NOMINAL, 50, false, 0);

        FireCalculationResult result = calculator.calculate(input(
                method, 50, 50, 1, 100, 0, 0, 0, 0,
                List.of(income, investment, lumpSum)));

        assertEquals(0, result.accumulationMonths());
        assertMoney(targetAfterIncome, result.selectedTarget());
        assertMoney(0, result.capitalGap());
        assertMoney(0, result.initialMonthlyContribution());
        assertMoney(targetAfterIncome, result.projectedAccumulationFinalBalance());
        assertMoney(0, result.totalShortfall());
    }

    @ParameterizedTest
    @EnumSource(FireMethod.class)
    void mixedResourcesStillRejectAnUnderfundedZeroMonthScenario(FireMethod method) {
        double targetAfterIncome = method == FireMethod.FINITE ? 600 : 15_000;
        double available = targetAfterIncome - 1;
        PeriodicIncome income = new PeriodicIncome(
                "Rendita FIRE", 50, 0, 50, null, false, true);
        ExistingInvestment investment = new ExistingInvestment(
                "Capitale disponibile", available / 2, 0, null, null, 0, 0, true);
        FutureLumpSum lumpSum = new FutureLumpSum(
                "Capitale immediato", available / 2, AmountBasis.NOMINAL, 50, false, 0);

        FireCalculationException exception = assertThrows(
                FireCalculationException.class,
                () -> calculator.calculate(input(
                        method, 50, 50, 1, 100, 0, 0, 0, 0,
                        List.of(income, investment, lumpSum)))
        );

        assertEquals(CalculationErrorCode.UNREACHABLE_WITH_ZERO_MONTHS, exception.code());
    }

    @Test
    void overlappingMixedResourcesReconcileMonthlyTotalsWithoutDoubleCounting() {
        ExistingInvestment investment = new ExistingInvestment(
                "PAC", 1_000, 100, 40, 42, 0, 0, true);
        PeriodicIncome income = new PeriodicIncome(
                "Entrata", 200, 0, 40, 42, true, false);
        FutureLumpSum lumpSum = new FutureLumpSum(
                "Capitale", 3_000, AmountBasis.NOMINAL, 41, false, 0);
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 40, 42, 1, 1_000, 0, 0, 0, 0,
                List.of(investment, income, lumpSum)));

        assertMoney(3_400, result.projectedAvailableExistingInvestmentsFinalBalance());
        assertMoney(4_800, result.projectedInvestedIncomeFinalBalance());
        assertMoney(3_000, result.projectedAvailableFutureLumpSumsFinalBalance());
        assertMoney(12_000,
                result.accumulationProjection().get(24).totalAvailableBalance());
        assertMoney(
                result.accumulationProjection().get(24).closingBalance()
                        + result.accumulationProjection().get(24).availableExistingInvestmentsBalance()
                        + result.accumulationProjection().get(24).availableFutureLumpSumsBalance(),
                result.accumulationProjection().get(24).totalAvailableBalance()
        );
        assertMoney(12_000, result.selectedTarget());
        assertMoney(800, result.capitalGap());
        assertMoney(800.0 / 24.0, result.initialMonthlyContribution());
        assertMoney(12_000, result.projectedAccumulationFinalBalance());
    }

    private static ExistingInvestment simpleInvestment() {
        return new ExistingInvestment(
                "PAC esistente", 600, 50, 40, 41, 0, 0, true);
    }

    private static PeriodicIncome simpleIncome() {
        return new PeriodicIncome(
                "Affitto", 100, 0, 40, null, true, true);
    }

    private static FutureLumpSum simpleLumpSum() {
        return new FutureLumpSum(
                "Capitale al FIRE", 600, AmountBasis.NOMINAL, 41, false, 0);
    }

    private static double targetWithoutIncome(FireMethod method) {
        return method == FireMethod.FINITE ? 3_600 : 90_000;
    }

    private static double targetWithIncome(FireMethod method) {
        return method == FireMethod.FINITE ? 2_400 : 60_000;
    }

    private static FireCalculationInput oneYearInput(
            FireMethod method,
            List<AdditionalResource> resources
    ) {
        return input(method, 40, 41, 1, 300, 0, 0, 0, 0, resources);
    }

    private static FireCalculationInput realisticInput(
            FireMethod method,
            List<AdditionalResource> resources
    ) {
        return input(method, 40, 50, 20, 2_000, 0.02, 0.04, 0, 0.05, resources);
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

    private static ExistingInvestmentResult investmentNamed(
            FireCalculationResult result,
            String name
    ) {
        return result.existingInvestments().stream()
                .filter(investment -> name.equals(investment.name()))
                .findFirst()
                .orElseThrow();
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
