package com.example.calcolatorefire.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;

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
                .andExpect(content().string(containsString("href=\"/images/favicon-32x32.png?v=1\"")))
                .andExpect(content().string(containsString("href=\"/images/apple-touch-icon.png?v=1\"")))
                .andExpect(content().string(containsString("property=\"og:title\" content=\"Simulatore FIRE\"")))
                .andExpect(content().string(containsString("property=\"og:url\" content=\"https://simulatorefire.com/\"")))
                .andExpect(content().string(containsString("property=\"og:image\" content=\"https://simulatorefire.com/images/og-simulatore-fire.jpg?v=2\"")))
                .andExpect(content().string(containsString("property=\"og:image:secure_url\" content=\"https://simulatorefire.com/images/og-simulatore-fire.jpg?v=2\"")))
                .andExpect(content().string(containsString("property=\"og:image:width\" content=\"1200\"")))
                .andExpect(content().string(containsString("property=\"og:image:height\" content=\"630\"")))
                .andExpect(content().string(containsString("name=\"twitter:card\" content=\"summary_large_image\"")))
                .andExpect(content().string(containsString("rel=\"canonical\" href=\"https://simulatorefire.com/\"")))
                .andExpect(content().string(containsString("href=\"/fonts/InterVariable.woff2?v=4.1\"")))
                .andExpect(content().string(containsString("href=\"/styles-7c8e1a4b5d20.css?v=5.7\"")))
                .andExpect(content().string(containsString("<h1 id=\"page-title\">Simulatore FIRE</h1>")))
                .andExpect(content().string(containsString("Stima il patrimonio necessario e il PAC mensile per raggiungere il tuo obiettivo FIRE.")))
                .andExpect(content().string(not(containsString("Quanto ti serve per raggiungere il FIRE?"))))
                .andExpect(content().string(not(containsString("Pianificazione FIRE, con ipotesi trasparenti"))))
                .andExpect(content().string(not(containsString("Nessun account"))))
                .andExpect(content().string(containsString("class=\"brand-logo\"")))
                .andExpect(content().string(containsString("src=\"/images/logo-percorso-indipendenza.png?v=1\"")))
                .andExpect(content().string(containsString("id=\"fire-form\"")))
                .andExpect(content().string(containsString("id=\"accumulation-chart\"")))
                .andExpect(content().string(containsString("id=\"decumulation-chart\"")))
                .andExpect(content().string(containsString("id=\"resource-projections\"")))
                .andExpect(content().string(containsString("id=\"resource-chart-grid\"")))
                .andExpect(content().string(containsString("id=\"pac-results-panel\"")))
                .andExpect(content().string(containsString(">PAC e FIRE<")))
                .andExpect(content().string(containsString(">Fase di accumulo PAC<")))
                .andExpect(content().string(containsString(">Fase di decumulo FIRE<")))
                .andExpect(content().string(containsString("id=\"method-description\"")))
                .andExpect(content().string(containsString("id=\"swr-field\"")))
                .andExpect(content().string(containsString("data-help=\"currentAge\"")))
                .andExpect(content().string(containsString("data-help=\"personalFinalBalance\"")))
                .andExpect(content().string(containsString("Patrimonio residuo stimato a fine FIRE")))
                .andExpect(content().string(containsString("id=\"parameter-help-dialog\"")))
                .andExpect(content().string(containsString(">I dati FIRE<")))
                .andExpect(content().string(containsString(">I dati PAC<")))
                .andExpect(content().string(containsString("class=\"step-label-logo\"")))
                .andExpect(content().string(containsString(">Il risultato FIRE<")))
                .andExpect(content().string(containsString(">Il risultato PAC<")))
                .andExpect(content().string(containsString("id=\"calculate-fire-button\" type=\"submit\"")))
                .andExpect(content().string(containsString(">Calcola FIRE e PAC<")))
                .andExpect(content().string(containsString("id=\"calculate-pac-button\" type=\"button\" disabled")))
                .andExpect(content().string(containsString(">Ricalcola solo il PAC<")))
                .andExpect(content().string(containsString("id=\"fire-results-content\" hidden")))
                .andExpect(content().string(containsString("id=\"pac-results-content\" hidden")))
                .andExpect(content().string(containsString(">Le proiezioni<")))
                .andExpect(content().string(containsString("id=\"currentCapital\" name=\"currentCapital\" type=\"number\" min=\"0\" max=\"1000000000000\" step=\"1\" value=\"0\"")))
                .andExpect(content().string(containsString("id=\"annualAccumulationReturnRate\" name=\"annualAccumulationReturnRate\" type=\"number\" min=\"-99.99\" max=\"100\" step=\"0.01\" value=\"5\"")))
                .andExpect(content().string(containsString("id=\"annualContributionGrowthRate\" name=\"annualContributionGrowthRate\" type=\"number\" min=\"-99.99\" max=\"100\" step=\"0.01\" value=\"0\"")))
                .andExpect(content().string(containsString("id=\"fire-input-warnings\"")))
                .andExpect(content().string(containsString("id=\"pac-input-warnings\"")))
                .andExpect(content().string(containsString("id=\"resource-input-warnings\"")))
                .andExpect(content().string(containsString("id=\"additional-resources\"")))
                .andExpect(content().string(containsString("id=\"add-resource-button\"")))
                .andExpect(content().string(containsString(">Aggiungi risorsa<")))
                .andExpect(content().string(containsString("data-resource-type=\"EXISTING_INVESTMENT\"")))
                .andExpect(content().string(containsString("data-resource-type=\"PERIODIC_INCOME\"")))
                .andExpect(content().string(containsString("data-resource-type=\"FUTURE_LUMP_SUM\"")))
                .andExpect(content().string(containsString("id=\"calculate-resources-button\"")))
                .andExpect(content().string(containsString(">Ricalcola FIRE e PAC<")))
                .andExpect(content().string(containsString("id=\"additional-resources-result\"")))
                .andExpect(content().string(not(containsString("01 ·"))))
                .andExpect(content().string(not(containsString("02 ·"))))
                .andExpect(content().string(not(containsString("03 ·"))))
                .andExpect(content().string(not(containsString("placeholder-number"))))
                .andExpect(content().string(containsString("aumenta il rischio di esaurirlo")))
                .andExpect(content().string(not(containsString("Patrimonio corrente proiettato"))))
                .andExpect(content().string(not(containsString("Margine di sicurezza"))))
                .andExpect(content().string(not(containsString("Shortfall previsto"))))
                .andExpect(content().string(containsString("src=\"/app-91e2b4c7a630.js?v=5.4\"")))
                .andExpect(content().string(not(containsString("CONSERVATIVE"))));
    }

    @Test
    void servesTheFrontendAssets() throws Exception {
        mockMvc.perform(get("/fonts/InterVariable.woff2").queryParam("v", "4.1"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/styles-7c8e1a4b5d20.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));

        mockMvc.perform(get("/images/logo-percorso-indipendenza.png").queryParam("v", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/png"));

        byte[] faviconBytes = mockMvc.perform(get("/images/favicon-32x32.png").queryParam("v", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/png"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage favicon = ImageIO.read(new ByteArrayInputStream(faviconBytes));
        assertEquals(32, favicon.getWidth());
        assertEquals(32, favicon.getHeight());

        byte[] appleTouchIconBytes = mockMvc.perform(get("/images/apple-touch-icon.png").queryParam("v", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/png"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage appleTouchIcon = ImageIO.read(new ByteArrayInputStream(appleTouchIconBytes));
        assertEquals(180, appleTouchIcon.getWidth());
        assertEquals(180, appleTouchIcon.getHeight());

        byte[] openGraphImageBytes = mockMvc.perform(get("/images/og-simulatore-fire.jpg").queryParam("v", "2"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/jpeg"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage openGraphImage = ImageIO.read(new ByteArrayInputStream(openGraphImageBytes));
        assertEquals(1200, openGraphImage.getWidth());
        assertEquals(630, openGraphImage.getHeight());
        assertTrue(openGraphImageBytes.length < 300_000);

        mockMvc.perform(get("/app-91e2b4c7a630.js"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/javascript"))
                .andExpect(content().string(containsString("/api/v1/fire/calculations")))
                .andExpect(content().string(containsString("attachParameterHelp")))
                .andExpect(content().string(containsString("Valore di riferimento")))
                .andExpect(content().string(containsString("Esempi a confronto")))
                .andExpect(content().string(containsString("Formula utilizzata")))
                .andExpect(content().string(containsString("Significato dei simboli")))
                .andExpect(content().string(containsString("T_finite =")))
                .andExpect(content().string(containsString("T_SWR =")))
                .andExpect(content().string(containsString("Gap = max")))
                .andExpect(content().string(containsString("Capitale_finale = B_N_FIRE")))
                .andExpect(content().string(containsString("buildAdditionalResources")))
                .andExpect(content().string(containsString("renderAdditionalResourcesResult")))
                .andExpect(content().string(containsString("includedInLastCalculation")))
                .andExpect(content().string(containsString("availableExistingInvestmentsFinalBalance")))
                .andExpect(content().string(containsString("360 = 720.000")))
                .andExpect(content().string(containsString("4% = 600.000")))
                .andExpect(content().string(containsString("primo prelievo non interamente coperto")))
                .andExpect(content().string(containsString("MAX_ADDITIONAL_RESOURCES = 100")))
                .andExpect(content().string(containsString("updateInputWarnings")))
                .andExpect(content().string(containsString("updateResourceWarnings")))
                .andExpect(content().string(containsString("La SWR supera il 6%")))
                .andExpect(content().string(containsString("renderResourceProjectionCharts")))
                .andExpect(content().string(containsString("resourceChartDefinition")))
                .andExpect(content().string(containsString("renderResourcePacImpactMessages")))
                .andExpect(content().string(containsString("La rendita che hai aggiunto ha contribuito ad abbassare la rata del PAC")))
                .andExpect(content().string(containsString("torna su e verifica!")))
                .andExpect(content().string(containsString("renderProjectionCharts")));
    }
}
