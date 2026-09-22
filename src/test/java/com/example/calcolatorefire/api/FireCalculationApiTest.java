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

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class FireCalculationApiTest {

    private static final String ENDPOINT = "/api/v1/fire/calculations";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void calculatesTheBaseScenario() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(baseRequest()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accumulationMonths").value(168))
                .andExpect(jsonPath("$.fireMonths").value(420))
                .andExpect(jsonPath("$.target.finiteTarget").value(closeTo(557_770.7040, 0.01)))
                .andExpect(jsonPath("$.target.safeWithdrawalRateTarget").doesNotExist())
                .andExpect(jsonPath("$.target.selectedTarget").value(closeTo(557_770.7040, 0.01)))
                .andExpect(jsonPath("$.target.recommendedTarget").doesNotExist())
                .andExpect(jsonPath("$.accumulation.initialMonthlyContribution").value(closeTo(1_905.5163, 0.01)))
                .andExpect(jsonPath("$.accumulation.projectedCurrentCapitalAtFire").doesNotExist())
                .andExpect(jsonPath("$.accumulation.projection", hasSize(169)))
                .andExpect(jsonPath("$.decumulation.projection", hasSize(421)))
                .andExpect(jsonPath("$.decumulation.totalShortfall").value(closeTo(0.0, 0.01)));
    }

    @Test
    void returnsDomainErrorWhenAgeOrderIsInvalid() throws Exception {
        String request = baseRequest().replace("\"currentAge\": 36", "\"currentAge\": 51");

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_AGE_ORDER"))
                .andExpect(jsonPath("$.title").value("Calcolo non valido"))
                .andExpect(jsonPath("$.instance").value(ENDPOINT));
    }

    @Test
    void returnsFieldErrorsWhenRequiredInputsAreMissing() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    @Test
    void returnsReadableErrorForUnknownMethod() throws Exception {
        String request = baseRequest().replace("\"FINITE\"", "\"UNKNOWN\"");

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.title").value("Richiesta non leggibile"));
    }

    @Test
    void calculatesSwrWithoutFiniteTarget() throws Exception {
        String request = baseRequest()
                .replace("\"FINITE\"", "\"SWR\"")
                .replace("\"annualSafeWithdrawalRate\": null", "\"annualSafeWithdrawalRate\": 0.04");
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target.safeWithdrawalRateTarget").value(closeTo(633_349.8063, 0.01)))
                .andExpect(jsonPath("$.target.finiteTarget").doesNotExist());
    }

    @Test
    void rejectsSwrWithoutRate() throws Exception {
        String request = baseRequest().replace("\"FINITE\"", "\"SWR\"");
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_SWR"));
    }

    @Test
    void reportsWhenAnAggressiveSwrDoesNotCoverTheFireDuration() throws Exception {
        String request = baseRequest()
                .replace("\"FINITE\"", "\"SWR\"")
                .replace("\"annualSafeWithdrawalRate\": null", "\"annualSafeWithdrawalRate\": 0.06");

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target.safeWithdrawalRateTarget").value(closeTo(422_233.2042, 0.01)))
                .andExpect(jsonPath("$.decumulation.depletionMonth").value(273))
                .andExpect(jsonPath("$.decumulation.totalShortfall").value(greaterThan(0.0)));
    }

    @Test
    void acceptsAnExplicitEmptyAdditionalResourcesListWithoutChangingTheCalculation() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withAdditionalResources("[]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target.selectedTarget").value(closeTo(557_770.7040, 0.01)))
                .andExpect(jsonPath("$.accumulation.initialMonthlyContribution").value(closeTo(1_905.5163, 0.01)));
    }

    @Test
    void deserializesAndValidatesAllAdditionalResourceTypes() throws Exception {
        String resources = """
                [
                  {
                    "type": "EXISTING_INVESTMENT",
                    "name": "PAC già attivo",
                    "currentCapital": 25000,
                    "initialMonthlyContribution": 300,
                    "contributionStartAge": 36,
                    "contributionEndAge": 50,
                    "annualReturnRate": 0.05,
                    "annualContributionGrowthRate": 0,
                    "availableAtFire": true
                  },
                  {
                    "type": "PERIODIC_INCOME",
                    "name": "Pensione",
                    "monthlyAmountToday": 1000,
                    "annualGrowthRate": 0.02,
                    "startAge": 67,
                    "endAge": null,
                    "investBeforeFire": false,
                    "offsetDuringFire": true
                  },
                  {
                    "type": "FUTURE_LUMP_SUM",
                    "name": "Capitale futuro",
                    "amount": 50000,
                    "amountBasis": "TODAY",
                    "receiptAge": 60,
                    "investAfterReceipt": true,
                    "annualReturnRateAfterReceipt": 0.03
                  }
                ]
                """;

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withAdditionalResources(resources)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target.selectedTarget").value(lessThan(557_770.7040)))
                .andExpect(jsonPath("$.accumulation.availableExistingInvestmentsFinalBalance")
                        .value(greaterThan(0.0)))
                .andExpect(jsonPath("$.accumulation.existingInvestments", hasSize(1)))
                .andExpect(jsonPath("$.accumulation.periodicIncomes", hasSize(1)))
                .andExpect(jsonPath("$.accumulation.periodicIncomes[0].resourceIndex").value(1))
                .andExpect(jsonPath("$.accumulation.periodicIncomes[0].projection[0].monthlyAmount").value(0.0))
                .andExpect(jsonPath("$.accumulation.periodicIncomes[0].projection[372].monthlyAmount")
                        .value(greaterThan(0.0)))
                .andExpect(jsonPath("$.accumulation.futureLumpSums", hasSize(1)))
                .andExpect(jsonPath("$.accumulation.futureLumpSums[0].projection[287].availableAmount").value(0.0))
                .andExpect(jsonPath("$.accumulation.futureLumpSums[0].projection[288].availableAmount")
                        .value(greaterThan(0.0)))
                .andExpect(jsonPath("$.decumulation.projection[121].capitalInflow")
                        .value(greaterThan(0.0)))
                .andExpect(jsonPath("$.decumulation.projection[205].additionalIncome")
                        .value(greaterThan(0.0)));
    }

    @Test
    void rejectsInvalidFieldsInsideAnAdditionalResource() throws Exception {
        String resources = """
                [{
                  "type": "EXISTING_INVESTMENT",
                  "currentCapital": -1,
                  "initialMonthlyContribution": 0,
                  "contributionStartAge": null,
                  "contributionEndAge": null,
                  "annualReturnRate": 0.05,
                  "annualContributionGrowthRate": 0,
                  "availableAtFire": true
                }]
                """;

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withAdditionalResources(resources)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[*].field",
                        hasItem("additionalResources[0].currentCapital")));
    }

    @Test
    void rejectsUnknownAdditionalResourceType() throws Exception {
        String resources = """
                [{"type": "UNKNOWN_RESOURCE"}]
                """;

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withAdditionalResources(resources)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void rejectsAnAdditionalResourceOutsideItsUsablePeriod() throws Exception {
        String resources = """
                [{
                  "type": "PERIODIC_INCOME",
                  "monthlyAmountToday": 1000,
                  "annualGrowthRate": 0,
                  "startAge": 86,
                  "endAge": null,
                  "investBeforeFire": false,
                  "offsetDuringFire": true
                }]
                """;

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withAdditionalResources(resources)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_RESOURCE_PERIOD"));
    }

    @Test
    void rejectsMainFieldsAboveTheirUpperLimits() throws Exception {
        String request = baseRequest()
                .replace("\"currentAge\": 36", "\"currentAge\": 131")
                .replace("\"monthlyExpenseToday\": 1600", "\"monthlyExpenseToday\": 1000000000001")
                .replace("\"annualFireReturnRate\": 0.05", "\"annualFireReturnRate\": 1.01");

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("currentAge")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("monthlyExpenseToday")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("annualFireReturnRate")));
    }

    @Test
    void rejectsACombinedHorizonEndingAfterAgeOneHundredAndThirty() throws Exception {
        String request = baseRequest()
                .replace("\"fireAge\": 50", "\"fireAge\": 100")
                .replace("\"fireDurationYears\": 35", "\"fireDurationYears\": 31");

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_FIRE_DURATION"));
    }

    @Test
    void rejectsResourceFieldsAboveTheirUpperLimits() throws Exception {
        String resources = """
                [{
                  "type": "FUTURE_LUMP_SUM",
                  "amount": 1000000000001,
                  "amountBasis": "TODAY",
                  "receiptAge": 131,
                  "investAfterReceipt": true,
                  "annualReturnRateAfterReceipt": 1.01
                }]
                """;

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withAdditionalResources(resources)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("additionalResources[0].amount")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("additionalResources[0].receiptAge")))
                .andExpect(jsonPath("$.fieldErrors[*].field",
                        hasItem("additionalResources[0].annualReturnRateAfterReceipt")));
    }

    @Test
    void rejectsMoreThanOneHundredResourcesAtTheApiBoundary() throws Exception {
        String resource = """
                {"type":"PERIODIC_INCOME","monthlyAmountToday":0,"annualGrowthRate":0,
                 "startAge":36,"endAge":null,"investBeforeFire":true,"offsetDuringFire":true}
                """;
        String resources = "[" + String.join(",", Collections.nCopies(101, resource)) + "]";

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withAdditionalResources(resources)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("additionalResources")));
    }

    private static String withAdditionalResources(String resources) {
        String request = baseRequest();
        int closingBrace = request.lastIndexOf('}');
        return request.substring(0, closingBrace)
                + ",\n\"additionalResources\": " + resources
                + request.substring(closingBrace);
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
