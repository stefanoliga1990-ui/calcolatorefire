package com.example.calcolatorefire.api;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
                .andExpect(jsonPath("$.target.recommendedTarget").value(closeTo(613_547.7744, 0.01)))
                .andExpect(jsonPath("$.accumulation.initialMonthlyContribution").value(closeTo(2_105.3040, 0.01)))
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
                  "safetyMargin": 0.10,
                  "terminalCapitalToday": 0,
                  "currentCapital": 10000,
                  "annualAccumulationReturnRate": 0.07,
                  "annualContributionGrowthRate": 0
                }
                """;
    }
}
