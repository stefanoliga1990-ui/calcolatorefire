package com.example.calcolatorefire.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
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
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_LANGUAGE, "it-IT"))
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("href=\"/images/favicon-32x32.png?v=2\"")))
                .andExpect(content().string(containsString("href=\"/images/apple-touch-icon.png?v=2\"")))
                .andExpect(content().string(containsString("<title>Calcolatore FIRE Italia: capitale necessario e PAC.</title>")))
                .andExpect(content().string(containsString("name=\"description\" content=\"Calcola il patrimonio necessario per il FIRE in Italia e il PAC mensile, considerando inflazione, rendimento, plusvalenze e imposta di bollo.\"")))
                .andExpect(content().string(containsString("property=\"og:title\" content=\"Calcolatore FIRE Italia: capitale necessario e PAC.\"")))
                .andExpect(content().string(containsString("property=\"og:description\" content=\"Calcola il patrimonio necessario per il FIRE in Italia e il PAC mensile, considerando inflazione, rendimento, plusvalenze e imposta di bollo.\"")))
                .andExpect(content().string(containsString("property=\"og:url\" content=\"https://simulatorefire.com/\"")))
                .andExpect(content().string(containsString("property=\"og:image\" content=\"https://simulatorefire.com/images/og-simulatore-fire.jpg?v=3\"")))
                .andExpect(content().string(containsString("property=\"og:image:secure_url\" content=\"https://simulatorefire.com/images/og-simulatore-fire.jpg?v=3\"")))
                .andExpect(content().string(containsString("property=\"og:image:width\" content=\"1200\"")))
                .andExpect(content().string(containsString("property=\"og:image:height\" content=\"630\"")))
                .andExpect(content().string(containsString("property=\"og:image:alt\" content=\"Calcolatore FIRE Italia per l'indipendenza finanziaria\"")))
                .andExpect(content().string(containsString("name=\"twitter:card\" content=\"summary_large_image\"")))
                .andExpect(content().string(containsString("name=\"twitter:title\" content=\"Calcolatore FIRE Italia: capitale necessario e PAC.\"")))
                .andExpect(content().string(containsString("name=\"twitter:description\" content=\"Calcola il patrimonio necessario per il FIRE in Italia e il PAC mensile, considerando inflazione, rendimento, plusvalenze e imposta di bollo.\"")))
                .andExpect(content().string(containsString("rel=\"canonical\" href=\"https://simulatorefire.com/\"")))
                .andExpect(content().string(containsString("href=\"/fonts/InterVariable.woff2?v=4.1\"")))
                .andExpect(content().string(containsString("href=\"/styles-7c8e1a4b5d20.css?v=8.3\"")))
                .andExpect(content().string(containsString("<h1 id=\"page-title\">Calcolatore FIRE Italia per l'indipendenza finanziaria.</h1>")))
                .andExpect(content().string(containsString("<p>Stima il patrimonio necessario per vivere di rendita e il PAC mensile per raggiungerlo, considerando le tue ipotesi su inflazione e rendimenti e la fiscalità italiana.</p>")))
                .andExpect(content().string(containsString("aria-label=\"Cosa puoi stimare con il simulatore\"")))
                .andExpect(content().string(containsString("<strong>Capitale FIRE con FINITE o SWR</strong>")))
                .andExpect(content().string(containsString("Stima il patrimonio necessario con una durata definita o un tasso iniziale di prelievo.")))
                .andExpect(content().string(containsString("<strong>PAC mensile per il tuo target</strong>")))
                .andExpect(content().string(containsString("Calcola il versamento mensile in base al capitale già investito e agli anni disponibili.")))
                .andExpect(content().string(containsString("<strong>Fiscalità italiana e risorse aggiuntive</strong>")))
                .andExpect(content().string(containsString("Stima plusvalenze e bollo e considera pensione, rendite e capitali futuri nel tuo scenario.")))
                .andExpect(content().string(not(containsString("Quanto ti serve per raggiungere il FIRE?"))))
                .andExpect(content().string(not(containsString("Pianificazione FIRE, con ipotesi trasparenti"))))
                .andExpect(content().string(not(containsString("Nessun account"))))
                .andExpect(content().string(containsString("class=\"brand-logo\"")))
                .andExpect(content().string(containsString("href=\"/guide\">Guide</a>")))
                .andExpect(content().string(containsString("href=\"/metodologia\">Metodologia</a>")))
                .andExpect(content().string(containsString("Leggi la guida completa al FIRE in Italia")))
                .andExpect(content().string(containsString("Leggi formule, convenzioni e limiti nella metodologia completa")))
                .andExpect(content().string(containsString("src=\"/images/logo-percorso-indipendenza.png?v=2\"")))
                .andExpect(content().string(containsString("id=\"fire-form\"")))
                .andExpect(content().string(containsString("class=\"app-layout is-wizard-view\" id=\"scenario-layout\"")))
                .andExpect(content().string(containsString("class=\"wizard-progress\"")))
                .andExpect(content().string(containsString("aria-label=\"Avanzamento configurazione\"")))
                .andExpect(content().string(containsString("data-wizard-step=\"1\"")))
                .andExpect(content().string(containsString("data-wizard-step=\"2\"")))
                .andExpect(content().string(containsString("data-wizard-step=\"3\"")))
                .andExpect(content().string(containsString("data-wizard-step=\"4\"")))
                .andExpect(content().string(containsString("data-wizard-step=\"5\"")))
                .andExpect(content().string(containsString("data-wizard-step=\"6\"")))
                .andExpect(content().string(containsString("data-wizard-step=\"2\" aria-labelledby=\"wizard-step-2-title\" hidden")))
                .andExpect(content().string(containsString("data-wizard-step=\"6\" aria-labelledby=\"pac-form-title\" hidden")))
                .andExpect(content().string(containsString("id=\"wizard-back-button\" type=\"button\" hidden")))
                .andExpect(content().string(containsString("id=\"wizard-next-button\" type=\"button\"")))
                .andExpect(content().string(containsString("id=\"wizard-step-status\" aria-live=\"polite\"")))
                .andExpect(content().string(containsString("id=\"wizard-edit-banner\" aria-labelledby=\"wizard-edit-title\" hidden")))
                .andExpect(content().string(containsString("id=\"wizard-edit-message\" aria-live=\"polite\"")))
                .andExpect(content().string(containsString("id=\"wizard-cancel-edit-button\" type=\"button\" hidden")))
                .andExpect(content().string(containsString("Torna ai risultati")))
                .andExpect(content().string(containsString("Continua")))
                .andExpect(content().string(containsString("id=\"accumulation-chart\"")))
                .andExpect(content().string(containsString("id=\"decumulation-chart\"")))
                .andExpect(content().string(containsString("id=\"resource-projections\"")))
                .andExpect(content().string(containsString("id=\"resource-chart-grid\"")))
                .andExpect(content().string(containsString("id=\"pac-results-panel\"")))
                .andExpect(content().string(containsString("class=\"result-stack\" id=\"results\" aria-live=\"polite\" hidden")))
                .andExpect(content().string(containsString("id=\"results-view-title\" tabindex=\"-1\"")))
                .andExpect(content().string(containsString(">I risultati del tuo percorso<")))
                .andExpect(content().string(containsString(">PAC e FIRE<")))
                .andExpect(content().string(containsString(">Fase di accumulo PAC<")))
                .andExpect(content().string(containsString(">Fase di decumulo FIRE<")))
                .andExpect(content().string(containsString("id=\"method-description\"")))
                .andExpect(content().string(containsString("name=\"method\" value=\"FINITE\" checked required")))
                .andExpect(content().string(containsString("name=\"method\" value=\"SWR\" required")))
                .andExpect(content().string(containsString(">Durata finita<")))
                .andExpect(content().string(containsString(">Safe Withdrawal Rate<")))
                .andExpect(content().string(containsString("id=\"fire-duration-label\"")))
                .andExpect(content().string(containsString("id=\"fire-duration-description\"")))
                .andExpect(content().string(containsString("class=\"field field-align-label\" data-help=\"fireDurationYears\"")))
                .andExpect(content().string(containsString("class=\"field field-align-label\" data-help=\"monthlyExpenseToday\"")))
                .andExpect(content().string(containsString("id=\"swr-field\"")))
                .andExpect(content().string(containsString("data-help=\"currentAge\"")))
                .andExpect(content().string(containsString("data-help=\"personalFinalBalance\"")))
                .andExpect(content().string(containsString("Patrimonio residuo stimato a fine FIRE")))
                .andExpect(content().string(containsString("id=\"parameter-help-dialog\"")))
                .andExpect(content().string(containsString("Passaggio 1 di 6")))
                .andExpect(content().string(containsString("Passaggio 6 di 6")))
                .andExpect(content().string(containsString("class=\"step-label-logo\"")))
                .andExpect(content().string(containsString(">Il risultato FIRE<")))
                .andExpect(content().string(containsString(">Il risultato PAC<")))
                .andExpect(content().string(containsString("id=\"calculate-fire-button\" type=\"submit\" hidden")))
                .andExpect(content().string(containsString(">Calcola il mio scenario<")))
                .andExpect(content().string(containsString("id=\"calculate-pac-button\" type=\"button\" disabled")))
                .andExpect(content().string(containsString(">Aggiorna FIRE e PAC<")))
                .andExpect(content().string(containsString("id=\"fire-results-content\" hidden")))
                .andExpect(content().string(containsString("id=\"pac-results-content\" hidden")))
                .andExpect(content().string(containsString("class=\"results-actions\"")))
                .andExpect(content().string(containsString(">Le proiezioni<")))
                .andExpect(content().string(containsString("id=\"currentCapital\" name=\"currentCapital\" type=\"number\" min=\"0\" max=\"1000000000000\" step=\"1\" value=\"0\"")))
                .andExpect(content().string(containsString("id=\"annualAccumulationReturnRate\" name=\"annualAccumulationReturnRate\" type=\"number\" min=\"-99.99\" max=\"100\" step=\"0.01\" value=\"5\"")))
                .andExpect(content().string(containsString("id=\"annualContributionGrowthRate\" name=\"annualContributionGrowthRate\" type=\"number\" min=\"-99.99\" max=\"100\" step=\"0.01\" value=\"0\"")))
                .andExpect(content().string(containsString("id=\"capitalGainsTaxRate\" name=\"capitalGainsTaxRate\"")))
                .andExpect(content().string(containsString("step=\"0.01\" value=\"26\" required")))
                .andExpect(content().string(containsString("id=\"annualStampDutyRate\" name=\"annualStampDutyRate\"")))
                .andExpect(content().string(containsString("step=\"0.01\" value=\"0.20\" required")))
                .andExpect(content().string(containsString("id=\"current-tax-basis-field\" data-help=\"currentTaxBasis\" hidden")))
                .andExpect(content().string(containsString("data-tax-basis-toggle=\"main\"")))
                .andExpect(content().string(containsString("id=\"first-gross-sale\"")))
                .andExpect(content().string(containsString("id=\"target-tax-basis\"")))
                .andExpect(content().string(containsString("id=\"total-estimated-taxes\"")))
                .andExpect(content().string(containsString("id=\"accumulation-stamp-duty\"")))
                .andExpect(content().string(containsString("id=\"fire-input-warnings\"")))
                .andExpect(content().string(containsString("id=\"pac-input-warnings\"")))
                .andExpect(content().string(containsString("id=\"resource-input-warnings\"")))
                .andExpect(content().string(containsString("id=\"additional-resources\" aria-labelledby=\"additional-resources-title\" hidden")))
                .andExpect(content().string(containsString("id=\"resource-sync-status\" aria-live=\"polite\"")))
                .andExpect(content().string(containsString("id=\"resource-sync-badge\"")))
                .andExpect(content().string(containsString("id=\"add-resource-button\"")))
                .andExpect(content().string(containsString(">Aggiungi rendita<")))
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
                .andExpect(content().string(containsString("src=\"/app-91e2b4c7a630.js?v=7.9\"")))
                .andExpect(content().string(not(containsString("CONSERVATIVE"))));

        String page = mockMvc.perform(get("/"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertEquals(1L, Pattern.compile("<h1\\b", Pattern.CASE_INSENSITIVE).matcher(page).results().count());
        var benefits = Pattern.compile("(?s)<ul\\b[^>]*id=\"home-benefits\"[^>]*>(.*?)</ul>").matcher(page);
        assertTrue(benefits.find());
        assertEquals(3L, Pattern.compile("<li\\b").matcher(benefits.group(1)).results().count());
        assertTrue(!benefits.group().contains("hidden"));
        int benefitsPosition = page.indexOf("id=\"home-benefits\"");
        assertTrue(page.indexOf("</section>") < benefitsPosition);
        assertTrue(benefitsPosition < page.indexOf("id=\"scenario-layout\""));
        int ageStepPosition = page.indexOf("data-wizard-step=\"1\"");
        int methodPosition = page.indexOf("name=\"method\" value=\"FINITE\"");
        int durationPosition = page.indexOf("id=\"fireDurationYears\"");
        int expensePosition = page.indexOf("id=\"monthlyExpenseToday\"");
        int assumptionsPosition = page.indexOf("data-wizard-step=\"4\"");
        int taxationPosition = page.indexOf("data-wizard-step=\"5\"");
        int pacPosition = page.indexOf("data-wizard-step=\"6\"");
        int fireResultsPosition = page.indexOf("id=\"fire-results-content\"");
        int pacResultsPosition = page.indexOf("id=\"pac-results-panel\"");
        int editButtonPosition = page.indexOf("id=\"edit-button\"");
        int resourcesPosition = page.indexOf("id=\"additional-resources\"");
        assertTrue(ageStepPosition >= 0 && ageStepPosition < methodPosition);
        assertTrue(methodPosition >= 0 && methodPosition < durationPosition);
        assertTrue(methodPosition < expensePosition);
        assertTrue(expensePosition < assumptionsPosition);
        assertTrue(assumptionsPosition < taxationPosition);
        assertTrue(taxationPosition < pacPosition);
        assertTrue(fireResultsPosition >= 0 && fireResultsPosition < pacResultsPosition);
        assertTrue(pacResultsPosition < editButtonPosition);
        assertTrue(editButtonPosition < resourcesPosition);
    }

    @Test
    void explainsTheCalculatorAndFireInVisibleEditorialSections() throws Exception {
        String page = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var overview = Pattern.compile("(?s)<div\\b[^>]*id=\"home-overview\"[^>]*>(.*?)</div>").matcher(page);
        assertTrue(overview.find());
        String editorial = overview.group();
        assertTrue(!editorial.contains("hidden"));
        assertTrue(page.indexOf("id=\"projections\"") < overview.start());
        assertTrue(overview.end() < page.indexOf("id=\"come-funziona\""));
        assertTrue(editorial.contains("<section id=\"cosa-calcola\" aria-labelledby=\"cosa-calcola-title\">"));
        assertTrue(editorial.contains("<h2 id=\"cosa-calcola-title\">Cosa calcola il simulatore FIRE</h2>"));
        for (String concept : new String[] {"patrimonio necessario all'ingresso nel FIRE", "versamento mensile",
                "piano di accumulo (PAC)", "capitale già investito", "anni disponibili", "inflazione e rendimenti",
                "stima semplificata", "plusvalenze", "bollo", "pensione", "rendite", "investimenti esistenti",
                "capitali futuri", "importi e date", "quando sono disponibili", "importi netti stimati da te"}) {
            assertTrue(editorial.contains(concept), "Concetto mancante: " + concept);
        }
        assertTrue(editorial.contains("<section id=\"cos-e-fire\" aria-labelledby=\"cos-e-fire-title\">"));
        assertTrue(editorial.contains("<h2 id=\"cos-e-fire-title\">Cos'è il FIRE?</h2>"));
        assertTrue(editorial.contains("<span lang=\"en\">Financial Independence, Retire Early</span>"));
        assertTrue(editorial.contains("indipendenza finanziaria e pensionamento anticipato"));
        assertTrue(editorial.contains("non richiede di smettere definitivamente di lavorare"));
        assertTrue(editorial.contains("senza garantire risultati"));
        assertTrue(editorial.contains("href=\"/guida/fire-italia\">Leggi la guida completa al FIRE in Italia</a>"));
        assertEquals(1L, Pattern.compile("href=\"/guida/fire-italia\"").matcher(page).results().count());
        mockMvc.perform(get("/guida/fire-italia"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
    }

    @Test
    void explainsTheSimplifiedFireNumberWithAConsistentExample() throws Exception {
        String page = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var section = Pattern.compile("(?s)<section\\b[^>]*id=\"numero-fire\"[^>]*>(.*?)</section>").matcher(page);
        assertTrue(section.find());
        String editorial = section.group();
        assertTrue(!editorial.contains("hidden"));
        assertTrue(page.indexOf("id=\"home-overview\"") < section.start());
        assertTrue(section.end() < page.indexOf("id=\"come-funziona\""));
        assertTrue(editorial.contains("aria-labelledby=\"numero-fire-title\""));
        assertTrue(editorial.contains("<h2 id=\"numero-fire-title\">Numero FIRE: quanto capitale serve?</h2>"));
        assertTrue(editorial.contains("<strong>Capitale FIRE = spesa annua / SWR</strong>"));
        for (String concept : new String[] {"metodo SWR semplificato", "espresso in forma decimale",
                "esempio didattico", "senza inflazione, fiscalità o risorse aggiuntive",
                "non è una raccomandazione personale", "né una garanzia", "non sostituisce la simulazione completa",
                "pensione e altre risorse", "durata", "capitale finale nel metodo FINITE",
                "con SWR verifica", "plusvalenze e bollo", "differire dai 600.000 euro"}) {
            assertTrue(editorial.contains(concept), "Concetto mancante: " + concept);
        }
        assertTrue(editorial.contains("Spesa mensile</dt>"));
        assertTrue(editorial.contains("Spesa annua (mensile × 12)</dt>"));
        assertTrue(editorial.contains("SWR iniziale</dt>"));
        assertTrue(editorial.contains("Capitale semplificato</dt>"));
        BigDecimal monthly = fireExampleValue(editorial, "monthly", "2.000 euro");
        BigDecimal annual = fireExampleValue(editorial, "annual", "24.000 euro");
        BigDecimal swrPercent = fireExampleValue(editorial, "swr", "4%");
        BigDecimal capital = fireExampleValue(editorial, "capital", "600.000 euro");
        assertEquals(new BigDecimal("2000"), monthly);
        assertEquals(new BigDecimal("24000"), annual);
        assertEquals(new BigDecimal("4"), swrPercent);
        assertEquals(new BigDecimal("600000"), capital);
        assertEquals(annual, monthly.multiply(new BigDecimal("12")));
        assertEquals(0, annual.divide(swrPercent.movePointLeft(2)).compareTo(capital));
        assertTrue(editorial.contains("24.000 euro / 0,04 = 600.000 euro"));
        assertTrue(editorial.contains("href=\"/guida/numero-fire\">Approfondisci il numero FIRE e il capitale necessario per vivere di rendita</a>"));
        assertEquals(1L, Pattern.compile("href=\"/guida/numero-fire\"").matcher(page).results().count());
        mockMvc.perform(get("/guida/numero-fire"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
    }

    @Test
    void distinguishesThePacFromTheFireTargetAndComparesTheMethods() throws Exception {
        String page = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var plan = Pattern.compile("(?s)<div\\b[^>]*id=\"home-plan\"[^>]*>(.*?)</div>").matcher(page);
        assertTrue(plan.find());
        String editorial = plan.group(1);
        assertTrue(!plan.group().contains("hidden"));
        assertEquals(2L, Pattern.compile("<section\\b").matcher(editorial).results().count());
        assertEquals(1L, Pattern.compile("<h1\\b", Pattern.CASE_INSENSITIVE).matcher(page).results().count());
        assertTrue(page.indexOf("id=\"numero-fire\"") < page.indexOf("id=\"home-plan\""));
        assertTrue(page.indexOf("id=\"home-plan\"") < page.indexOf("id=\"come-funziona\""));
        assertTrue(editorial.contains("id=\"pac-fire\" aria-labelledby=\"pac-fire-title\""));
        assertTrue(editorial.contains("<h2 id=\"pac-fire-title\">Dal capitale FIRE al PAC mensile</h2>"));
        for (String concept : new String[]{"patrimonio obiettivo", "versamento mensile iniziale", "divario",
                "capitale iniziale", "anni disponibili", "rendimento ipotizzato", "crescita dei versamenti",
                "importi maggiori nei mesi successivi", "non certifica che il PAC sia sostenibile",
                "non garantisce il raggiungimento dell'obiettivo"}) {
            assertTrue(editorial.contains(concept), "Concetto PAC mancante: " + concept);
        }
        assertTrue(editorial.contains("id=\"metodi-fire\" aria-labelledby=\"metodi-fire-title\""));
        assertTrue(editorial.contains("<h2 id=\"metodi-fire-title\">FINITE o SWR: due modi di stimare il capitale</h2>"));
        assertTrue(editorial.contains("<h3>FINITE: durata finita</h3>"));
        assertTrue(editorial.contains("durata definita del FIRE e un eventuale capitale finale"));
        assertTrue(editorial.contains("<h3>SWR: tasso iniziale di prelievo</h3>"));
        assertTrue(editorial.contains("La SWR non è un rendimento"));
        assertTrue(editorial.contains("verifica poi i prelievi sull'orizzonte scelto e segnala eventuali ammanchi"));
        assertTrue(editorial.contains("Un target SWR non garantisce che il patrimonio copra tutta la durata"));
        String[][] guides = {
                {"/guida/pac-per-raggiungere-il-fire", "Approfondisci il PAC per raggiungere il FIRE"},
                {"/guida/metodo-finite-o-swr", "Confronta i metodi FINITE e SWR"},
                {"/guida/regola-del-4-percento-swr", "origine e limiti della regola del 4%"}
        };
        for (String[] guide : guides) {
            assertTrue(editorial.contains("href=\"" + guide[0] + "\">" + guide[1] + "</a>"));
            assertEquals(1L, Pattern.compile("href=\"" + Pattern.quote(guide[0]) + "\"")
                    .matcher(page).results().count());
            mockMvc.perform(get(guide[0]))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("text/html"));
        }
    }

    @Test
    void explainsSimplifiedTaxationAndVisibleSimulationLimits() throws Exception {
        String page = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var tax = Pattern.compile("(?s)<section\\b[^>]*id=\"fiscalita-fire\"[^>]*>(.*?)</section>").matcher(page);
        assertTrue(tax.find());
        String editorial = tax.group();
        assertTrue(!editorial.contains("hidden"));
        assertTrue(page.indexOf("id=\"home-plan\"") < tax.start());
        assertTrue(tax.end() < page.indexOf("id=\"come-funziona\""));
        assertEquals(1L, Pattern.compile("<h1\\b", Pattern.CASE_INSENSITIVE).matcher(page).results().count());
        assertTrue(editorial.contains("aria-labelledby=\"fiscalita-fire-title\""));
        assertTrue(editorial.contains("<h2 id=\"fiscalita-fire-title\">Fiscalità italiana: cosa stima il simulatore</h2>"));
        for (String concept : new String[]{"modello fiscale semplificato", "costo fiscale residuo", "quote ancora detenute", "plusvalenza latente",
                "quota di guadagno realizzata", "prelievo lordo", "spesa netta",
                "26% è l'aliquota ordinaria iniziale del modello, modificabile",
                "non si applica indistintamente a ogni strumento o situazione personale",
                "costo fiscale aggregato non ricostruisce", "bollo del modello riduce il saldo investito",
                "anche senza vendite o plusvalenze", "0,20% annuo", "riduzione mensile equivalente",
                "non il calendario degli addebiti fiscali reali", "importi già netti stimati dall'utente"}) {
            assertTrue(editorial.contains(concept), "Concetto fiscale mancante: " + concept);
        }
        String[][] guides = {
                {"/guida/tassazione-fire-italia", "guida alla tassazione nel FIRE in Italia"},
                {"/guida/plusvalenze-costo-fiscale-fire", "plusvalenze e costo fiscale nel calcolo FIRE"},
                {"/guida/imposta-bollo-investimenti-fire", "imposta di bollo sugli investimenti nel FIRE"}
        };
        for (String[] guide : guides) {
            assertTrue(editorial.contains("href=\"" + guide[0] + "\">" + guide[1] + "</a>"));
            assertEquals(1L, Pattern.compile("href=\"" + Pattern.quote(guide[0]) + "\"")
                    .matcher(page).results().count());
            mockMvc.perform(get(guide[0]))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("text/html"));
        }
        var limits = Pattern.compile("(?s)<section class=\"disclaimer\"[^>]*>(.*?)</section>").matcher(page);
        assertTrue(limits.find());
        assertTrue(tax.end() < limits.start());
        assertTrue(!limits.group().contains("hidden"));
        assertTrue(limits.group().contains("<strong>Questa simulazione ha finalità educative. Non costituisce consulenza finanziaria, fiscale o previdenziale.</strong>"));
        for (String concept : new String[]{"rendimenti costanti", "non simula la volatilità né il rischio di sequenza",
                "ordine dei rendimenti durante i prelievi", "non esprimono probabilità di successo",
                "regimi", "agevolazioni", "differenze personali", "variazioni normative",
                "sostenibilità reale del PAC", "reddito, spese e imprevisti", "il valore calcolato non la verifica"}) {
            assertTrue(limits.group().contains(concept), "Limite mancante: " + concept);
        }
    }

    private static BigDecimal fireExampleValue(String section, String id, String visibleValue) {
        var value = Pattern.compile("<data id=\"fire-example-" + id + "\" value=\"([0-9.]+)\">([^<]+)</data>")
                .matcher(section);
        assertTrue(value.find(), "Valore mancante nell'esempio: " + id);
        assertEquals(visibleValue, value.group(2));
        return new BigDecimal(value.group(1));
    }

    @Test
    void redirectsTheStaticIndexToTheCanonicalHome() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(HttpHeaders.LOCATION, "/"));
    }

    @Test
    void servesSearchEngineDiscoveryFiles() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string(containsString("User-agent: *")))
                .andExpect(content().string(containsString("Allow: /")))
                .andExpect(content().string(containsString(
                        "Sitemap: https://simulatorefire.com/sitemap.xml")));

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(content().string(containsString(
                        "<loc>https://simulatorefire.com/</loc>")))
                .andExpect(content().string(containsString(
                        "<loc>https://simulatorefire.com/metodologia</loc>")));
    }

    @Test
    void servesTheFrontendAssets() throws Exception {
        mockMvc.perform(get("/fonts/InterVariable.woff2").queryParam("v", "4.1"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/styles-7c8e1a4b5d20.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));

        mockMvc.perform(get("/editorial-2a6c4e8d.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"))
                .andExpect(content().string(containsString(".content-layout")));

        mockMvc.perform(get("/images/logo-percorso-indipendenza.png").queryParam("v", "2"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/png"));

        byte[] faviconBytes = mockMvc.perform(get("/images/favicon-32x32.png").queryParam("v", "2"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/png"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage favicon = ImageIO.read(new ByteArrayInputStream(faviconBytes));
        assertEquals(32, favicon.getWidth());
        assertEquals(32, favicon.getHeight());

        byte[] appleTouchIconBytes = mockMvc.perform(get("/images/apple-touch-icon.png").queryParam("v", "2"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/png"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage appleTouchIcon = ImageIO.read(new ByteArrayInputStream(appleTouchIconBytes));
        assertEquals(180, appleTouchIcon.getWidth());
        assertEquals(180, appleTouchIcon.getHeight());

        byte[] openGraphImageBytes = mockMvc.perform(get("/images/og-simulatore-fire.jpg").queryParam("v", "3"))
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
                .andExpect(content().string(containsString("minimo versamento che raggiunge il target fiscale")))
                .andExpect(content().string(containsString("Capitale_finale = B_N_FIRE")))
                .andExpect(content().string(containsString("buildAdditionalResources")))
                .andExpect(content().string(containsString("renderAdditionalResourcesResult")))
                .andExpect(content().string(containsString("includedInLastCalculation")))
                .andExpect(content().string(containsString("availableBalanceAtFire")))
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
                .andExpect(content().string(containsString("scrollToResourceElement")))
                .andExpect(content().string(containsString("captureAdditionalResourcesState")))
                .andExpect(content().string(containsString("refreshResourceCalculationState")))
                .andExpect(content().string(containsString("Inclusa nei risultati")))
                .andExpect(content().string(containsString("setMainTaxBasisMode")))
                .andExpect(content().string(containsString("setResourceTaxBasisMode")))
                .andExpect(content().string(containsString("setFieldLabelText")))
                .andExpect(content().string(containsString("showWizardStep")))
                .andExpect(content().string(containsString("validateWizardStep")))
                .andExpect(content().string(containsString("validateAllWizardSteps")))
                .andExpect(content().string(containsString("showResultView")))
                .andExpect(content().string(containsString("showWizardView")))
                .andExpect(content().string(containsString("enterEditMode")))
                .andExpect(content().string(containsString("captureMainFormState")))
                .andExpect(content().string(containsString("restoreLastCalculatedFormState")))
                .andExpect(content().string(containsString("refreshEditModeState")))
                .andExpect(content().string(containsString("Orizzonte della proiezione FIRE")))
                .andExpect(content().string(containsString("Non modifica la formula SWR base")))
                .andExpect(content().string(containsString("capitalGainsTaxRate: percent")))
                .andExpect(content().string(containsString("annualStampDutyRate: percent")))
                .andExpect(content().string(containsString("data.fiscal.target.selectedTarget")))
                .andExpect(content().string(containsString("data.fiscal.decumulation.personal.projection")))
                .andExpect(content().string(containsString("La rendita che hai aggiunto ha contribuito ad abbassare la rata del PAC")))
                .andExpect(content().string(containsString("torna su e verifica!")))
                .andExpect(content().string(containsString("renderProjectionCharts")));
    }
}
