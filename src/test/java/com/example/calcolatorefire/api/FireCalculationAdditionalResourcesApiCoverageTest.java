package com.example.calcolatorefire.api;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class FireCalculationAdditionalResourcesApiCoverageTest {

    private static final String ENDPOINT = "/api/v1/fire/calculations";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @ParameterizedTest(name = "additionalResources={0}")
    @MethodSource("absentResourceLists")
    void omittedNullAndEmptyListsPreserveTheBaseCalculation(String representation, String request)
            throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target.selectedTarget")
                        .value(closeTo(557_770.7040, 0.01)))
                .andExpect(jsonPath("$.accumulation.initialMonthlyContribution")
                        .value(closeTo(1_905.5163, 0.01)))
                .andExpect(jsonPath("$.accumulation.existingInvestments", hasSize(0)))
                .andExpect(jsonPath("$.accumulation.futureLumpSums", hasSize(0)));
    }

    @Test
    void rejectsNullItemsInsideTheResourceList() throws Exception {
        performResources("[null]")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("additionalResources[0]")));
    }

    @ParameterizedTest(name = "campo obbligatorio {0}")
    @MethodSource("missingRequiredResourceFields")
    void rejectsMissingOrNullRequiredFieldsForEveryResourceType(String field, String resource)
            throws Exception {
        performResources("[" + resource + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[*].field",
                        hasItem("additionalResources[0]." + field)));
    }

    @ParameterizedTest(name = "importo negativo {0}")
    @MethodSource("negativeResourceAmounts")
    void rejectsNegativeAmountsForEveryResourceType(String field, String resource)
            throws Exception {
        performResources("[" + resource + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[*].field",
                        hasItem("additionalResources[0]." + field)));
    }

    @ParameterizedTest(name = "tasso -100% {0}")
    @MethodSource("invalidResourceRates")
    void rejectsRatesAtMinusOneForEveryResourceType(String field, String resource)
            throws Exception {
        performResources("[" + resource + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[*].field",
                        hasItem("additionalResources[0]." + field)));
    }

    @Test
    void rejectsResourceNamesLongerThanOneHundredCharacters() throws Exception {
        String longName = "x".repeat(101);
        String resource = existingInvestment().replace("\"Investimento\"", "\"" + longName + "\"");

        performResources("[" + resource + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[*].field",
                        hasItem("additionalResources[0].name")));
    }

    @ParameterizedTest(name = "discriminatore non leggibile: {0}")
    @ValueSource(strings = {
            "{\"name\":\"Senza tipo\"}",
            "{\"type\":null}",
            "{\"type\":\"UNKNOWN\"}"
    })
    void rejectsMissingNullAndUnknownResourceTypes(String resource) throws Exception {
        performResources("[" + resource + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void rejectsUnknownAmountBasisAsMalformedJsonValue() throws Exception {
        String resource = futureLumpSum().replace("\"TODAY\"", "\"UNKNOWN\"");

        performResources("[" + resource + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @ParameterizedTest(name = "regola dominio {0}")
    @MethodSource("invalidResourceDomainRules")
    void mapsInvalidResourceRulesToUnprocessableContent(
            String description,
            String expectedCode,
            String resource
    ) throws Exception {
        performResources("[" + resource + "]")
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.instance").value(ENDPOINT));
    }

    @ParameterizedTest(name = "risposta completa {0}")
    @ValueSource(strings = {"FINITE", "SWR"})
    void responsePreservesResourceTotalsProjectionsAndProvenance(String method) throws Exception {
        String resources = "[" + existingInvestment() + "," + periodicIncome() + ","
                + futureLumpSum() + "]";
        String request = withResources(requestForMethod(method), resources);

        var actions = mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accumulationMonths").value(168))
                .andExpect(jsonPath("$.fireMonths").value(420))
                .andExpect(jsonPath("$.target.selectedTarget").value(lessThan(633_350.0)))
                .andExpect(jsonPath("$.target.firstMonthlyAdditionalIncome")
                        .value(closeTo(100.0, 0.01)))
                .andExpect(jsonPath("$.target.firstMonthlyNetWithdrawal")
                        .value(greaterThan(0.0)))
                .andExpect(jsonPath("$.accumulation.totalNominalAdditionalIncomeInvested")
                        .value(closeTo(16_800.0, 0.01)))
                .andExpect(jsonPath("$.accumulation.totalNominalExistingInvestmentContributions")
                        .value(closeTo(50_400.0, 0.01)))
                .andExpect(jsonPath("$.accumulation.availableExistingInvestmentsFinalBalance")
                        .value(greaterThan(0.0)))
                .andExpect(jsonPath("$.accumulation.investedIncomeFinalBalance")
                        .value(greaterThan(0.0)))
                .andExpect(jsonPath("$.accumulation.existingInvestments", hasSize(1)))
                .andExpect(jsonPath("$.accumulation.existingInvestments[0].resourceIndex").value(0))
                .andExpect(jsonPath("$.accumulation.existingInvestments[0].name")
                        .value("Investimento"))
                .andExpect(jsonPath("$.accumulation.existingInvestments[0].availableAtFire")
                        .value(true))
                .andExpect(jsonPath("$.accumulation.existingInvestments[0].projection", hasSize(169)))
                .andExpect(jsonPath("$.accumulation.futureLumpSums", hasSize(1)))
                .andExpect(jsonPath("$.accumulation.futureLumpSums[0].resourceIndex").value(2))
                .andExpect(jsonPath("$.accumulation.futureLumpSums[0].name").value("Capitale"))
                .andExpect(jsonPath("$.accumulation.futureLumpSums[0].amountBasis").value("TODAY"))
                .andExpect(jsonPath("$.accumulation.futureLumpSums[0].fireReceiptMonth").value(121))
                .andExpect(jsonPath("$.accumulation.projection", hasSize(169)))
                .andExpect(jsonPath("$.decumulation.projection", hasSize(421)))
                .andExpect(jsonPath("$.decumulation.projection[1].additionalIncome")
                        .value(closeTo(100.0, 0.01)))
                .andExpect(jsonPath("$.decumulation.projection[121].capitalInflow")
                        .value(greaterThan(50_000.0)))
                .andExpect(jsonPath("$.decumulation.totalCapitalInflows")
                        .value(greaterThan(50_000.0)));

        if ("FINITE".equals(method)) {
            actions.andExpect(jsonPath("$.target.finiteTarget").isNumber())
                    .andExpect(jsonPath("$.target.safeWithdrawalRateBaseTarget").doesNotExist())
                    .andExpect(jsonPath("$.target.safeWithdrawalRateTarget").doesNotExist());
        } else {
            actions.andExpect(jsonPath("$.target.finiteTarget").doesNotExist())
                    .andExpect(jsonPath("$.target.safeWithdrawalRateBaseTarget").isNumber())
                    .andExpect(jsonPath("$.target.safeWithdrawalRateTarget").isNumber());
        }
    }

    private org.springframework.test.web.servlet.ResultActions performResources(String resources)
            throws Exception {
        return mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(withResources(baseRequest(), resources)));
    }

    private static Stream<Arguments> absentResourceLists() {
        return Stream.of(
                Arguments.of("omesso", baseRequest()),
                Arguments.of("null", withResources(baseRequest(), "null")),
                Arguments.of("vuoto", withResources(baseRequest(), "[]"))
        );
    }

    private static Stream<Arguments> missingRequiredResourceFields() {
        return Stream.of(
                Arguments.of("availableAtFire",
                        existingInvestment().replace("\"availableAtFire\": true",
                                "\"availableAtFire\": null")),
                Arguments.of("monthlyAmountToday",
                        periodicIncome().replace("\"monthlyAmountToday\": 100,", "")),
                Arguments.of("amountBasis",
                        futureLumpSum().replace("\"amountBasis\": \"TODAY\",", ""))
        );
    }

    private static Stream<Arguments> negativeResourceAmounts() {
        return Stream.of(
                Arguments.of("currentCapital",
                        existingInvestment().replace("\"currentCapital\": 25000",
                                "\"currentCapital\": -1")),
                Arguments.of("monthlyAmountToday",
                        periodicIncome().replace("\"monthlyAmountToday\": 100",
                                "\"monthlyAmountToday\": -1")),
                Arguments.of("amount",
                        futureLumpSum().replace("\"amount\": 50000", "\"amount\": -1"))
        );
    }

    private static Stream<Arguments> invalidResourceRates() {
        return Stream.of(
                Arguments.of("annualReturnRate",
                        existingInvestment().replace("\"annualReturnRate\": 0.05",
                                "\"annualReturnRate\": -1")),
                Arguments.of("annualGrowthRate",
                        periodicIncome().replace("\"annualGrowthRate\": 0",
                                "\"annualGrowthRate\": -1")),
                Arguments.of("annualReturnRateAfterReceipt",
                        futureLumpSum().replace("\"annualReturnRateAfterReceipt\": 0.03",
                                "\"annualReturnRateAfterReceipt\": -1"))
        );
    }

    private static Stream<Arguments> invalidResourceDomainRules() {
        return Stream.of(
                Arguments.of("PAC con un solo estremo", "INVALID_RESOURCE_PERIOD",
                        existingInvestment().replace("\"contributionEndAge\": 50",
                                "\"contributionEndAge\": null")),
                Arguments.of("PAC con periodo invertito", "INVALID_RESOURCE_PERIOD",
                        existingInvestment()
                                .replace("\"contributionStartAge\": 36", "\"contributionStartAge\": 45")
                                .replace("\"contributionEndAge\": 50", "\"contributionEndAge\": 44")),
                Arguments.of("PAC prima dell'età attuale", "INVALID_RESOURCE_PERIOD",
                        existingInvestment().replace("\"contributionStartAge\": 36",
                                "\"contributionStartAge\": 35")),
                Arguments.of("rendita senza utilizzo", "INVALID_RESOURCE",
                        periodicIncome()
                                .replace("\"investBeforeFire\": true", "\"investBeforeFire\": false")
                                .replace("\"offsetDuringFire\": true", "\"offsetDuringFire\": false")),
                Arguments.of("rendita con periodo vuoto", "INVALID_RESOURCE_PERIOD",
                        periodicIncome().replace("\"endAge\": null", "\"endAge\": 36")),
                Arguments.of("rendita fuori orizzonte", "INVALID_RESOURCE_PERIOD",
                        periodicIncome()
                                .replace("\"startAge\": 36", "\"startAge\": 86")
                                .replace("\"investBeforeFire\": true", "\"investBeforeFire\": false")),
                Arguments.of("capitale prima dell'età attuale", "INVALID_RESOURCE_PERIOD",
                        futureLumpSum().replace("\"receiptAge\": 60", "\"receiptAge\": 35")),
                Arguments.of("capitale dopo l'orizzonte", "INVALID_RESOURCE_PERIOD",
                        futureLumpSum().replace("\"receiptAge\": 60", "\"receiptAge\": 86"))
        );
    }

    private static String requestForMethod(String method) {
        if ("FINITE".equals(method)) {
            return baseRequest();
        }
        return baseRequest()
                .replace("\"FINITE\"", "\"SWR\"")
                .replace("\"annualSafeWithdrawalRate\": null",
                        "\"annualSafeWithdrawalRate\": 0.04")
                .replace("\"terminalCapitalToday\": 0", "\"terminalCapitalToday\": 0");
    }

    private static String withResources(String request, String resources) {
        int closingBrace = request.lastIndexOf('}');
        return request.substring(0, closingBrace)
                + ",\n\"additionalResources\": " + resources
                + request.substring(closingBrace);
    }

    private static String existingInvestment() {
        return """
                {
                  "type": "EXISTING_INVESTMENT",
                  "name": "Investimento",
                  "currentCapital": 25000,
                  "initialMonthlyContribution": 300,
                  "contributionStartAge": 36,
                  "contributionEndAge": 50,
                  "annualReturnRate": 0.05,
                  "annualContributionGrowthRate": 0,
                  "availableAtFire": true
                }
                """;
    }

    private static String periodicIncome() {
        return """
                {
                  "type": "PERIODIC_INCOME",
                  "name": "Rendita",
                  "monthlyAmountToday": 100,
                  "annualGrowthRate": 0,
                  "startAge": 36,
                  "endAge": null,
                  "investBeforeFire": true,
                  "offsetDuringFire": true
                }
                """;
    }

    private static String futureLumpSum() {
        return """
                {
                  "type": "FUTURE_LUMP_SUM",
                  "name": "Capitale",
                  "amount": 50000,
                  "amountBasis": "TODAY",
                  "receiptAge": 60,
                  "investAfterReceipt": true,
                  "annualReturnRateAfterReceipt": 0.03
                }
                """;
    }

    private static String baseRequest() {
        return """
                {
                  "method": "FINITE",
                  "currentAge": 36,
                  "fireAge": 50,
                  "fireDurationYears": 35,
                  "monthlyExpenseToday": 1600,
                  "annualInflationRate": 0.02,
                  "annualFireReturnRate": 0.05,
                  "annualSafeWithdrawalRate": null,
                  "terminalCapitalToday": 0,
                  "currentCapital": 10000,
                  "annualAccumulationReturnRate": 0.07,
                  "annualContributionGrowthRate": 0
                }
                """;
    }
}
