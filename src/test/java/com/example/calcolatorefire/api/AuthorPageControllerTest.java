package com.example.calcolatorefire.api;

import static org.hamcrest.Matchers.containsString;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class AuthorPageControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void servesTheCanonicalAuthorPage() throws Exception {
        String page = mockMvc.perform(get("/autore/stefano-liga"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_LANGUAGE, "it-IT"))
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("<title>Stefano Liga | Calcolo FIRE Italia</title>")))
                .andExpect(content().string(containsString(
                        "rel=\"canonical\" href=\"https://simulatorefire.com/autore/stefano-liga\"")))
                .andExpect(content().string(containsString("\"@type\": \"ProfilePage\"")))
                .andExpect(content().string(containsString("\"@type\": \"Person\"")))
                .andExpect(content().string(containsString("<h1>Stefano Liga</h1>")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquals(1, countOccurrences(page, "<h1>"));
        assertEquals(1, countOccurrences(page, "rel=\"canonical\""));
    }

    @Test
    void redirectsAuthorAliasesToTheCanonicalUrl() throws Exception {
        mockMvc.perform(get("/autore/stefano-liga/"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(HttpHeaders.LOCATION, "/autore/stefano-liga"));

        mockMvc.perform(get("/autore/stefano-liga.html"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(HttpHeaders.LOCATION, "/autore/stefano-liga"));

        mockMvc.perform(get("/autore"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(HttpHeaders.LOCATION, "/autore/stefano-liga"));

        mockMvc.perform(get("/autore/"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(HttpHeaders.LOCATION, "/autore/stefano-liga"));
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
