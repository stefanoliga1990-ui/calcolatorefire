package com.example.calcolatorefire.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SpringBootTest
class MethodologyPageTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void servesTheCanonicalMethodologyPage() throws Exception {
        String page = mockMvc.perform(get("/metodologia"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_LANGUAGE, "it-IT"))
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString(
                        "<title>Metodologia del simulatore FIRE | Calcolo FIRE Italia</title>")))
                .andExpect(content().string(containsString(
                        "rel=\"canonical\" href=\"https://simulatorefire.com/metodologia\"")))
                .andExpect(content().string(containsString(
                        "property=\"og:url\" content=\"https://simulatorefire.com/metodologia\"")))
                .andExpect(content().string(containsString("\"@type\": \"Article\"")))
                .andExpect(content().string(containsString("\"@type\": \"BreadcrumbList\"")))
                .andExpect(content().string(containsString(
                        "<h1>Metodologia del simulatore FIRE</h1>")))
                .andExpect(content().string(containsString("A cura di Stefano Liga")))
                .andExpect(content().string(containsString("T_finite =")))
                .andExpect(content().string(containsString("T_swr = W_1 × 12 / SWR")))
                .andExpect(content().string(containsString("C_1 = Gap / F")))
                .andExpect(content().string(containsString("vendita_lorda_richiesta")))
                .andExpect(content().string(containsString("non produce probabilità di successo")))
                .andExpect(content().string(containsString("href=\"/\">Apri il simulatore FIRE</a>")))
                .andExpect(content().string(containsString(
                        "href=\"/editorial-2a6c4e8d.css?v=1.0\"")))
                .andExpect(content().string(not(containsString("noindex"))))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquals(1, countOccurrences(page, "<h1>"));
        assertEquals(1, countOccurrences(page, "rel=\"canonical\""));
    }

    @Test
    void redirectsMethodologyAliasesToTheCanonicalUrl() throws Exception {
        mockMvc.perform(get("/metodologia/"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(HttpHeaders.LOCATION, "/metodologia"));

        mockMvc.perform(get("/metodologia.html"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(HttpHeaders.LOCATION, "/metodologia"));
    }

    private static int countOccurrences(String text, String value) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
    }
}
