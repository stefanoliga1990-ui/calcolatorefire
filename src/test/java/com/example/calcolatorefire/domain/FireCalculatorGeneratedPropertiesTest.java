package com.example.calcolatorefire.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class FireCalculatorGeneratedPropertiesTest {

    private static final int SAMPLES = 40;
    private static final double MONEY_TOLERANCE = 0.01;

    private final FireCalculator calculator = new FireCalculator();

    @Test
    void finiteScenariosReconcileAndRespectMonotonicity() {
        for (int seed = 0; seed < SAMPLES; seed++) {
            Random random = random(10_000 + seed);
            Scenario scenario = scenario(random, FireMethod.FINITE);
            List<AdditionalResource> resources = accumulationAndIncomeResources(scenario, random);
            FireCalculationInput baseInput = scenario.input(resources);
            FireCalculationResult base = calculator.calculate(baseInput);

            assertMoney(base.terminalCapitalNominalAtEnd(),
                    base.finiteTargetProjectedFinalBalance(), seed, "saldo finale grezzo FINITE");
            assertMoney(base.terminalCapitalNominalAtEnd(),
                    base.targetDecumulationFinalBalance(), seed, "saldo finale target FINITE");
            assertMoney(0, base.totalShortfall(), seed, "shortfall FINITE");

            FireCalculationResult higherExpense = calculator.calculate(scenario.withExpense(
                    scenario.monthlyExpense() * 1.15, resources));
            assertAtLeast(higherExpense.selectedTarget(), base.selectedTarget(), seed,
                    "più spesa non deve ridurre il target FINITE");
            assertAtLeast(higherExpense.initialMonthlyContribution(),
                    base.initialMonthlyContribution(), seed,
                    "più spesa non deve ridurre il PAC FINITE");

            ExistingInvestment extraCapital = new ExistingInvestment(
                    "Capitale aggiuntivo", 10_000 + random.nextDouble() * 40_000,
                    0, null, null, randomRate(random, -0.02, 0.08), 0, true);
            List<AdditionalResource> richerResources = new ArrayList<>(resources);
            richerResources.add(extraCapital);
            FireCalculationResult richer = calculator.calculate(scenario.input(richerResources));
            assertAtMost(richer.initialMonthlyContribution(), base.initialMonthlyContribution(), seed,
                    "più patrimonio disponibile non deve aumentare il PAC FINITE");
        }
    }

    @Test
    void swrResourcesNeverRaiseTheBaseTarget() {
        for (int seed = 0; seed < SAMPLES; seed++) {
            Random random = random(20_000 + seed);
            Scenario scenario = scenario(random, FireMethod.SWR);
            List<AdditionalResource> resources = bridgeResources(scenario, random);
            FireCalculationResult result = calculator.calculate(scenario.input(resources));

            assertFiniteNonNegative(result.safeWithdrawalRateBaseTarget(), seed, "target SWR base");
            assertFiniteNonNegative(result.safeWithdrawalRateTarget(), seed, "target SWR con risorse");
            assertAtMost(result.safeWithdrawalRateTarget(),
                    result.safeWithdrawalRateBaseTarget(), seed,
                    "le risorse non devono aumentare il target SWR");

            FireCalculationResult withoutResources = calculator.calculate(scenario.input(List.of()));
            assertMoney(withoutResources.safeWithdrawalRateBaseTarget(),
                    result.safeWithdrawalRateBaseTarget(), seed,
                    "il target SWR base non dipende dalle risorse");
            assertMoney(withoutResources.selectedTarget(),
                    withoutResources.safeWithdrawalRateBaseTarget(), seed,
                    "senza risorse il target SWR coincide con il base");
        }
    }

    @Test
    void resourceOrderDoesNotChangeAggregates() {
        for (FireMethod method : FireMethod.values()) {
            for (int seed = 0; seed < SAMPLES; seed++) {
                Random random = random(30_000L + method.ordinal() * 1_000L + seed);
                Scenario scenario = scenario(random, method);
                List<AdditionalResource> resources = mixedResources(scenario, random);
                List<AdditionalResource> shuffled = new ArrayList<>(resources);
                Collections.shuffle(shuffled, random);

                FireCalculationResult ordered = calculator.calculate(scenario.input(resources));
                FireCalculationResult reordered = calculator.calculate(scenario.input(shuffled));

                assertAggregateEquality(ordered, reordered, seed, method + " ordine risorse");
                assertProjectionEquality(ordered, reordered, seed, method + " ordine risorse");
            }
        }
    }

    @Test
    void splittingResourcesIntoEquivalentHalvesPreservesResults() {
        for (FireMethod method : FireMethod.values()) {
            for (int seed = 0; seed < SAMPLES; seed++) {
                Random random = random(40_000L + method.ordinal() * 1_000L + seed);
                Scenario scenario = scenario(random, method);
                List<AdditionalResource> resources = mixedResources(scenario, random);
                List<AdditionalResource> splitResources = splitInHalves(resources);

                FireCalculationResult whole = calculator.calculate(scenario.input(resources));
                FireCalculationResult split = calculator.calculate(scenario.input(splitResources));

                assertAggregateEquality(whole, split, seed, method + " risorse divise");
                assertProjectionEquality(whole, split, seed, method + " risorse divise");
            }
        }
    }

    @Test
    void monthlyProjectionsConserveBalancesAndRemainFinite() {
        for (FireMethod method : FireMethod.values()) {
            for (int seed = 0; seed < SAMPLES; seed++) {
                Random random = random(50_000L + method.ordinal() * 1_000L + seed);
                Scenario scenario = scenario(random, method);
                FireCalculationResult result = calculator.calculate(
                        scenario.input(mixedResources(scenario, random)));

                assertEquals(result.accumulationMonths() + 1,
                        result.accumulationProjection().size(), message(seed, method + " mesi accumulo"));
                assertEquals(result.fireMonths() + 1,
                        result.decumulationProjection().size(), message(seed, method + " mesi FIRE"));
                verifyAccumulationProjection(result, seed, method);
                verifyExistingInvestmentProjections(result, seed, method);
                verifyDecumulationProjection(result, seed, method);
            }
        }
    }

    private static void verifyAccumulationProjection(
            FireCalculationResult result,
            int seed,
            FireMethod method
    ) {
        List<AccumulationPoint> points = result.accumulationProjection();
        for (int index = 0; index < points.size(); index++) {
            AccumulationPoint point = points.get(index);
            assertEquals(index, point.month(), message(seed, method + " indice accumulo"));
            assertFiniteNonNegative(point.closingBalance(), seed, method + " saldo accumulo");
            assertFiniteNonNegative(point.totalAvailableBalance(), seed,
                    method + " patrimonio disponibile");
            assertMoney(point.closingBalance()
                            + point.availableExistingInvestmentsBalance()
                            + point.availableFutureLumpSumsBalance(),
                    point.totalAvailableBalance(), seed, method + " totale accumulo");
            if (index == 0) {
                continue;
            }
            AccumulationPoint previous = points.get(index - 1);
            assertMoney(point.openingBalance() + point.investmentReturn()
                            + point.contribution() + point.additionalIncomeContribution(),
                    point.closingBalance(), seed, method + " identità accumulo mese " + index);
            assertMoney(previous.cumulativeContributions() + point.contribution(),
                    point.cumulativeContributions(), seed,
                    method + " contributi cumulati mese " + index);
            assertMoney(previous.cumulativeAdditionalIncome() + point.additionalIncomeContribution(),
                    point.cumulativeAdditionalIncome(), seed,
                    method + " rendite cumulate mese " + index);
        }
    }

    private static void verifyExistingInvestmentProjections(
            FireCalculationResult result,
            int seed,
            FireMethod method
    ) {
        for (ExistingInvestmentResult investment : result.existingInvestments()) {
            assertEquals(result.accumulationMonths() + 1, investment.projection().size(),
                    message(seed, method + " mesi investimento"));
            for (int index = 1; index < investment.projection().size(); index++) {
                ExistingInvestmentPoint previous = investment.projection().get(index - 1);
                ExistingInvestmentPoint point = investment.projection().get(index);
                assertMoney(point.openingBalance() + point.investmentReturn() + point.contribution(),
                        point.closingBalance(), seed,
                        method + " identità investimento mese " + index);
                assertMoney(previous.cumulativeContributions() + point.contribution(),
                        point.cumulativeContributions(), seed,
                        method + " contributi investimento mese " + index);
            }
        }
    }

    private static void verifyDecumulationProjection(
            FireCalculationResult result,
            int seed,
            FireMethod method
    ) {
        List<DecumulationPoint> points = result.decumulationProjection();
        for (int index = 0; index < points.size(); index++) {
            DecumulationPoint point = points.get(index);
            assertEquals(index, point.month(), message(seed, method + " indice FIRE"));
            assertFiniteNonNegative(point.closingBalance(), seed, method + " saldo FIRE");
            assertFiniteNonNegative(point.actualWithdrawal(), seed, method + " prelievo effettivo");
            assertFiniteNonNegative(point.shortfall(), seed, method + " shortfall");
            if (index == 0) {
                continue;
            }
            double available = point.openingBalance() + point.capitalInflow();
            double remaining = available - point.actualWithdrawal();
            assertMoney(Math.max(0, point.grossExpense() - point.additionalIncome()),
                    point.scheduledWithdrawal(), seed,
                    method + " prelievo netto mese " + index);
            assertMoney(point.scheduledWithdrawal(),
                    point.actualWithdrawal() + point.shortfall(), seed,
                    method + " copertura prelievo mese " + index);
            assertMoney(remaining * result.monthlyFireReturnRate(),
                    point.investmentReturn(), seed,
                    method + " rendimento FIRE mese " + index);
            assertMoney(Math.max(0, remaining + point.investmentReturn()
                            + point.terminalCapitalInflow()),
                    point.closingBalance(), seed,
                    method + " identità FIRE mese " + index);
        }
    }

    private static void assertAggregateEquality(
            FireCalculationResult expected,
            FireCalculationResult actual,
            int seed,
            String label
    ) {
        assertMoney(expected.selectedTarget(), actual.selectedTarget(), seed, label + " target");
        assertMoney(expected.capitalGap(), actual.capitalGap(), seed, label + " gap");
        assertMoney(expected.initialMonthlyContribution(), actual.initialMonthlyContribution(),
                seed, label + " PAC");
        assertMoney(expected.projectedAccumulationFinalBalance(),
                actual.projectedAccumulationFinalBalance(), seed, label + " saldo accumulo");
        assertMoney(expected.totalCapitalInflows(), actual.totalCapitalInflows(),
                seed, label + " capitali FIRE");
        assertMoney(expected.totalShortfall(), actual.totalShortfall(), seed, label + " shortfall");
        assertMoney(expected.personalDecumulationFinalBalance(),
                actual.personalDecumulationFinalBalance(), seed, label + " saldo finale");
    }

    private static void assertProjectionEquality(
            FireCalculationResult expected,
            FireCalculationResult actual,
            int seed,
            String label
    ) {
        assertEquals(expected.accumulationProjection().size(), actual.accumulationProjection().size(),
                message(seed, label + " lunghezza accumulo"));
        for (int index = 0; index < expected.accumulationProjection().size(); index++) {
            AccumulationPoint left = expected.accumulationProjection().get(index);
            AccumulationPoint right = actual.accumulationProjection().get(index);
            assertMoney(left.closingBalance(), right.closingBalance(), seed,
                    label + " saldo accumulo mese " + index);
            assertMoney(left.totalAvailableBalance(), right.totalAvailableBalance(), seed,
                    label + " totale accumulo mese " + index);
        }
        assertEquals(expected.decumulationProjection().size(), actual.decumulationProjection().size(),
                message(seed, label + " lunghezza FIRE"));
        for (int index = 0; index < expected.decumulationProjection().size(); index++) {
            DecumulationPoint left = expected.decumulationProjection().get(index);
            DecumulationPoint right = actual.decumulationProjection().get(index);
            assertMoney(left.additionalIncome(), right.additionalIncome(), seed,
                    label + " rendita mese " + index);
            assertMoney(left.capitalInflow(), right.capitalInflow(), seed,
                    label + " capitale mese " + index);
            assertMoney(left.closingBalance(), right.closingBalance(), seed,
                    label + " saldo FIRE mese " + index);
        }
    }

    private static List<AdditionalResource> accumulationAndIncomeResources(
            Scenario scenario,
            Random random
    ) {
        ExistingInvestment investment = new ExistingInvestment(
                "Investimento", random.nextDouble() * 30_000, random.nextDouble() * 150,
                scenario.currentAge(), scenario.fireAge(), randomRate(random, -0.03, 0.09),
                randomRate(random, -0.02, 0.04), true);
        PeriodicIncome income = new PeriodicIncome(
                "Rendita", scenario.monthlyExpense() * random.nextDouble() * 0.20,
                randomRate(random, -0.01, 0.03), scenario.currentAge(), null, true, true);
        return List.of(investment, income);
    }

    private static List<AdditionalResource> bridgeResources(Scenario scenario, Random random) {
        int incomeStart = scenario.fireAge() + random.nextInt(Math.max(1, scenario.fireYears()));
        PeriodicIncome income = new PeriodicIncome(
                "Pensione", scenario.monthlyExpense() * (0.10 + random.nextDouble() * 0.40),
                randomRate(random, -0.01, 0.03), incomeStart, null, false, true);
        int receiptAge = scenario.fireAge() + random.nextInt(scenario.fireYears() + 1);
        FutureLumpSum capital = new FutureLumpSum(
                "Capitale ponte", random.nextDouble() * 60_000,
                random.nextBoolean() ? AmountBasis.NOMINAL : AmountBasis.TODAY,
                receiptAge, false, 0);
        return List.of(income, capital);
    }

    private static List<AdditionalResource> mixedResources(Scenario scenario, Random random) {
        ExistingInvestment investment = new ExistingInvestment(
                "Investimento", random.nextDouble() * 50_000, random.nextDouble() * 250,
                scenario.currentAge(), scenario.fireAge(), randomRate(random, -0.04, 0.10),
                randomRate(random, -0.03, 0.05), true);
        PeriodicIncome income = new PeriodicIncome(
                "Rendita", scenario.monthlyExpense() * random.nextDouble() * 0.35,
                randomRate(random, -0.02, 0.04), scenario.currentAge(), null, true, true);
        int receiptAge = scenario.currentAge()
                + random.nextInt(scenario.fireAge() + scenario.fireYears() - scenario.currentAge() + 1);
        FutureLumpSum capital = new FutureLumpSum(
                "Capitale", random.nextDouble() * 80_000,
                random.nextBoolean() ? AmountBasis.NOMINAL : AmountBasis.TODAY,
                receiptAge, random.nextBoolean(), randomRate(random, -0.03, 0.08));
        return List.of(investment, income, capital);
    }

    private static List<AdditionalResource> splitInHalves(List<AdditionalResource> resources) {
        List<AdditionalResource> split = new ArrayList<>();
        for (AdditionalResource resource : resources) {
            if (resource instanceof ExistingInvestment investment) {
                for (int half = 1; half <= 2; half++) {
                    split.add(new ExistingInvestment(
                            investment.name() + " " + half,
                            investment.currentCapital() / 2,
                            investment.initialMonthlyContribution() / 2,
                            investment.contributionStartAge(), investment.contributionEndAge(),
                            investment.annualReturnRate(), investment.annualContributionGrowthRate(),
                            investment.availableAtFire()));
                }
            } else if (resource instanceof PeriodicIncome income) {
                for (int half = 1; half <= 2; half++) {
                    split.add(new PeriodicIncome(
                            income.name() + " " + half, income.monthlyAmountToday() / 2,
                            income.annualGrowthRate(), income.startAge(), income.endAge(),
                            income.investBeforeFire(), income.offsetDuringFire()));
                }
            } else if (resource instanceof FutureLumpSum capital) {
                for (int half = 1; half <= 2; half++) {
                    split.add(new FutureLumpSum(
                            capital.name() + " " + half, capital.amount() / 2,
                            capital.amountBasis(), capital.receiptAge(), capital.investAfterReceipt(),
                            capital.annualReturnRateAfterReceipt()));
                }
            }
        }
        return split;
    }

    private static Scenario scenario(Random random, FireMethod method) {
        int currentAge = 25 + random.nextInt(21);
        int accumulationYears = 2 + random.nextInt(19);
        int fireAge = currentAge + accumulationYears;
        int fireYears = 10 + random.nextInt(31);
        return new Scenario(
                method,
                currentAge,
                fireAge,
                fireYears,
                800 + random.nextDouble() * 3_200,
                randomRate(random, -0.01, 0.05),
                randomRate(random, -0.03, 0.09),
                method == FireMethod.SWR ? randomRate(random, 0.025, 0.07) : null,
                random.nextDouble() * 100_000,
                random.nextDouble() * 80_000,
                randomRate(random, -0.04, 0.11),
                randomRate(random, -0.03, 0.05)
        );
    }

    private static Random random(long seed) {
        return new Random(seed);
    }

    private static double randomRate(Random random, double minimum, double maximum) {
        return minimum + random.nextDouble() * (maximum - minimum);
    }

    private static void assertFiniteNonNegative(double value, int seed, String label) {
        assertTrue(Double.isFinite(value) && value >= -MONEY_TOLERANCE,
                () -> message(seed, label + ": " + value));
    }

    private static void assertMoney(
            double expected,
            double actual,
            int seed,
            String label
    ) {
        assertEquals(expected, actual, MONEY_TOLERANCE, message(seed, label));
    }

    private static void assertAtMost(double actual, double maximum, int seed, String label) {
        assertTrue(actual <= maximum + MONEY_TOLERANCE,
                () -> message(seed, label + ": " + actual + " > " + maximum));
    }

    private static void assertAtLeast(double actual, double minimum, int seed, String label) {
        assertTrue(actual + MONEY_TOLERANCE >= minimum,
                () -> message(seed, label + ": " + actual + " < " + minimum));
    }

    private static String message(int seed, String label) {
        return "seed=" + seed + " - " + label;
    }

    private record Scenario(
            FireMethod method,
            int currentAge,
            int fireAge,
            int fireYears,
            double monthlyExpense,
            double inflation,
            double fireReturn,
            Double swr,
            double terminalCapital,
            double currentCapital,
            double accumulationReturn,
            double contributionGrowth
    ) {

        FireCalculationInput input(List<AdditionalResource> resources) {
            return new FireCalculationInput(
                    method, currentAge, fireAge, fireYears, monthlyExpense, inflation,
                    fireReturn, swr, terminalCapital, currentCapital,
                    accumulationReturn, contributionGrowth, resources);
        }

        FireCalculationInput withExpense(
                double changedExpense,
                List<AdditionalResource> resources
        ) {
            return new FireCalculationInput(
                    method, currentAge, fireAge, fireYears, changedExpense, inflation,
                    fireReturn, swr, terminalCapital, currentCapital,
                    accumulationReturn, contributionGrowth, resources);
        }
    }
}
