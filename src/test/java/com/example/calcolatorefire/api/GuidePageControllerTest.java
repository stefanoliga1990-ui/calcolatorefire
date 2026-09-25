package com.example.calcolatorefire.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class GuidePageControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void servesAnExistingGuideAtItsCanonicalRoute() throws Exception {
        mockMvc.perform(get("/guida/test-generatore"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_LANGUAGE, "it-IT"))
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("<h1>Fixture guida</h1>")));
    }

    @Test
    void servesTheGuideIndexAndRedirectsItsAliases() throws Exception {
        mockMvc.perform(get("/guide"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_LANGUAGE, "it-IT"))
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("<h1>Guide FIRE e indipendenza finanziaria</h1>")))
                .andExpect(content().string(containsString("href=\"/guida/fire-italia\"")));

        mockMvc.perform(get("/guide/"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(HttpHeaders.LOCATION, "/guide"));

        mockMvc.perform(get("/guide.html"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(HttpHeaders.LOCATION, "/guide"));
    }

    @Test
    void redirectsAliasesToTheCanonicalRoute() throws Exception {
        mockMvc.perform(get("/guida/test-generatore/"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(HttpHeaders.LOCATION, "/guida/test-generatore"));

        mockMvc.perform(get("/guida/test-generatore.html"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(HttpHeaders.LOCATION, "/guida/test-generatore"));
    }

    @Test
    void returnsNotFoundForMissingOrMalformedGuides() throws Exception {
        mockMvc.perform(get("/guida/non-esiste"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/guida/non-esiste.html"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/guida/non--valida"))
                .andExpect(status().isNotFound());
    }
}
