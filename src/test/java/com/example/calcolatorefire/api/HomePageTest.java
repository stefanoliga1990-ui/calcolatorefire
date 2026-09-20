package com.example.calcolatorefire.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SpringBootTest
class HomePageTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void servesTheCalculatorHomePage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("href=\"/fonts/InterVariable.woff2?v=4.1\"")))
                .andExpect(content().string(containsString("href=\"/styles-daa4f32601b6.css\"")))
                .andExpect(content().string(containsString("<h1 id=\"page-title\">Simulatore FIRE</h1>")))
                .andExpect(content().string(containsString("Calcola il patrimonio necessario per raggiungere il FIRE, e gli investimenti necessari per raggiungerlo")))
                .andExpect(content().string(not(containsString("Quanto ti serve per raggiungere il FIRE?"))))
                .andExpect(content().string(not(containsString("Pianificazione FIRE, con ipotesi trasparenti"))))
                .andExpect(content().string(not(containsString("Nessun account"))))
                .andExpect(content().string(not(containsString("brand-mark"))))
                .andExpect(content().string(containsString("id=\"fire-form\"")))
                .andExpect(content().string(containsString("id=\"accumulation-chart\"")))
                .andExpect(content().string(containsString("id=\"decumulation-chart\"")))
                .andExpect(content().string(containsString("id=\"method-description\"")))
                .andExpect(content().string(containsString("id=\"swr-field\"")))
                .andExpect(content().string(containsString("data-help=\"currentAge\"")))
                .andExpect(content().string(containsString("data-help=\"personalFinalBalance\"")))
                .andExpect(content().string(containsString("id=\"parameter-help-dialog\"")))
                .andExpect(content().string(containsString(">I tuoi dati<")))
                .andExpect(content().string(containsString(">Il risultato<")))
                .andExpect(content().string(containsString(">Le proiezioni<")))
                .andExpect(content().string(not(containsString("01 ·"))))
                .andExpect(content().string(not(containsString("02 ·"))))
                .andExpect(content().string(not(containsString("03 ·"))))
                .andExpect(content().string(not(containsString("placeholder-number"))))
                .andExpect(content().string(containsString("aumenta il rischio di esaurirlo")))
                .andExpect(content().string(not(containsString("Patrimonio corrente proiettato"))))
                .andExpect(content().string(not(containsString("Margine di sicurezza"))))
                .andExpect(content().string(not(containsString("Shortfall previsto"))))
                .andExpect(content().string(containsString("src=\"/app.js?v=103508d33530\"")))
                .andExpect(content().string(not(containsString("CONSERVATIVE"))));
    }

    @Test
    void servesTheFrontendAssets() throws Exception {
        mockMvc.perform(get("/fonts/InterVariable.woff2").queryParam("v", "4.1"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/styles-daa4f32601b6.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));

        mockMvc.perform(get("/app.js").queryParam("v", "103508d33530"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/javascript"))
                .andExpect(content().string(containsString("/api/v1/fire/calculations")))
                .andExpect(content().string(containsString("attachParameterHelp")))
                .andExpect(content().string(containsString("Valore di riferimento")))
                .andExpect(content().string(containsString("Esempi a confronto")))
                .andExpect(content().string(containsString("360 = 720.000")))
                .andExpect(content().string(containsString("4% = 600.000")))
                .andExpect(content().string(containsString("primo prelievo non interamente coperto")))
                .andExpect(content().string(containsString("renderProjectionCharts")));
    }
}
