package com.example.calcolatorefire.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

class FireCalculatorAdditionalResourcesTest {

    private static final double MONEY_TOLERANCE = 0.01;
    private final FireCalculator calculator = new FireCalculator();

    @Test
    void finiteTargetUsesPermanentIncomeToReduceEveryWithdrawal() {
        PeriodicIncome income = new PeriodicIncome(
                "Affitto netto", 500, 0, 50, null, false, true
        );
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 50, 50, 30, 2_000, null, 540_000, List.of(income)
        ));

        assertMoney(540_000, result.finiteTarget());
        assertMoney(2_000, result.firstMonthlyWithdrawal());
        assertMoney(500, result.firstMonthlyAdditionalIncome());
        assertMoney(1_500, result.firstMonthlyNetWithdrawal());
        assertMoney(0, result.personalDecumulationFinalBalance());
        assertMoney(2_000, result.decumulationProjection().get(1).grossExpense());
        assertMoney(500, result.decumulationProjection().get(1).additionalIncome());
        assertMoney(1_500, result.decumulationProjection().get(1).scheduledWithdrawal());
    }

    @Test
    void swrBuildsABridgeToFuturePermanentIncome() {
        PeriodicIncome pension = new PeriodicIncome(
                "Pensione", 1_000, 0, 60, null, false, true
        );
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.SWR, 50, 50, 30, 2_000, 0.04, 540_000, List.of(pension)
        ));

        assertMoney(600_000, result.safeWithdrawalRateBaseTarget());
        assertMoney(540_000, result.safeWithdrawalRateTarget());
        assertMoney(2_000, result.firstMonthlyNetWithdrawal());
        assertMoney(1_000, result.decumulationProjection().get(121).additionalIncome());
        assertMoney(1_000, result.decumulationProjection().get(121).scheduledWithdrawal());
    }

    @Test
    void temporaryIncomeCannotIncreaseTheSwrTarget() {
        PeriodicIncome temporaryIncome = new PeriodicIncome(
                "Rendita temporanea", 500, 0, 50, 60, false, true
        );
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.SWR, 50, 50, 30, 2_000, 0.04, 600_000, List.of(temporaryIncome)
        ));

        assertMoney(600_000, result.safeWithdrawalRateBaseTarget());
        assertMoney(600_000, result.safeWithdrawalRateTarget());
        assertMoney(1_500, result.decumulationProjection().get(1).scheduledWithdrawal());
        assertMoney(2_000, result.decumulationProjection().get(121).scheduledWithdrawal());
        assertMoney(60_000, result.totalShortfall());
    }

    @Test
    void existingInvestmentUsesItsOwnReturnAndReducesThePacGap() {
        ExistingInvestment investment = new ExistingInvestment(
                "Portafoglio obbligazionario", 100_000, 0, null, null, 0.05, 0, true
        );
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 40, 50, 1, 0, null, 0, List.of(investment)
        ));

        assertMoney(162_889.4627, result.projectedAvailableExistingInvestmentsFinalBalance());
        assertMoney(162_889.4627, result.projectedAccumulationFinalBalance());
        assertMoney(0, result.initialMonthlyContribution());
        assertEquals(1, result.existingInvestments().size());
        assertMoney(162_889.4627, result.existingInvestments().get(0).finalBalance());
        assertEquals(121, result.existingInvestments().get(0).projection().size());
    }

    @Test
    void existingPacAndInvestedIncomeKeepTheirEndOfMonthTiming() {
        ExistingInvestment existingPac = new ExistingInvestment(
                "PAC esistente", 0, 100, 40, 41, 0, 0, true
        );
        PeriodicIncome investedIncome = new PeriodicIncome(
                "Entrata investita", 100, 0, 40, 41, true, false
        );
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 40, 41, 1, 200, null, 0, List.of(existingPac, investedIncome)
        ));

        assertMoney(1_200, result.totalNominalExistingInvestmentContributions());
        assertMoney(1_200, result.totalNominalAdditionalIncomeInvested());
        assertMoney(1_200, result.projectedInvestedIncomeFinalBalance());
        assertMoney(1_200, result.projectedAvailableExistingInvestmentsFinalBalance());
        assertMoney(0, result.initialMonthlyContribution());
        assertMoney(2_400, result.projectedAccumulationFinalBalance());
        assertMoney(100, result.accumulationProjection().get(1).additionalIncomeContribution());
        assertMoney(100, result.existingInvestments().get(0).projection().get(1).contribution());
    }

    @Test
    void resourceAvailableAtFireCanMakeAZeroMonthScenarioReachable() {
        ExistingInvestment investment = new ExistingInvestment(
                "Capitale disponibile", 540_000, 0, null, null, 0, 0, true
        );
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 50, 50, 30, 1_500, null, 0, List.of(investment)
        ));

        assertEquals(0, result.accumulationMonths());
        assertMoney(540_000, result.selectedTarget());
        assertMoney(0, result.capitalGap());
        assertMoney(0, result.initialMonthlyContribution());
        assertMoney(540_000, result.projectedAccumulationFinalBalance());
        assertNull(result.depletionMonth());
    }

    @Test
    void unavailableInvestmentIsProjectedButDoesNotReduceThePacGap() {
        ExistingInvestment investment = new ExistingInvestment(
                "Patrimonio non disponibile", 100_000, 0, null, null, 0, 0, false
        );
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 40, 50, 1, 100, null, 0, List.of(investment)
        ));

        assertMoney(0, result.projectedAvailableExistingInvestmentsFinalBalance());
        assertMoney(1_200, result.capitalGap());
        assertMoney(1_200, result.projectedAccumulationFinalBalance());
        assertMoney(100_000, result.existingInvestments().get(0).finalBalance());
    }

    @Test
    void incomeEndingAtFireDoesNotReduceTheFirstFireWithdrawal() {
        PeriodicIncome income = new PeriodicIncome(
                "Entrata fino al FIRE", 100, 0, 40, 50, true, true
        );
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 40, 50, 1, 100, null, 0, List.of(income)
        ));

        assertMoney(0, result.firstMonthlyAdditionalIncome());
        assertMoney(100, result.firstMonthlyNetWithdrawal());
        assertMoney(12_000, result.totalNominalAdditionalIncomeInvested());
    }

    @Test
    void incomeAboveTheExpenseDoesNotCreateANegativeWithdrawal() {
        PeriodicIncome income = new PeriodicIncome(
                "Rendita eccedente", 2_500, 0, 50, null, false, true
        );
        FireCalculationResult result = calculator.calculate(input(
                FireMethod.FINITE, 50, 50, 1, 2_000, null, 0, List.of(income)
        ));

        assertMoney(0, result.selectedTarget());
        assertMoney(0, result.firstMonthlyNetWithdrawal());
        assertMoney(0, result.decumulationProjection().get(1).scheduledWithdrawal());
        assertMoney(0, result.personalDecumulationFinalBalance());
    }

    @Test
    void finiteBackwardRecurrencePreservesTheNominalTerminalCapital() {
        PeriodicIncome pension = new PeriodicIncome(
                "Pensione indicizzata", 500, 0.02, 67, null, false, true
        );
        FireCalculationInput input = new FireCalculationInput(
                FireMethod.FINITE,
                36,
                50,
                35,
                1_600,
                0.02,
                0.05,
                null,
                50_000,
                1_000_000,
                0.05,
                0,
                List.of(pension)
        );

        FireCalculationResult result = calculator.calculate(input);

        assertMoney(result.terminalCapitalNominalAtEnd(), result.finiteTargetProjectedFinalBalance());
        assertMoney(result.terminalCapitalNominalAtEnd(), result.targetDecumulationFinalBalance());
        assertMoney(0, result.totalShortfall());
    }

    private static FireCalculationInput input(
            FireMethod method,
            int currentAge,
            int fireAge,
            int fireDurationYears,
            double monthlyExpense,
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
                0,
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
