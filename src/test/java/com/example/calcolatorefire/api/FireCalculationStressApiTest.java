package com.example.calcolatorefire.api;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class FireCalculationStressApiTest {

    private static final String ENDPOINT = "/api/v1/fire/calculations";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void serializesLongMonthlyProjections() throws Exception {
        String request = """
                {
                  "method": "FINITE",
                  "currentAge": 20,
                  "fireAge": 80,
                  "fireDurationYears": 80,
                  "monthlyExpenseToday": 2000,
                  "annualInflationRate": 0.02,
                  "annualFireReturnRate": 0.04,
                  "annualSafeWithdrawalRate": null,
                  "terminalCapitalToday": 500000,
                  "currentCapital": 10000,
                  "annualAccumulationReturnRate": 0.05,
                  "annualContributionGrowthRate": 0.01,
                  "additionalResources": []
                }
                """;

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accumulationMonths").value(720))
                .andExpect(jsonPath("$.fireMonths").value(960))
                .andExpect(jsonPath("$.accumulation.projection", hasSize(721)))
                .andExpect(jsonPath("$.decumulation.projection", hasSize(961)));
    }

    @Test
    void acceptsNinetyResourcesInOneRequestAndPreservesTheirIndexes() throws Exception {
        String request = baseRequest(manyResourceJson(30));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accumulation.existingInvestments", hasSize(30)))
                .andExpect(jsonPath("$.accumulation.futureLumpSums", hasSize(30)))
                .andExpect(jsonPath("$.accumulation.existingInvestments[29].resourceIndex").value(29))
                .andExpect(jsonPath("$.accumulation.futureLumpSums[0].resourceIndex").value(60))
                .andExpect(jsonPath("$.accumulation.futureLumpSums[29].resourceIndex").value(89))
                .andExpect(jsonPath("$.accumulation.totalNominalAdditionalIncomeInvested")
                        .value(greaterThan(0.0)))
                .andExpect(jsonPath("$.decumulation.totalCapitalInflows").value(greaterThan(0.0)));
    }

    @Test
    void serializesLargeButFiniteAmounts() throws Exception {
        String request = """
                {
                  "method": "FINITE",
                  "currentAge": 30,
                  "fireAge": 60,
                  "fireDurationYears": 50,
                  "monthlyExpenseToday": 10000000,
                  "annualInflationRate": 0.04,
                  "annualFireReturnRate": 0.10,
                  "annualSafeWithdrawalRate": null,
                  "terminalCapitalToday": 1000000000,
                  "currentCapital": 3000000000,
                  "annualAccumulationReturnRate": 0.15,
                  "annualContributionGrowthRate": 0.03,
                  "additionalResources": []
                }
                """;

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target.selectedTarget").isNumber())
                .andExpect(jsonPath("$.accumulation.projectedFinalBalance").isNumber())
                .andExpect(jsonPath("$.decumulation.personalFinalBalance").isNumber());
    }

    private static String baseRequest(String resources) {
        return """
                {
                  "method": "SWR",
                  "currentAge": 36,
                  "fireAge": 50,
                  "fireDurationYears": 35,
                  "monthlyExpenseToday": 1600,
                  "annualInflationRate": 0.02,
                  "annualFireReturnRate": 0.05,
                  "annualSafeWithdrawalRate": 0.04,
                  "terminalCapitalToday": 0,
                  "currentCapital": 0,
                  "annualAccumulationReturnRate": 0.05,
                  "annualContributionGrowthRate": 0,
                  "additionalResources": %s
                }
                """.formatted(resources);
    }

    private static String manyResourceJson(int resourcesPerType) {
        List<String> resources = new ArrayList<>(resourcesPerType * 3);
        for (int index = 0; index < resourcesPerType; index++) {
            resources.add("""
                    {"type":"EXISTING_INVESTMENT","name":"Investimento %d",
                     "currentCapital":%d,"initialMonthlyContribution":10,
                     "contributionStartAge":36,"contributionEndAge":50,
                     "annualReturnRate":0.03,"annualContributionGrowthRate":0,
                     "availableAtFire":true}
                    """.formatted(index, 1_000 + index));
        }
        for (int index = 0; index < resourcesPerType; index++) {
            resources.add("""
                    {"type":"PERIODIC_INCOME","name":"Rendita %d",
                     "monthlyAmountToday":%d,"annualGrowthRate":0,
                     "startAge":36,"endAge":null,
                     "investBeforeFire":true,"offsetDuringFire":true}
                    """.formatted(index, 10 + index));
        }
        for (int index = 0; index < resourcesPerType; index++) {
            resources.add("""
                    {"type":"FUTURE_LUMP_SUM","name":"Capitale %d",
                     "amount":%d,"amountBasis":"NOMINAL","receiptAge":%d,
                     "investAfterReceipt":false,"annualReturnRateAfterReceipt":0}
                    """.formatted(index, 1_000 + index * 100, 36 + index));
        }
        return "[" + String.join(",", resources) + "]";
    }
}
