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
                .andExpect(content().string(containsString("Quanto ti serve per raggiungere il FIRE?")))
                .andExpect(content().string(containsString("id=\"fire-form\"")))
                .andExpect(content().string(containsString("id=\"accumulation-chart\"")))
                .andExpect(content().string(containsString("id=\"decumulation-chart\"")))
                .andExpect(content().string(containsString("id=\"method-description\"")))
                .andExpect(content().string(containsString("id=\"swr-field\" hidden")))
                .andExpect(content().string(containsString("aumenta il rischio di esaurirlo")))
                .andExpect(content().string(not(containsString("Patrimonio corrente proiettato"))))
                .andExpect(content().string(not(containsString("Margine di sicurezza"))))
                .andExpect(content().string(not(containsString("Shortfall previsto"))))
                .andExpect(content().string(containsString("src=\"/app.js?v=1a23273d6fcc\"")))
                .andExpect(content().string(not(containsString("CONSERVATIVE"))));
    }

    @Test
    void servesTheFrontendAssets() throws Exception {
        mockMvc.perform(get("/styles.css").queryParam("v", "2a9a9744199e"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));

        mockMvc.perform(get("/app.js").queryParam("v", "1a23273d6fcc"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/javascript"))
                .andExpect(content().string(containsString("/api/v1/fire/calculations")))
                .andExpect(content().string(containsString("primo prelievo non interamente coperto")))
                .andExpect(content().string(containsString("renderProjectionCharts")));
    }
}
