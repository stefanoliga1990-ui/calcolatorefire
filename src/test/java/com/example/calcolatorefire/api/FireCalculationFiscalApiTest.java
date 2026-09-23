package com.example.calcolatorefire.api;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class FireCalculationFiscalApiTest {

    private static final String ENDPOINT = "/api/v1/fire/calculations";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void appliesApprovedFiscalDefaultsWhenFieldsAreOmitted() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(baseRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fiscal.settings.capitalGainsTaxRate").value(0.26))
                .andExpect(jsonPath("$.fiscal.settings.annualStampDutyRate").value(0.002))
                .andExpect(jsonPath("$.fiscal.settings.monthlyStampDutyRate").isNumber())
                .andExpect(jsonPath("$.fiscal.accumulation.portfolios[0].sourceType")
                        .value("MAIN_PORTFOLIO"))
                .andExpect(jsonPath("$.fiscal.accumulation.portfolios[0].initialBalance")
                        .value(10_000.0))
                .andExpect(jsonPath("$.fiscal.accumulation.portfolios[0].initialTaxBasis")
                        .value(10_000.0))
                .andExpect(jsonPath("$.fiscal.accumulation.portfolios[0].projection", hasSize(169)))
                .andExpect(jsonPath("$.fiscal.decumulation.personal.projection", hasSize(421)));
    }

    @Test
    void zeroFiscalRatesPreserveTheCurrentPublicCalculation() throws Exception {
        String request = addFields(baseRequest(), """
                "capitalGainsTaxRate": 0,
                "annualStampDutyRate": 0
                """);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fiscal.target.selectedTarget")
                        .value(closeTo(557_770.7040, 0.01)))
                .andExpect(jsonPath("$.fiscal.accumulation.initialMonthlyContribution")
                        .value(closeTo(1_905.5163, 0.01)))
                .andExpect(jsonPath("$.fiscal.totals.totalEstimatedTaxes").value(0.0));
    }

    @Test
    void exposesGrossSaleTaxAndNetProceedsForManualTaxBasis() throws Exception {
        String request = simpleFiniteRequest("""
                ,
                "capitalGainsTaxRate": 0.26,
                "annualStampDutyRate": 0,
                "currentTaxBasis": 10500
                """);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target.selectedTarget").value(closeTo(12_000.0, 0.01)))
                .andExpect(jsonPath("$.fiscal.target.selectedTarget")
                        .value(closeTo(13_015.184381, 0.01)))
                .andExpect(jsonPath("$.fiscal.target.taxBasis")
                        .value(closeTo(9_110.629067, 0.01)))
                .andExpect(jsonPath("$.fiscal.target.firstRequiredGrossSale")
                        .value(closeTo(1_084.598698, 0.01)))
                .andExpect(jsonPath("$.fiscal.target.firstGrossSale")
                        .value(closeTo(1_084.598698, 0.01)))
                .andExpect(jsonPath("$.fiscal.target.firstCapitalGainsTax")
                        .value(closeTo(84.598698, 0.01)))
                .andExpect(jsonPath("$.fiscal.target.firstNetProceeds")
                        .value(closeTo(1_000.0, 0.01)))
                .andExpect(jsonPath("$.fiscal.decumulation.target.totalCapitalGainsTax")
                        .value(closeTo(1_015.184381, 0.01)));
    }

    @Test
    void mapsTaxBasisToTheCorrectExistingInvestment() throws Exception {
        String resources = """
                [{
                  "type": "EXISTING_INVESTMENT",
                  "name": "ETF",
                  "currentCapital": 100000,
                  "taxBasis": 70000,
                  "initialMonthlyContribution": 0,
                  "contributionStartAge": null,
                  "contributionEndAge": null,
                  "annualReturnRate": 0,
                  "annualContributionGrowthRate": 0,
                  "availableAtFire": true
                }]
                """;
        String request = simpleFiniteRequest("""
                ,
                "currentCapital": 0,
                "capitalGainsTaxRate": 0.26,
                "annualStampDutyRate": 0,
                "additionalResources": %s
                """.formatted(resources)).replace("\"currentCapital\": 15000,", "");

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fiscal.accumulation.portfolios", hasSize(2)))
                .andExpect(jsonPath("$.fiscal.accumulation.portfolios[1].sourceType")
                        .value("EXISTING_INVESTMENT"))
                .andExpect(jsonPath("$.fiscal.accumulation.portfolios[1].resourceIndex").value(0))
                .andExpect(jsonPath("$.fiscal.accumulation.portfolios[1].initialBalance")
                        .value(100_000.0))
                .andExpect(jsonPath("$.fiscal.accumulation.portfolios[1].initialTaxBasis")
                        .value(70_000.0))
                .andExpect(jsonPath("$.fiscal.decumulation.personal.totalCapitalGainsTax")
                        .value(greaterThan(0.0)));
    }

    @Test
    void reportsFiscalTotalsAndResidualTaxBasis() throws Exception {
        String request = simpleFiniteRequest("""
                ,
                "capitalGainsTaxRate": 0.26,
                "annualStampDutyRate": 0.002,
                "currentTaxBasis": 10500
                """);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fiscal.totals.fireCapitalGainsTax").value(greaterThan(0.0)))
                .andExpect(jsonPath("$.fiscal.totals.fireStampDuty").value(greaterThan(0.0)))
                .andExpect(jsonPath("$.fiscal.totals.totalEstimatedTaxes").value(greaterThan(0.0)))
                .andExpect(jsonPath("$.fiscal.decumulation.personal.finalTaxBasis").isNumber())
                .andExpect(jsonPath("$.fiscal.decumulation.personal.projection[1].closingTaxBasis")
                        .isNumber());
    }

    @Test
    void doesNotChargeAccumulationStampOnUninvestedCash() throws Exception {
        String resources = """
                [{
                  "type": "FUTURE_LUMP_SUM",
                  "name": "Liquidità",
                  "amount": 100000,
                  "amountBasis": "NOMINAL",
                  "receiptAge": 40,
                  "investAfterReceipt": false,
                  "annualReturnRateAfterReceipt": 0
                }]
                """;
        String request = """
                {
                  "method": "FINITE",
                  "currentAge": 40,
                  "fireAge": 41,
                  "fireDurationYears": 1,
                  "monthlyExpenseToday": 0,
                  "annualInflationRate": 0,
                  "annualFireReturnRate": 0,
                  "annualSafeWithdrawalRate": null,
                  "terminalCapitalToday": 0,
                  "currentCapital": 0,
                  "annualAccumulationReturnRate": 0,
                  "annualContributionGrowthRate": 0,
                  "additionalResources": %s
                }
                """.formatted(resources);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fiscal.accumulation.portfolios[1].sourceType")
                        .value("FUTURE_LUMP_SUM"))
                .andExpect(jsonPath("$.fiscal.accumulation.portfolios[1].stampDutyApplicable")
                        .value(false))
                .andExpect(jsonPath("$.fiscal.accumulation.portfolios[1].finalBalance")
                        .value(100_000.0))
                .andExpect(jsonPath("$.fiscal.accumulation.portfolios[1].totalStampDuty")
                        .value(0.0));
    }

    @Test
    void rejectsInvalidFiscalRatesAtTheApiBoundary() throws Exception {
        String request = addFields(baseRequest(), """
                "capitalGainsTaxRate": -0.01,
                "annualStampDutyRate": 1
                """);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("capitalGainsTaxRate")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("annualStampDutyRate")));
    }

    @Test
    void rejectsInvalidMainAndInvestmentTaxBases() throws Exception {
        String resources = """
                [{
                  "type": "EXISTING_INVESTMENT",
                  "currentCapital": 1000,
                  "taxBasis": -1,
                  "initialMonthlyContribution": 0,
                  "contributionStartAge": null,
                  "contributionEndAge": null,
                  "annualReturnRate": 0,
                  "annualContributionGrowthRate": 0,
                  "availableAtFire": true
                }]
                """;
        String request = addFields(baseRequest(), """
                "currentTaxBasis": -1,
                "additionalResources": %s
                """.formatted(resources));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("currentTaxBasis")))
                .andExpect(jsonPath("$.fieldErrors[*].field",
                        hasItem("additionalResources[0].taxBasis")));
    }

    private static String addFields(String request, String fields) {
        int closingBrace = request.lastIndexOf('}');
        return request.substring(0, closingBrace)
                + ",\n" + fields.strip() + "\n"
                + request.substring(closingBrace);
    }

    private static String simpleFiniteRequest(String extraFields) {
        return """
                {
                  "method": "FINITE",
                  "currentAge": 40,
                  "fireAge": 40,
                  "fireDurationYears": 1,
                  "monthlyExpenseToday": 1000,
                  "annualInflationRate": 0,
                  "annualFireReturnRate": 0,
                  "annualSafeWithdrawalRate": null,
                  "terminalCapitalToday": 0,
                  "currentCapital": 15000,
                  "annualAccumulationReturnRate": 0,
                  "annualContributionGrowthRate": 0
                  %s
                }
                """.formatted(extraFields);
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
