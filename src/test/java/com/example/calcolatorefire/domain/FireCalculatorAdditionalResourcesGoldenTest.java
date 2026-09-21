package com.example.calcolatorefire.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.example.calcolatorefire.api.FireCalculationRequest;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class FireCalculatorAdditionalResourcesGoldenTest {

    private static final double MONEY_TOLERANCE = 0.01;
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private final FireCalculator calculator = new FireCalculator();

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenScenarios")
    void matchesAdditionalResourcesGoldenScenario(String scenarioId, GoldenScenario scenario) {
        FireCalculationResult result = calculator.calculate(scenario.request().toDomain());
        Expected expected = scenario.expected();

        assertIntegerIfPresent(expected.accumulationMonths(), result.accumulationMonths(), scenarioId, "accumulationMonths");
        assertIntegerIfPresent(expected.fireMonths(), result.fireMonths(), scenarioId, "fireMonths");
        assertMoneyIfPresent(expected.finiteTarget(), result.finiteTarget(), scenarioId, "finiteTarget");
        assertMoneyIfPresent(expected.safeWithdrawalRateBaseTarget(), result.safeWithdrawalRateBaseTarget(), scenarioId,
                "safeWithdrawalRateBaseTarget");
        assertMoneyIfPresent(expected.safeWithdrawalRateTarget(), result.safeWithdrawalRateTarget(), scenarioId,
                "safeWithdrawalRateTarget");
        assertMoneyIfPresent(expected.selectedTarget(), result.selectedTarget(), scenarioId, "selectedTarget");
        assertMoneyIfPresent(expected.initialMonthlyContribution(), result.initialMonthlyContribution(), scenarioId,
                "initialMonthlyContribution");
        assertMoneyIfPresent(expected.projectedFinalBalance(), result.projectedAccumulationFinalBalance(), scenarioId,
                "projectedFinalBalance");
        assertMoneyIfPresent(expected.investedIncomeFinalBalance(), result.projectedInvestedIncomeFinalBalance(), scenarioId,
                "investedIncomeFinalBalance");
        assertMoneyIfPresent(expected.availableExistingInvestmentsFinalBalance(),
                result.projectedAvailableExistingInvestmentsFinalBalance(), scenarioId,
                "availableExistingInvestmentsFinalBalance");
        assertMoneyIfPresent(expected.availableFutureLumpSumsFinalBalance(),
                result.projectedAvailableFutureLumpSumsFinalBalance(), scenarioId,
                "availableFutureLumpSumsFinalBalance");
        assertMoneyIfPresent(expected.totalNominalAdditionalIncomeInvested(),
                result.totalNominalAdditionalIncomeInvested(), scenarioId, "totalNominalAdditionalIncomeInvested");
        assertMoneyIfPresent(expected.totalNominalExistingInvestmentContributions(),
                result.totalNominalExistingInvestmentContributions(), scenarioId,
                "totalNominalExistingInvestmentContributions");
        assertMoneyIfPresent(expected.personalFinalBalance(), result.personalDecumulationFinalBalance(), scenarioId,
                "personalFinalBalance");
        assertMoneyIfPresent(expected.firstMonthlyAdditionalIncome(), result.firstMonthlyAdditionalIncome(), scenarioId,
                "firstMonthlyAdditionalIncome");
        assertMoneyIfPresent(expected.firstMonthlyNetWithdrawal(), result.firstMonthlyNetWithdrawal(), scenarioId,
                "firstMonthlyNetWithdrawal");
        assertMoneyIfPresent(expected.totalCapitalInflows(), result.totalCapitalInflows(), scenarioId,
                "totalCapitalInflows");
        assertMoneyIfPresent(expected.terminalCapitalInflow(), result.terminalCapitalInflow(), scenarioId,
                "terminalCapitalInflow");
        assertMoneyIfPresent(expected.totalShortfall(), result.totalShortfall(), scenarioId, "totalShortfall");
        assertEquals(expected.depletionMonth(), result.depletionMonth(), label(scenarioId, "depletionMonth"));

        assertMethodSpecificTargets(scenario.request(), result, scenarioId);
        assertAccumulationCheckpoints(expected.accumulationCheckpoints(), result, scenarioId);
        assertDecumulationCheckpoints(expected.decumulationCheckpoints(), result, scenarioId);
        assertExistingInvestments(expected.existingInvestments(), result, scenarioId);
        assertFutureLumpSums(expected.futureLumpSums(), result, scenarioId);
    }

    private static void assertMethodSpecificTargets(
            FireCalculationRequest request,
            FireCalculationResult result,
            String scenarioId
    ) {
        if (request.method() == FireMethod.FINITE) {
            assertNotNull(result.finiteTarget(), label(scenarioId, "finiteTarget"));
            assertNull(result.safeWithdrawalRateBaseTarget(), label(scenarioId, "safeWithdrawalRateBaseTarget"));
            assertNull(result.safeWithdrawalRateTarget(), label(scenarioId, "safeWithdrawalRateTarget"));
        } else {
            assertNull(result.finiteTarget(), label(scenarioId, "finiteTarget"));
            assertNotNull(result.safeWithdrawalRateBaseTarget(), label(scenarioId, "safeWithdrawalRateBaseTarget"));
            assertNotNull(result.safeWithdrawalRateTarget(), label(scenarioId, "safeWithdrawalRateTarget"));
        }
    }

    private static void assertAccumulationCheckpoints(
            List<AccumulationCheckpoint> checkpoints,
            FireCalculationResult result,
            String scenarioId
    ) {
        for (AccumulationCheckpoint expected : orEmpty(checkpoints)) {
            AccumulationPoint actual = result.accumulationProjection().stream()
                    .filter(point -> point.month() == expected.month())
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(label(scenarioId, "missing accumulation month " + expected.month())));

            assertMoneyIfPresent(expected.contribution(), actual.contribution(), scenarioId,
                    "accumulation[" + expected.month() + "].contribution");
            assertMoneyIfPresent(expected.additionalIncomeContribution(), actual.additionalIncomeContribution(), scenarioId,
                    "accumulation[" + expected.month() + "].additionalIncomeContribution");
            assertMoneyIfPresent(expected.closingBalance(), actual.closingBalance(), scenarioId,
                    "accumulation[" + expected.month() + "].closingBalance");
            assertMoneyIfPresent(expected.availableExistingInvestmentsBalance(),
                    actual.availableExistingInvestmentsBalance(), scenarioId,
                    "accumulation[" + expected.month() + "].availableExistingInvestmentsBalance");
            assertMoneyIfPresent(expected.availableFutureLumpSumsBalance(), actual.availableFutureLumpSumsBalance(),
                    scenarioId, "accumulation[" + expected.month() + "].availableFutureLumpSumsBalance");
            assertMoneyIfPresent(expected.totalAvailableBalance(), actual.totalAvailableBalance(), scenarioId,
                    "accumulation[" + expected.month() + "].totalAvailableBalance");
        }
    }

    private static void assertDecumulationCheckpoints(
            List<DecumulationCheckpoint> checkpoints,
            FireCalculationResult result,
            String scenarioId
    ) {
        for (DecumulationCheckpoint expected : orEmpty(checkpoints)) {
            DecumulationPoint actual = result.decumulationProjection().stream()
                    .filter(point -> point.month() == expected.month())
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(label(scenarioId, "missing decumulation month " + expected.month())));

            assertMoneyIfPresent(expected.grossExpense(), actual.grossExpense(), scenarioId,
                    "decumulation[" + expected.month() + "].grossExpense");
            assertMoneyIfPresent(expected.additionalIncome(), actual.additionalIncome(), scenarioId,
                    "decumulation[" + expected.month() + "].additionalIncome");
            assertMoneyIfPresent(expected.scheduledWithdrawal(), actual.scheduledWithdrawal(), scenarioId,
                    "decumulation[" + expected.month() + "].scheduledWithdrawal");
            assertMoneyIfPresent(expected.capitalInflow(), actual.capitalInflow(), scenarioId,
                    "decumulation[" + expected.month() + "].capitalInflow");
            assertMoneyIfPresent(expected.terminalCapitalInflow(), actual.terminalCapitalInflow(), scenarioId,
                    "decumulation[" + expected.month() + "].terminalCapitalInflow");
            assertMoneyIfPresent(expected.actualWithdrawal(), actual.actualWithdrawal(), scenarioId,
                    "decumulation[" + expected.month() + "].actualWithdrawal");
            assertMoneyIfPresent(expected.shortfall(), actual.shortfall(), scenarioId,
                    "decumulation[" + expected.month() + "].shortfall");
            assertMoneyIfPresent(expected.closingBalance(), actual.closingBalance(), scenarioId,
                    "decumulation[" + expected.month() + "].closingBalance");
        }
    }

    private static void assertExistingInvestments(
            List<ExistingInvestmentExpected> expectedInvestments,
            FireCalculationResult result,
            String scenarioId
    ) {
        for (ExistingInvestmentExpected expected : orEmpty(expectedInvestments)) {
            ExistingInvestmentResult actual = result.existingInvestments().stream()
                    .filter(investment -> investment.resourceIndex() == expected.resourceIndex())
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(label(scenarioId,
                            "missing existing investment " + expected.resourceIndex())));

            if (expected.availableAtFire() != null) {
                assertEquals(expected.availableAtFire(), actual.availableAtFire(),
                        label(scenarioId, "existingInvestment[" + expected.resourceIndex() + "].availableAtFire"));
            }
            assertMoneyIfPresent(expected.totalNominalContributions(), actual.totalNominalContributions(), scenarioId,
                    "existingInvestment[" + expected.resourceIndex() + "].totalNominalContributions");
            assertMoneyIfPresent(expected.finalBalance(), actual.finalBalance(), scenarioId,
                    "existingInvestment[" + expected.resourceIndex() + "].finalBalance");
        }
    }

    private static void assertFutureLumpSums(
            List<FutureLumpSumExpected> expectedLumpSums,
            FireCalculationResult result,
            String scenarioId
    ) {
        for (FutureLumpSumExpected expected : orEmpty(expectedLumpSums)) {
            FutureLumpSumResult actual = result.futureLumpSums().stream()
                    .filter(lumpSum -> lumpSum.resourceIndex() == expected.resourceIndex())
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(label(scenarioId,
                            "missing future lump sum " + expected.resourceIndex())));

            assertMoneyIfPresent(expected.nominalAmountAtReceipt(), actual.nominalAmountAtReceipt(), scenarioId,
                    "futureLumpSum[" + expected.resourceIndex() + "].nominalAmountAtReceipt");
            assertMoneyIfPresent(expected.balanceAtFire(), actual.balanceAtFire(), scenarioId,
                    "futureLumpSum[" + expected.resourceIndex() + "].balanceAtFire");
            assertEquals(expected.fireReceiptMonth(), actual.fireReceiptMonth(),
                    label(scenarioId, "futureLumpSum[" + expected.resourceIndex() + "].fireReceiptMonth"));
        }
    }

    private static void assertMoneyIfPresent(Double expected, Double actual, String scenarioId, String field) {
        if (expected != null) {
            assertNotNull(actual, label(scenarioId, field));
            assertEquals(expected, actual, MONEY_TOLERANCE, label(scenarioId, field));
        }
    }

    private static void assertIntegerIfPresent(Integer expected, int actual, String scenarioId, String field) {
        if (expected != null) {
            assertEquals(expected.intValue(), actual, label(scenarioId, field));
        }
    }

    private static String label(String scenarioId, String field) {
        return scenarioId + " - " + field;
    }

    private static <T> List<T> orEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static Stream<Arguments> goldenScenarios() throws IOException {
        try (InputStream input = FireCalculatorAdditionalResourcesGoldenTest.class
                .getResourceAsStream("/golden-additional-resources.json")) {
            Objects.requireNonNull(input, "Missing golden-additional-resources.json");
            List<GoldenScenario> scenarios = OBJECT_MAPPER.readValue(input, new TypeReference<>() {
            });
            return scenarios.stream().map(scenario -> Arguments.of(scenario.scenarioId(), scenario));
        }
    }

    private record GoldenScenario(String scenarioId, FireCalculationRequest request, Expected expected) {
    }

    private record Expected(
            Integer accumulationMonths,
            Integer fireMonths,
            Double finiteTarget,
            Double safeWithdrawalRateBaseTarget,
            Double safeWithdrawalRateTarget,
            Double selectedTarget,
            Double initialMonthlyContribution,
            Double projectedFinalBalance,
            Double investedIncomeFinalBalance,
            Double availableExistingInvestmentsFinalBalance,
            Double availableFutureLumpSumsFinalBalance,
            Double totalNominalAdditionalIncomeInvested,
            Double totalNominalExistingInvestmentContributions,
            Double personalFinalBalance,
            Double firstMonthlyAdditionalIncome,
            Double firstMonthlyNetWithdrawal,
            Double totalCapitalInflows,
            Double terminalCapitalInflow,
            Double totalShortfall,
            Integer depletionMonth,
            List<AccumulationCheckpoint> accumulationCheckpoints,
            List<DecumulationCheckpoint> decumulationCheckpoints,
            List<ExistingInvestmentExpected> existingInvestments,
            List<FutureLumpSumExpected> futureLumpSums
    ) {
    }

    private record AccumulationCheckpoint(
            int month,
            Double contribution,
            Double additionalIncomeContribution,
            Double closingBalance,
            Double availableExistingInvestmentsBalance,
            Double availableFutureLumpSumsBalance,
            Double totalAvailableBalance
    ) {
    }

    private record DecumulationCheckpoint(
            int month,
            Double grossExpense,
            Double additionalIncome,
            Double scheduledWithdrawal,
            Double capitalInflow,
            Double terminalCapitalInflow,
            Double actualWithdrawal,
            Double shortfall,
            Double closingBalance
    ) {
    }

    private record ExistingInvestmentExpected(
            int resourceIndex,
            Boolean availableAtFire,
            Double totalNominalContributions,
            Double finalBalance
    ) {
    }

    private record FutureLumpSumExpected(
            int resourceIndex,
            Double nominalAmountAtReceipt,
            Double balanceAtFire,
            Integer fireReceiptMonth
    ) {
    }
}
