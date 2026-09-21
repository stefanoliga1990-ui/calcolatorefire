const form = document.querySelector("#fire-form");
const calculateFireButton = document.querySelector("#calculate-fire-button");
const calculatePacButton = document.querySelector("#calculate-pac-button");
const resetButton = document.querySelector("#reset-button");
const editButton = document.querySelector("#edit-button");
const errorBox = document.querySelector("#form-error");
const pacErrorBox = document.querySelector("#pac-form-error");
const fireResultsPlaceholder = document.querySelector("#fire-results-placeholder");
const fireResultsContent = document.querySelector("#fire-results-content");
const pacResultsPlaceholder = document.querySelector("#pac-results-placeholder");
const pacResultsContent = document.querySelector("#pac-results-content");
const projections = document.querySelector("#projections");
const addResourceButton = document.querySelector("#add-resource-button");
const resourceTypePicker = document.querySelector("#resource-type-picker");
const resourcesEmpty = document.querySelector("#resources-empty");
const resourcesList = document.querySelector("#additional-resources-list");
const resourcesActions = document.querySelector("#resources-actions");
const resourcesResult = document.querySelector("#additional-resources-result");
const calculateResourcesButton = document.querySelector("#calculate-resources-button");
const resourceErrorBox = document.querySelector("#resource-form-error");

let chartCleanups = { accumulation: null, decumulation: null };
let renderedMethod = null;
let lastFireRequest = null;
let resourceCounter = 0;

const currency = new Intl.NumberFormat("it-IT", {
    style: "currency",
    currency: "EUR",
    maximumFractionDigits: 0
});

const methodLabels = {
    FINITE: "Durata finita",
    SWR: "SWR"
};

const methodDescriptions = {
    FINITE: "Calcola il capitale per finanziare la spesa mensile per la durata FIRE scelta.",
    SWR: "Calcola il capitale dalla spesa annua e dal tasso di prelievo scelto; la proiezione verifica se copre tutta la durata FIRE."
};

const finiteTargetFormula = {
    expressions: [
        "D_k = max(0, W_k − R_k)",
        "T_N = max(0, L_FIRE − K_terminale)",
        "T_(k−1) = max(0, D_k − K_k + T_k ÷ (1 + r_f,m))",
        "T_finite = T_0"
    ],
    symbols: [
        ["T_finite", "target nominale calcolato con il metodo Durata finita"],
        ["W_k", "spesa lorda nominale del mese FIRE k"],
        ["R_k", "rendite periodiche utilizzabili nel mese k"],
        ["D_k", "prelievo netto richiesto al portafoglio nel mese k"],
        ["K_k", "capitali una tantum disponibili all’inizio del mese k"],
        ["K_terminale", "capitale una tantum ricevuto al confine finale"],
        ["r_f,m", "rendimento nominale mensile equivalente durante il FIRE"],
        ["L_FIRE", "capitale finale desiderato in valore nominale alla fine del FIRE"]
    ]
};

const swrTargetFormula = {
    expressions: [
        "T_SWR,base = W₁ × 12 ÷ SWR",
        "T_SWR = min(T_SWR,base, capitale ponte + riserva del regime stabile)"
    ],
    symbols: [
        ["T_SWR,base", "target calcolato ignorando le risorse aggiuntive"],
        ["T_SWR", "target selezionato dopo rendite e capitali futuri"],
        ["W₁", "prima spesa mensile lorda nominale all’ingresso nel FIRE"],
        ["12", "numero di mesi usato per trasformare il prelievo mensile in spesa annua"],
        ["SWR", "tasso annuo di prelievo iniziale, espresso in forma decimale"],
        ["capitale ponte", "capitale necessario prima dell’inizio del regime stabile"],
        ["riserva", "fabbisogno mensile stabile annualizzato e diviso per la SWR"]
    ]
};

const selectedTargetFormulas = {
    FINITE: {
        expressions: [...finiteTargetFormula.expressions, "T_oggi = T_finite ÷ (1 + i_m)^N_acc"],
        symbols: [
            ...finiteTargetFormula.symbols,
            ["T_oggi", "equivalente del target in euro di oggi"],
            ["i_m", "tasso mensile equivalente dell’inflazione"],
            ["N_acc", "numero di mesi dall’età attuale all’ingresso nel FIRE"]
        ]
    },
    SWR: {
        expressions: [...swrTargetFormula.expressions, "T_oggi = T_SWR ÷ (1 + i_m)^N_acc"],
        symbols: [
            ...swrTargetFormula.symbols,
            ["T_oggi", "equivalente del target in euro di oggi"],
            ["i_m", "tasso mensile equivalente dell’inflazione"],
            ["N_acc", "numero di mesi dall’età attuale all’ingresso nel FIRE"]
        ]
    }
};

const contributionFormula = {
    expressions: [
        "C₁ = Gap ÷ F",
        "Gap = max(0, T_target − FV_disponibile)",
        "F = [(1 + r_a,m)^N_acc − (1 + g_m)^N_acc] ÷ (r_a,m − g_m)",
        "Se r_a,m = g_m:  F = N_acc × (1 + r_a,m)^(N_acc − 1)"
    ],
    symbols: [
        ["C₁", "PAC del primo mese, versato a fine mese"],
        ["Gap", "capitale che i nuovi versamenti devono ancora costruire"],
        ["F", "fattore di capitalizzazione dei versamenti mensili"],
        ["T_target", "patrimonio necessario secondo il metodo selezionato: T_finite oppure T_SWR"],
        ["FV_disponibile", "patrimonio principale e risorse aggiuntive disponibili all’ingresso nel FIRE, prima del nuovo PAC"],
        ["r_a,m", "rendimento mensile equivalente nella fase di accumulo"],
        ["g_m", "crescita mensile equivalente del PAC"],
        ["N_acc", "numero di mesi disponibili per l’accumulo"]
    ]
};

const firstWithdrawalFormula = {
    expressions: ["W₁ = S₀ × (1 + i_m)^N_acc"],
    symbols: [
        ["W₁", "primo prelievo mensile nominale all’ingresso nel FIRE"],
        ["S₀", "spesa mensile desiderata espressa in euro di oggi"],
        ["i_m", "tasso mensile equivalente dell’inflazione"],
        ["N_acc", "numero di mesi dall’età attuale all’ingresso nel FIRE"]
    ]
};

const totalContributionsFormula = {
    expressions: ["Versamenti_totali = Σ da j = 1 a N_acc di [C₁ × (1 + g_m)^(j − 1)]"],
    symbols: [
        ["Versamenti_totali", "somma nominale di tutti i nuovi versamenti PAC"],
        ["j", "numero progressivo del mese di accumulo"],
        ["C₁", "PAC del primo mese"],
        ["g_m", "crescita mensile equivalente del PAC"],
        ["N_acc", "numero totale di mesi di accumulo"]
    ]
};

const personalFinalBalanceFormula = {
    expressions: [
        "B₀ = max(T_target, B_acc)",
        "D_k = max(0, W_k − R_k)",
        "A_k = min(B_(k−1) + K_k, D_k)",
        "B_k = max(0, [B_(k−1) + K_k − A_k] × (1 + r_f,m))",
        "Capitale_finale = B_N_FIRE"
    ],
    symbols: [
        ["B₀", "capitale personale disponibile all’inizio del FIRE"],
        ["T_target", "patrimonio necessario secondo il metodo selezionato"],
        ["B_acc", "patrimonio effettivamente raggiunto al termine dell’accumulo"],
        ["k", "numero progressivo del mese FIRE"],
        ["W_k", "spesa lorda nominale nel mese k"],
        ["R_k", "rendite che riducono il fabbisogno nel mese k"],
        ["D_k", "prelievo netto programmato"],
        ["K_k", "capitali una tantum ricevuti all’inizio del mese k"],
        ["A_k", "prelievo effettivamente coperto nel mese k"],
        ["B_k", "capitale alla fine del mese k"],
        ["r_f,m", "rendimento nominale mensile equivalente durante il FIRE"],
        ["N_FIRE", "numero totale di mesi della durata FIRE"]
    ]
};

const parameterHelp = {
    currentAge: {
        title: "Età attuale",
        description: "È l'età da cui parte la simulazione. Insieme all'età FIRE determina quanti mesi hai a disposizione per accumulare capitale.",
        reference: "Inserisci gli anni compiuti oggi: non serve usare un valore medio o stimato."
    },
    fireAge: {
        title: "Età di ingresso nel FIRE",
        description: "È l'età alla quale termina l'accumulo e iniziano i prelievi dal patrimonio.",
        reference: "Scegli un obiettivo realistico e confronta anche uno scenario posticipato di 2–5 anni per misurare quanto cambia il PAC."
    },
    fireDurationYears: {
        title: "Durata del FIRE",
        description: "Indica per quanti anni la proiezione deve finanziare i prelievi. Incide sul target a durata finita e verifica la sostenibilità del target SWR.",
        reference: "Controllo pratico: età FIRE più durata dovrebbe arrivare almeno all'età fino alla quale vuoi essere coperto, spesso 90–100 anni."
    },
    monthlyExpenseToday: {
        title: "Spesa mensile desiderata oggi",
        description: "È il tenore di vita mensile che vuoi finanziare, espresso con il potere d'acquisto di oggi. Il calcolatore lo rivaluta con l'inflazione fino all'ingresso nel FIRE.",
        reference: "Usa la media degli ultimi 12 mesi e aggiungi le spese annuali o irregolari divise per 12. Evita una media nazionale: il dato utile è la tua spesa reale."
    },
    method: {
        title: "Metodo di calcolo",
        description: "Durata finita calcola il capitale necessario per un numero preciso di anni e un eventuale capitale finale. SWR divide la prima spesa annua per il tasso di prelievo scelto e poi ne verifica la durata nella proiezione.",
        reference: "Confronta entrambi i metodi quando vuoi distinguere un obiettivo legato a una durata precisa da una regola di prelievo sintetica.",
        examples: [
            {
                title: "Durata finita",
                text: "Esempio semplificato: 2.000 € al mese per 30 anni, rendimento reale 0% e capitale finale 0 €. Il target finanzia 360 prelievi: 2.000 € × 360 = 720.000 €. Con un rendimento reale positivo il target calcolato sarebbe più basso."
            },
            {
                title: "Safe Withdrawal Rate",
                text: "Con la stessa spesa di 2.000 € al mese e una SWR del 4%, il target è 2.000 € × 12 ÷ 4% = 600.000 €. La durata non entra direttamente nella formula: la proiezione verifica poi se 600.000 € coprono tutti gli anni scelti."
            }
        ]
    },
    annualInflationRate: {
        title: "Inflazione annua",
        description: "Serve a trasformare la spesa di oggi nei prelievi nominali futuri e a esprimere i risultati anche in euro di oggi.",
        reference: "Il 2% è il riferimento di lungo periodo della BCE. Puoi affiancare uno scenario più prudente al 3%.",
        source: { label: "Obiettivo di inflazione BCE", url: "https://www.ecb.europa.eu/mopo/strategy/strategy-review/html/price-stability-objective.en.html" }
    },
    annualFireReturnRate: {
        title: "Rendimento annuo nel FIRE",
        description: "È il rendimento nominale medio ipotizzato durante i prelievi, al netto dei costi ricorrenti e prima delle imposte personali.",
        reference: "Non esiste un valore universale. Per un controllo prudente confronta 3%, 4% e 5%. Le previsioni di mercato cambiano nel tempo e il portafoglio in decumulo può rendere meno di uno azionario.",
        source: { label: "Previsioni dei rendimenti Vanguard", url: "https://corporate.vanguard.com/content/corporatesite/us/en/corp/vemo/vemo-return-forecasts.html" }
    },
    annualSafeWithdrawalRate: {
        title: "Safe Withdrawal Rate",
        description: "È la percentuale del patrimonio prelevata nel primo anno. L'importo viene poi adeguato all'inflazione. Una percentuale più alta abbassa il target ma aumenta il rischio di esaurimento.",
        reference: "Come scenari iniziali confronta 3%, 3,5% e 4%. La ricerca Morningstar 2025 stima il 3,9% per 30 anni e probabilità di successo del 90%; orizzonti FIRE più lunghi richiedono maggiore prudenza.",
        source: { label: "Ricerca Morningstar sulla SWR", url: "https://www.morningstar.com/retirement/whats-safe-retirement-withdrawal-rate-2026" }
    },
    terminalCapitalToday: {
        title: "Capitale finale desiderato",
        description: "È il patrimonio che vuoi conservare alla fine del periodo FIRE, espresso in euro di oggi. È usato solo dal metodo Durata finita.",
        reference: "Usa 0 € se accetti di consumare il capitale nello scenario medio. Inserisci una riserva reale specifica se vuoi lasciare un'eredità o mantenere un cuscinetto finale."
    },
    currentCapital: {
        title: "Patrimonio investito oggi",
        description: "È il capitale già investito che partecipa al piano di accumulo e sul quale si aggiungeranno i versamenti del PAC.",
        reference: "Inserisci solo il patrimonio realmente destinato al FIRE. Escludi fondo di emergenza, abitazione e somme che prevedi di spendere prima del FIRE."
    },
    annualAccumulationReturnRate: {
        title: "Rendimento annuo in accumulo",
        description: "È il rendimento nominale medio ipotizzato prima del FIRE, al netto dei costi ricorrenti e prima delle imposte personali.",
        reference: "Confronta almeno 4%, 5% e 6%. Le attuali previsioni Vanguard per ampi mercati azionari sono circa 4,2–6,5% nominali prima di costi, imposte e inflazione; il rendimento del tuo portafoglio può essere diverso.",
        source: { label: "Previsioni dei rendimenti Vanguard", url: "https://corporate.vanguard.com/content/corporatesite/us/en/corp/vemo/vemo-return-forecasts.html" }
    },
    annualContributionGrowthRate: {
        title: "Crescita annua del PAC",
        description: "Indica di quanto aumentano i versamenti nel tempo. Il valore viene convertito in una crescita mensile equivalente.",
        reference: "Usa 0% se vuoi un PAC costante. Il 2% simula un aumento vicino al riferimento d'inflazione BCE, ma usalo solo se prevedi che il reddito permetta davvero di aumentare i versamenti."
    },
    resourceName: {
        title: "Nome della risorsa",
        description: "Serve a riconoscere questa risorsa nei dati e nei risultati. Non modifica il calcolo.",
        reference: "Usa un nome breve e riconoscibile, per esempio PAC ETF, affitto netto o pensione."
    },
    resourceCurrentCapital: {
        title: "Patrimonio già investito",
        description: "È il saldo attuale di questo investimento separato dal patrimonio principale.",
        reference: "Non includere qui somme già inserite in Patrimonio investito oggi."
    },
    resourceMonthlyContribution: {
        title: "Versamento mensile già programmato",
        description: "È il primo versamento futuro del PAC esistente e viene accreditato alla fine del mese.",
        reference: "Inserisci 0 € se non effettuerai altri versamenti su questo investimento."
    },
    resourceContributionStartAge: {
        title: "Età di inizio dei versamenti",
        description: "È l'età, inclusa, dalla quale parte il versamento mensile dell'investimento esistente.",
        reference: "Deve essere compresa tra l'età attuale e l'età FIRE. Lasciala vuota se il versamento è 0 €."
    },
    resourceContributionEndAge: {
        title: "Età di fine dei versamenti",
        description: "È il confine escluso dei versamenti: al raggiungimento di questa età il PAC esistente si interrompe.",
        reference: "Deve essere successiva all'età iniziale e non oltre l'età FIRE. Lasciala vuota se il versamento è 0 €."
    },
    resourceReturnRate: {
        title: "Rendimento annuo della risorsa",
        description: "È il total return nominale annuo di questo investimento, al netto dei costi ricorrenti e prima delle imposte personali.",
        reference: "Confronta più scenari. Se il rendimento comprende già cedole o dividendi reinvestiti, non aggiungerli anche come rendita."
    },
    resourceContributionGrowthRate: {
        title: "Crescita annua dei versamenti",
        description: "Indica come cresce nel tempo il PAC già esistente. Il calcolo usa il tasso mensile equivalente.",
        reference: "Usa 0% per mantenere costante il versamento."
    },
    resourceAvailableAtFire: {
        title: "Disponibile all'ingresso nel FIRE",
        description: "Se selezionato, il saldo dell'investimento contribuirà al patrimonio FIRE e ridurrà il nuovo PAC necessario.",
        reference: "Disattivalo se il capitale ha un altro scopo o non potrà essere usato per finanziare il FIRE."
    },
    resourceMonthlyIncome: {
        title: "Importo mensile netto di oggi",
        description: "È la rendita mensile al netto delle imposte stimate, espressa con il potere d'acquisto di oggi.",
        reference: "Per un affitto considera anche periodi di sfitto, manutenzione e costi; per una pensione usa una stima netta."
    },
    resourceIncomeGrowthRate: {
        title: "Crescita annua della rendita",
        description: "Indica come viene rivalutata la rendita nel tempo.",
        reference: "Usa 0% per una rendita nominalmente fissa; usa l'inflazione ipotizzata solo se prevedi un'effettiva indicizzazione."
    },
    resourceStartAge: {
        title: "Età di inizio della rendita",
        description: "È l'età, inclusa, dalla quale la rendita mensile diventa disponibile.",
        reference: "Per una rendita già attiva inserisci l'età attuale."
    },
    resourceEndAge: {
        title: "Età di fine della rendita",
        description: "È l'età, esclusa, dalla quale la rendita non viene più percepita.",
        reference: "Lasciala vuota se la rendita prosegue per tutto l'orizzonte simulato."
    },
    resourceInvestBeforeFire: {
        title: "Investi prima del FIRE",
        description: "Le mensilità ricevute prima del FIRE vengono versate nel portafoglio principale a fine mese e riducono il nuovo PAC necessario.",
        reference: "Se non prevedi di investire questa entrata prima del FIRE, lascia l'opzione disattivata."
    },
    resourceOffsetDuringFire: {
        title: "Usa durante il FIRE",
        description: "Le mensilità ricevute durante il FIRE riducono il prelievo richiesto al patrimonio nello stesso mese.",
        reference: "Attivala se la rendita sarà destinata alle spese durante il FIRE."
    },
    resourceLumpAmount: {
        title: "Importo del capitale futuro",
        description: "È la somma che prevedi di ricevere una sola volta.",
        reference: "Inserisci una stima prudente e scegli sotto se l'importo è espresso in euro di oggi o nominali."
    },
    resourceAmountBasis: {
        title: "Valore dell'importo",
        description: "Euro di oggi rivaluta l'importo con l'inflazione fino alla ricezione; euro nominali usa esattamente la cifra inserita.",
        reference: "Usa euro di oggi quando ragioni in potere d'acquisto corrente."
    },
    resourceReceiptAge: {
        title: "Età di ricezione",
        description: "È l'età alla quale il capitale diventa disponibile. Il momento determina se riduce il PAC o finanzia la fase FIRE.",
        reference: "Deve rientrare tra l'età attuale e la fine dell'orizzonte FIRE."
    },
    resourceInvestAfterReceipt: {
        title: "Investi dopo la ricezione",
        description: "Se il capitale arriva prima del FIRE, questa opzione gli permette di maturare il rendimento indicato fino all'ingresso nel FIRE.",
        reference: "Durante il FIRE il capitale confluisce comunque nel saldo al momento della ricezione."
    },
    resourceReturnAfterReceipt: {
        title: "Rendimento dopo la ricezione",
        description: "È il rendimento nominale annuo applicato al capitale ricevuto prima del FIRE quando scegli di investirlo.",
        reference: "Usa un rendimento coerente con lo strumento nel quale prevedi di investire la somma."
    },
    resourceInvestedIncomeResult: {
        title: "Rendite reinvestite prima del FIRE",
        description: "È la somma nominale delle entrate periodiche versate nel portafoglio principale durante l'accumulo, senza i rendimenti maturati.",
        formula: {
            expressions: ["Rendite_investite = Σ I_j"],
            symbols: [["I_j", "rendita del mese di accumulo j configurata per essere investita"]]
        }
    },
    resourceExistingBalanceResult: {
        title: "Investimenti e PAC disponibili al FIRE",
        description: "È il saldo complessivo all'età FIRE degli investimenti aggiuntivi dichiarati disponibili.",
        formula: {
            expressions: ["Investimenti_disponibili = Σ available_q × B_q,N_acc"],
            symbols: [["available_q", "vale 1 se la risorsa è disponibile al FIRE, altrimenti 0"], ["B_q,N_acc", "saldo finale dell'investimento q"]]
        }
    },
    resourceLumpBalanceResult: {
        title: "Capitali futuri disponibili al FIRE",
        description: "Somma i capitali una tantum ricevuti entro l'ingresso nel FIRE e la loro eventuale crescita prima del FIRE.",
        formula: {
            expressions: ["Capitali_al_FIRE = Σ F_q,N_acc"],
            symbols: [["F_q,N_acc", "saldo al FIRE del capitale futuro q ricevuto entro quel confine"]]
        }
    },
    resourceFirstIncomeResult: {
        title: "Rendite nel primo mese FIRE",
        description: "È il totale delle rendite periodiche attive e utilizzabili all'inizio del primo mese FIRE.",
        formula: {
            expressions: ["R₁ = Σ R_q,1"],
            symbols: [["R_q,1", "importo nominale della rendita q attiva nel primo mese FIRE"]]
        }
    },
    resourceFirstNetWithdrawalResult: {
        title: "Prelievo netto nel primo mese FIRE",
        description: "È quanto deve essere prelevato dal patrimonio dopo aver sottratto le rendite disponibili dalla spesa lorda.",
        formula: {
            expressions: ["D₁ = max(0, W₁ − R₁)"],
            symbols: [["W₁", "prima spesa mensile lorda nominale"], ["R₁", "rendite disponibili nel primo mese FIRE"], ["D₁", "prelievo netto richiesto al patrimonio"]]
        }
    },
    resourceFireInflowsResult: {
        title: "Capitali ricevuti durante il FIRE",
        description: "È il totale nominale dei capitali una tantum ricevuti dopo l'ingresso nel FIRE, compreso l'eventuale capitale al confine finale.",
        formula: {
            expressions: ["Capitali_nel_FIRE = Σ K_k + K_terminale"],
            symbols: [["K_k", "capitale ricevuto all'inizio del mese FIRE k"], ["K_terminale", "capitale ricevuto esattamente alla fine dell'orizzonte"]]
        }
    },
    selectedTarget: {
        title: "Patrimonio necessario all'ingresso nel FIRE",
        description: "È il capitale nominale da raggiungere all'età FIRE secondo il metodo selezionato. Sotto viene mostrato anche l'equivalente in euro di oggi.",
        formulas: selectedTargetFormulas
    },
    initialMonthlyContribution: {
        title: "PAC mensile iniziale",
        description: "È il primo versamento mensile necessario per raggiungere il target, considerando patrimonio attuale, rendimento e crescita del PAC. Il versamento avviene a fine mese.",
        formula: contributionFormula
    },
    firstMonthlyWithdrawal: {
        title: "Primo prelievo mensile",
        description: "È la spesa mensile di oggi rivalutata con l'inflazione fino all'età FIRE. Viene prelevata all'inizio del primo mese e alimenta entrambi i metodi.",
        formula: firstWithdrawalFormula
    },
    finiteTarget: {
        title: "Target a durata finita",
        description: "È il capitale necessario per finanziare tutti i prelievi della durata scelta e terminare con il capitale finale desiderato. Usa una rendita anticipata perché il primo prelievo è immediato.",
        formula: finiteTargetFormula
    },
    swrTarget: {
        title: "Target secondo la SWR",
        description: "È la prima spesa annua all'ingresso nel FIRE divisa per la SWR. La proiezione successiva verifica se questo capitale copre davvero tutta la durata indicata.",
        formula: swrTargetFormula
    },
    totalNominalContributions: {
        title: "Nuovi versamenti nominali",
        description: "È la somma di tutti i versamenti PAC effettuati fino al FIRE. Non comprende il patrimonio già investito né i rendimenti maturati.",
        formula: totalContributionsFormula
    },
    personalFinalBalance: {
        title: "Capitale personale a fine FIRE",
        description: "È il patrimonio nominale residuo al termine della durata FIRE, dopo prelievi e rendimenti. Se il capitale si esaurisce prima, il risultato mostra 0 € e l'avviso indica il primo mese non interamente coperto.",
        formula: personalFinalBalanceFormula
    }
};

const helpDialog = document.querySelector("#parameter-help-dialog");
const helpDialogTitle = document.querySelector("#parameter-help-title");
const helpDialogContent = document.querySelector("#parameter-help-content");
const helpDialogClose = document.querySelector("#parameter-help-close");

attachParameterHelp();
helpDialogClose.addEventListener("click", () => helpDialog.close());
helpDialog.addEventListener("click", (event) => {
    if (event.target === helpDialog) {
        helpDialog.close();
    }
});

const defaults = Object.fromEntries(new FormData(form).entries());
const methodSelect = form.elements.namedItem("method");
methodSelect.addEventListener("change", updateMethodFields);
updateMethodFields();

function attachParameterHelp(root = document) {
    root.querySelectorAll("[data-help]").forEach((container) => {
        if (container.querySelector(":scope .info-button")) {
            return;
        }
        const key = container.dataset.help;
        const content = parameterHelp[key];
        const label = container.matches(".field")
            ? container.querySelector(":scope > span:first-child")
            : container.querySelector(":scope > p, :scope > dt");
        if (!content || !label) {
            return;
        }

        label.classList.add("label-with-info");
        const button = document.createElement("button");
        button.type = "button";
        button.className = "info-button";
        button.textContent = "i";
        button.setAttribute("aria-label", `Informazioni su ${content.title}`);
        button.setAttribute("aria-haspopup", "dialog");
        button.addEventListener("click", (event) => {
            event.preventDefault();
            event.stopPropagation();
            const method = container.closest("#results") ? renderedMethod : value("method");
            openParameterHelp(content, method);
        });
        label.append(button);
    });

}

function openParameterHelp(content, method) {
    helpDialogTitle.textContent = content.title;
    helpDialogContent.replaceChildren();

    const description = document.createElement("p");
    description.textContent = content.description;
    helpDialogContent.append(description);

    const formula = content.formulas?.[method] ?? content.formula;
    if (formula) {
        appendFormulaHelp(formula);
    }

    if (content.examples) {
        const examples = document.createElement("div");
        examples.className = "help-examples";
        const heading = document.createElement("h3");
        heading.textContent = "Esempi a confronto";
        examples.append(heading);
        for (const example of content.examples) {
            const card = document.createElement("article");
            const title = document.createElement("strong");
            title.textContent = example.title;
            const text = document.createElement("p");
            text.textContent = example.text;
            card.append(title, text);
            examples.append(card);
        }
        helpDialogContent.append(examples);
    }

    if (content.reference) {
        const reference = document.createElement("div");
        reference.className = "help-reference";
        const heading = document.createElement("strong");
        heading.textContent = "Valore di riferimento";
        const text = document.createElement("p");
        text.textContent = content.reference;
        reference.append(heading, text);
        helpDialogContent.append(reference);
    }

    if (content.source) {
        const source = document.createElement("a");
        source.className = "help-source";
        source.href = content.source.url;
        source.target = "_blank";
        source.rel = "noreferrer";
        source.textContent = `${content.source.label} ↗`;
        helpDialogContent.append(source);
    }

    helpDialog.showModal();
}

function appendFormulaHelp(formula) {
    const section = document.createElement("section");
    section.className = "help-formula";

    const heading = document.createElement("h3");
    heading.textContent = "Formula utilizzata";
    section.append(heading);

    const equations = document.createElement("div");
    equations.className = "help-equations";
    for (const expression of formula.expressions) {
        const equation = document.createElement("code");
        equation.setAttribute("role", "math");
        equation.textContent = expression;
        equations.append(equation);
    }
    section.append(equations);

    const legendHeading = document.createElement("h4");
    legendHeading.textContent = "Significato dei simboli";
    section.append(legendHeading);

    const symbols = document.createElement("dl");
    symbols.className = "help-symbols";
    for (const [symbol, meaning] of formula.symbols) {
        const row = document.createElement("div");
        const term = document.createElement("dt");
        const code = document.createElement("code");
        code.textContent = symbol;
        term.append(code);
        const description = document.createElement("dd");
        description.textContent = meaning;
        row.append(term, description);
        symbols.append(row);
    }
    section.append(symbols);
    helpDialogContent.append(section);
}

function updateMethodFields() {
    const isSwr = value("method") === "SWR";
    document.querySelector("#method-description").textContent = methodDescriptions[value("method")];
    document.querySelector("#swr-field").hidden = !isSwr;
    document.querySelector("#terminal-capital-field").hidden = isSwr;
    form.elements.namedItem("annualSafeWithdrawalRate").disabled = !isSwr;
    form.elements.namedItem("terminalCapitalToday").disabled = isSwr;
}

addResourceButton.addEventListener("click", () => {
    resourceTypePicker.hidden = !resourceTypePicker.hidden;
    addResourceButton.setAttribute("aria-expanded", String(!resourceTypePicker.hidden));
});

resourceTypePicker.addEventListener("click", (event) => {
    const typeButton = event.target.closest("[data-resource-type]");
    if (!typeButton) {
        return;
    }
    addResource(typeButton.dataset.resourceType);
    resourceTypePicker.hidden = true;
    addResourceButton.setAttribute("aria-expanded", "false");
});

resourcesList.addEventListener("click", (event) => {
    const removeButton = event.target.closest("[data-remove-resource]");
    if (!removeButton) {
        return;
    }
    removeButton.closest(".resource-card")?.remove();
    updateResourcesState();
});

resourcesList.addEventListener("change", (event) => {
    const card = event.target.closest(".resource-card");
    if (card) {
        updateResourceConditionalFields(card);
    }
});

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    await runFullCalculation(calculateFireButton, "Calcola FIRE e PAC");
});

calculateResourcesButton.addEventListener("click", async () => {
    await runFullCalculation(calculateResourcesButton, "Ricalcola FIRE e PAC");
});

async function runFullCalculation(triggerButton, idleLabel) {
    clearErrors();

    if (!form.reportValidity() || !validateAdditionalResources()) {
        return;
    }

    const request = buildRequest();
    calculatePacButton.disabled = true;
    const otherFullButton = triggerButton === calculateFireButton ? calculateResourcesButton : calculateFireButton;
    otherFullButton.disabled = true;
    setLoading(triggerButton, true, idleLabel);
    try {
        const result = await calculate(request);
        if (!result.ok) {
            showApiError(result.body, request.additionalResources.length > 0 ? resourceErrorBox : errorBox);
            return;
        }

        lastFireRequest = structuredClone(request);
        renderFireResults(result.body, request);
        renderPacResults(result.body, request);
        renderAdditionalResourcesResult(result.body, request);
        renderProjectionCharts(result.body, request);
        calculatePacButton.disabled = false;
        setText("pac-calculation-help", "Modifica i dati PAC e usa questo pulsante per aggiornare soltanto il piano di accumulo.");

        if (window.matchMedia("(max-width: 920px)").matches) {
            document.querySelector("#results").scrollIntoView({ behavior: "smooth", block: "start" });
        }
    } catch (error) {
        showError(
            "Non è stato possibile contattare il calcolatore. Riprova tra poco.",
            false,
            triggerButton === calculateResourcesButton ? resourceErrorBox : errorBox
        );
    } finally {
        setLoading(triggerButton, false, idleLabel);
        otherFullButton.disabled = false;
        calculatePacButton.disabled = lastFireRequest === null;
    }
}

calculatePacButton.addEventListener("click", async () => {
    clearErrors();
    if (!lastFireRequest || !validatePacInputs()) {
        return;
    }

    const request = { ...lastFireRequest, ...buildPacRequest() };
    calculateFireButton.disabled = true;
    calculateResourcesButton.disabled = true;
    setLoading(calculatePacButton, true, "Ricalcola solo il PAC");
    try {
        const result = await calculate(request);
        if (!result.ok) {
            showApiError(result.body, pacErrorBox);
            return;
        }

        renderPacResults(result.body, request);
        renderAccumulationChart(result.body, request);
        if (window.matchMedia("(max-width: 920px)").matches) {
            document.querySelector(".pac-results-panel").scrollIntoView({ behavior: "smooth", block: "start" });
        }
    } catch (error) {
        showError("Non è stato possibile ricalcolare il PAC. Riprova tra poco.", false, pacErrorBox);
    } finally {
        setLoading(calculatePacButton, false, "Ricalcola solo il PAC");
        calculateFireButton.disabled = false;
        calculateResourcesButton.disabled = false;
    }
});

resetButton.addEventListener("click", () => {
    for (const [name, value] of Object.entries(defaults)) {
        const control = form.elements.namedItem(name);
        if (control) {
            control.value = value;
        }
    }
    updateMethodFields();
    clearErrors();
    renderedMethod = null;
    lastFireRequest = null;
    fireResultsContent.hidden = true;
    pacResultsContent.hidden = true;
    fireResultsPlaceholder.hidden = false;
    pacResultsPlaceholder.hidden = false;
    calculatePacButton.disabled = true;
    setText("pac-calculation-help", "Calcola prima il FIRE per definire il patrimonio da raggiungere.");
    resourcesList.replaceChildren();
    resourcesResult.hidden = true;
    resourcesActions.hidden = true;
    resourceTypePicker.hidden = true;
    addResourceButton.setAttribute("aria-expanded", "false");
    updateResourcesState();
    projections.hidden = true;
    destroyCharts();
    form.querySelector("input, select")?.focus();
});

editButton.addEventListener("click", () => {
    document.querySelector("#fire-form-title").scrollIntoView({ behavior: "smooth", block: "start" });
    form.querySelector("input, select")?.focus({ preventScroll: true });
});

function buildRequest() {
    return {
        method: value("method"),
        currentAge: number("currentAge"),
        fireAge: number("fireAge"),
        fireDurationYears: number("fireDurationYears"),
        monthlyExpenseToday: number("monthlyExpenseToday"),
        annualInflationRate: percent("annualInflationRate"),
        annualFireReturnRate: percent("annualFireReturnRate"),
        annualSafeWithdrawalRate: value("method") === "SWR" ? percent("annualSafeWithdrawalRate") : null,
        terminalCapitalToday: value("method") === "FINITE" ? number("terminalCapitalToday") : 0,
        currentCapital: number("currentCapital"),
        annualAccumulationReturnRate: percent("annualAccumulationReturnRate"),
        annualContributionGrowthRate: percent("annualContributionGrowthRate"),
        additionalResources: buildAdditionalResources()
    };
}

function buildPacRequest() {
    return {
        currentCapital: number("currentCapital"),
        annualAccumulationReturnRate: percent("annualAccumulationReturnRate"),
        annualContributionGrowthRate: percent("annualContributionGrowthRate")
    };
}

function addResource(type) {
    resourceCounter += 1;
    const card = document.createElement("article");
    card.className = "resource-card";
    card.dataset.resourceType = type;
    card.dataset.resourceId = String(resourceCounter);
    card.innerHTML = resourceCardMarkup(type, resourceCounter);
    resourcesList.append(card);
    attachParameterHelp(card);
    updateResourceConditionalFields(card);
    updateResourcesState();
    card.scrollIntoView({ behavior: "smooth", block: "center" });
    card.querySelector("input, select")?.focus({ preventScroll: true });
}

function resourceCardMarkup(type, id) {
    const currentAge = number("currentAge");
    const fireAge = number("fireAge");
    const contributionStartValue = currentAge < fireAge ? `value="${currentAge}"` : "";
    const contributionEndValue = currentAge < fireAge ? `value="${fireAge}"` : "";
    const commonHeader = (badge, title, description) => `
        <div class="resource-card-heading">
            <div><span class="resource-type-badge">${badge}</span><h3>${title}</h3><p>${description}</p></div>
            <button class="remove-resource-button" type="button" data-remove-resource aria-label="Rimuovi ${title}">Rimuovi</button>
        </div>`;
    const nameField = (defaultName) => `
        <label class="field field-wide" data-help="resourceName">
            <span>Nome della risorsa</span>
            <input id="resource-${id}-name" data-resource-field="name" type="text" maxlength="100" value="${defaultName}">
        </label>`;

    if (type === "EXISTING_INVESTMENT") {
        return `${commonHeader("Investimento", "Investimento o PAC esistente", "Proietta un capitale separato e gli eventuali versamenti già programmati.")}
            <div class="resource-field-grid">
                ${nameField("PAC esistente")}
                <label class="field" data-help="resourceCurrentCapital"><span>Patrimonio già investito</span><span class="input-prefix"><span>€</span><input data-resource-field="currentCapital" type="number" min="0" step="1000" value="0" required></span></label>
                <label class="field" data-help="resourceMonthlyContribution"><span>Versamento mensile già programmato</span><span class="input-prefix"><span>€</span><input data-resource-field="initialMonthlyContribution" type="number" min="0" step="10" value="0" required></span></label>
                <label class="field" data-help="resourceContributionStartAge"><span>Età di inizio dei versamenti</span><input data-resource-field="contributionStartAge" type="number" min="0" step="1" ${contributionStartValue}></label>
                <label class="field" data-help="resourceContributionEndAge"><span>Età di fine dei versamenti</span><input data-resource-field="contributionEndAge" type="number" min="0" step="1" ${contributionEndValue}></label>
                <label class="field" data-help="resourceReturnRate"><span>Rendimento annuo della risorsa</span><span class="input-suffix"><input data-resource-field="annualReturnRate" type="number" min="-99.99" step="0.01" value="5" required><span>%</span></span></label>
                <label class="field" data-help="resourceContributionGrowthRate"><span>Crescita annua dei versamenti</span><span class="input-suffix"><input data-resource-field="annualContributionGrowthRate" type="number" min="-99.99" step="0.01" value="0" required><span>%</span></span></label>
                <label class="field checkbox-field field-wide" data-help="resourceAvailableAtFire"><span>Disponibile all'ingresso nel FIRE</span><span class="checkbox-control"><input data-resource-field="availableAtFire" type="checkbox" checked><span>Il saldo contribuirà al patrimonio FIRE</span></span></label>
            </div>`;
    }

    if (type === "PERIODIC_INCOME") {
        return `${commonHeader("Rendita", "Rendita periodica", "Modella un'entrata mensile netta, attuale o futura, con eventuale scadenza.")}
            <div class="resource-field-grid">
                ${nameField("Rendita periodica")}
                <label class="field" data-help="resourceMonthlyIncome"><span>Importo mensile netto di oggi</span><span class="input-prefix"><span>€</span><input data-resource-field="monthlyAmountToday" type="number" min="0" step="10" value="0" required></span></label>
                <label class="field" data-help="resourceIncomeGrowthRate"><span>Crescita annua della rendita</span><span class="input-suffix"><input data-resource-field="annualGrowthRate" type="number" min="-99.99" step="0.01" value="0" required><span>%</span></span></label>
                <label class="field" data-help="resourceStartAge"><span>Età di inizio</span><input data-resource-field="startAge" type="number" min="0" step="1" value="${number("currentAge")}" required></label>
                <label class="field" data-help="resourceEndAge"><span>Età di fine</span><input data-resource-field="endAge" type="number" min="0" step="1" placeholder="Senza fine"><small>Lascia vuoto se continua per tutto l'orizzonte.</small></label>
                <label class="field checkbox-field" data-help="resourceInvestBeforeFire"><span>Investi prima del FIRE</span><span class="checkbox-control"><input data-resource-field="investBeforeFire" type="checkbox" checked><span>Confluisce nel portafoglio di accumulo</span></span></label>
                <label class="field checkbox-field" data-help="resourceOffsetDuringFire"><span>Usa durante il FIRE</span><span class="checkbox-control"><input data-resource-field="offsetDuringFire" type="checkbox" checked><span>Riduce il prelievo richiesto</span></span></label>
            </div>`;
    }

    return `${commonHeader("Capitale futuro", "Capitale futuro una tantum", "Inserisci una somma che diventerà disponibile una sola volta.")}
        <div class="resource-field-grid">
            ${nameField("Capitale futuro")}
            <label class="field" data-help="resourceLumpAmount"><span>Importo</span><span class="input-prefix"><span>€</span><input data-resource-field="amount" type="number" min="0" step="1000" value="0" required></span></label>
            <label class="field" data-help="resourceAmountBasis"><span>Valore dell'importo</span><select data-resource-field="amountBasis" required><option value="TODAY">Euro di oggi</option><option value="NOMINAL">Euro nominali alla ricezione</option></select></label>
            <label class="field" data-help="resourceReceiptAge"><span>Età di ricezione</span><input data-resource-field="receiptAge" type="number" min="0" step="1" value="${number("fireAge")}" required></label>
            <label class="field checkbox-field" data-help="resourceInvestAfterReceipt"><span>Investi dopo la ricezione</span><span class="checkbox-control"><input data-resource-field="investAfterReceipt" type="checkbox" checked><span>Fino all'ingresso nel FIRE</span></span></label>
            <label class="field field-wide" data-help="resourceReturnAfterReceipt" data-return-after-receipt><span>Rendimento annuo dopo la ricezione</span><span class="input-suffix"><input data-resource-field="annualReturnRateAfterReceipt" type="number" min="-99.99" step="0.01" value="5" required><span>%</span></span></label>
        </div>`;
}

function updateResourceConditionalFields(card) {
    if (card.dataset.resourceType === "FUTURE_LUMP_SUM") {
        const invest = resourceField(card, "investAfterReceipt").checked;
        card.querySelector("[data-return-after-receipt]").hidden = !invest;
        resourceField(card, "annualReturnRateAfterReceipt").disabled = !invest;
    }
}

function updateResourcesState() {
    const hasResources = resourcesList.children.length > 0;
    resourcesEmpty.hidden = hasResources;
    if (hasResources) {
        resourcesActions.hidden = false;
    }
}

function buildAdditionalResources() {
    return [...resourcesList.querySelectorAll(".resource-card")].map((card) => {
        const type = card.dataset.resourceType;
        const name = resourceValue(card, "name").trim() || null;
        if (type === "EXISTING_INVESTMENT") {
            return { type, name, currentCapital: resourceNumber(card, "currentCapital"), initialMonthlyContribution: resourceNumber(card, "initialMonthlyContribution"), contributionStartAge: resourceNullableNumber(card, "contributionStartAge"), contributionEndAge: resourceNullableNumber(card, "contributionEndAge"), annualReturnRate: resourcePercent(card, "annualReturnRate"), annualContributionGrowthRate: resourcePercent(card, "annualContributionGrowthRate"), availableAtFire: resourceField(card, "availableAtFire").checked };
        }
        if (type === "PERIODIC_INCOME") {
            return { type, name, monthlyAmountToday: resourceNumber(card, "monthlyAmountToday"), annualGrowthRate: resourcePercent(card, "annualGrowthRate"), startAge: resourceNumber(card, "startAge"), endAge: resourceNullableNumber(card, "endAge"), investBeforeFire: resourceField(card, "investBeforeFire").checked, offsetDuringFire: resourceField(card, "offsetDuringFire").checked };
        }
        const investAfterReceipt = resourceField(card, "investAfterReceipt").checked;
        return { type, name, amount: resourceNumber(card, "amount"), amountBasis: resourceValue(card, "amountBasis"), receiptAge: resourceNumber(card, "receiptAge"), investAfterReceipt, annualReturnRateAfterReceipt: investAfterReceipt ? resourcePercent(card, "annualReturnRateAfterReceipt") : 0 };
    });
}

function validateAdditionalResources() {
    for (const card of resourcesList.querySelectorAll(".resource-card")) {
        for (const control of card.querySelectorAll("input, select")) {
            control.setCustomValidity("");
            if (!control.checkValidity()) {
                control.reportValidity();
                return false;
            }
        }
        if (card.dataset.resourceType === "EXISTING_INVESTMENT") {
            const contribution = resourceNumber(card, "initialMonthlyContribution");
            const start = resourceField(card, "contributionStartAge");
            const end = resourceField(card, "contributionEndAge");
            if ((contribution > 0 && (!start.value || !end.value)) || Boolean(start.value) !== Boolean(end.value)) {
                const invalidControl = !start.value ? start : end;
                invalidControl.setCustomValidity("Inserisci entrambe le età dei versamenti, oppure lascia entrambe vuote quando il versamento è 0 €.");
                invalidControl.reportValidity();
                return false;
            }
        }
        if (card.dataset.resourceType === "PERIODIC_INCOME") {
            const invest = resourceField(card, "investBeforeFire");
            const offset = resourceField(card, "offsetDuringFire");
            if (!invest.checked && !offset.checked) {
                invest.setCustomValidity("Scegli almeno un utilizzo per la rendita.");
                invest.reportValidity();
                return false;
            }
        }
    }
    return true;
}

function renderAdditionalResourcesResult(data, request) {
    if (request.additionalResources.length === 0) {
        resourcesResult.hidden = true;
        resourcesActions.hidden = true;
        return;
    }
    setText("resource-invested-income", money(data.accumulation.totalNominalAdditionalIncomeInvested));
    setText("resource-existing-balance", money(data.accumulation.availableExistingInvestmentsFinalBalance));
    setText("resource-lump-balance", money(data.accumulation.availableFutureLumpSumsFinalBalance));
    setText("resource-first-income", `${money(data.target.firstMonthlyAdditionalIncome)} / mese`);
    setText("resource-first-net-withdrawal", `${money(data.target.firstMonthlyNetWithdrawal)} / mese`);
    setText("resource-fire-inflows", money(data.decumulation.totalCapitalInflows));
    resourcesResult.hidden = false;
}

function resourceField(card, name) {
    return card.querySelector(`[data-resource-field="${name}"]`);
}

function resourceValue(card, name) {
    return resourceField(card, name).value;
}

function resourceNumber(card, name) {
    return Number(resourceValue(card, name));
}

function resourceNullableNumber(card, name) {
    const rawValue = resourceValue(card, name);
    return rawValue === "" ? null : Number(rawValue);
}

function resourcePercent(card, name) {
    return resourceNumber(card, name) / 100;
}

async function calculate(request) {
    const response = await fetch("/api/v1/fire/calculations", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(request)
    });
    return { ok: response.ok, body: await response.json() };
}

function renderFireResults(data, request) {
    const method = request.method;
    renderedMethod = method;
    setText("result-method", methodLabels[method]);
    setText("selected-target", money(data.target.selectedTarget));
    setText("selected-target-today", `${money(data.target.selectedTargetToday)} in euro di oggi`);
    setText("first-withdrawal", `${money(data.target.firstMonthlyWithdrawal)} / mese`);
    const isSwr = method === "SWR";
    document.querySelector("#finite-target-row").hidden = isSwr;
    document.querySelector("#swr-target-row").hidden = !isSwr;
    if (isSwr) {
        setText("swr-target", money(data.target.safeWithdrawalRateTarget));
    } else {
        setText("finite-target", money(data.target.finiteTarget));
    }
    setText("personal-final-balance", money(data.decumulation.personalFinalBalance));

    const depletionMonth = data.decumulation.depletionMonth;
    const fireResultNote = document.querySelector("#fire-result-note");
    fireResultNote.hidden = depletionMonth === null;
    if (depletionMonth !== null) {
        setText(
            "fire-result-note",
            `Attenzione: con una SWR del ${(request.annualSafeWithdrawalRate * 100).toLocaleString("it-IT")}% il capitale non copre tutti i ${request.fireDurationYears} anni. Il primo prelievo non interamente coperto si verifica al ${depletionMonth}° mese FIRE.`
        );
    }

    fireResultsPlaceholder.hidden = true;
    fireResultsContent.hidden = false;
}

function renderPacResults(data, request) {
    const contribution = data.accumulation.initialMonthlyContribution;
    setText("monthly-contribution", `${money(data.accumulation.initialMonthlyContribution)} / mese`);
    setText("accumulation-time", `${formatMonths(data.accumulationMonths)} per raggiungere il target`);
    setText("total-contributions", money(data.accumulation.totalNominalContributions));

    const pacNote = contribution === 0
        ? "Il patrimonio che possiedi oggi è già sufficiente nello scenario inserito: il PAC richiesto è zero."
        : `Il versamento indicato è quello del primo mese. Avviene a fine mese e ${request.annualContributionGrowthRate === 0 ? "resta costante" : "cresce nel tempo"}.`;
    setText("pac-result-note", pacNote);

    pacResultsPlaceholder.hidden = true;
    pacResultsContent.hidden = false;
    projections.hidden = false;
}

function showApiError(problem, target = errorBox) {
    if (Array.isArray(problem.fieldErrors)) {
        for (const error of problem.fieldErrors) {
            const control = findControlForApiField(error.field);
            control?.setAttribute("aria-invalid", "true");
        }
        const items = problem.fieldErrors
            .map((error) => `<li>${escapeHtml(error.message)}</li>`)
            .join("");
        showError(`${escapeHtml(problem.detail || "Controlla i dati inseriti.")}<ul>${items}</ul>`, true, target);
        return;
    }
    showError(problem.detail || "Lo scenario non può essere calcolato con questi valori.", false, target);
}

function showError(message, isHtml = false, target = errorBox) {
    if (isHtml) {
        target.innerHTML = message;
    } else {
        target.textContent = message;
    }
    target.hidden = false;
    target.focus();
}

function clearErrors() {
    for (const box of [errorBox, pacErrorBox, resourceErrorBox]) {
        box.hidden = true;
        box.textContent = "";
    }
    for (const control of [...form.elements, ...resourcesList.querySelectorAll("input, select")]) {
        control.removeAttribute?.("aria-invalid");
        control.setCustomValidity?.("");
    }
}

function findControlForApiField(fieldName) {
    const directControl = form.elements.namedItem(fieldName);
    if (directControl) {
        return directControl;
    }
    const resourceMatch = fieldName.match(/^additionalResources\[(\d+)]\.(\w+)$/);
    if (!resourceMatch) {
        return null;
    }
    const card = resourcesList.querySelectorAll(".resource-card")[Number(resourceMatch[1])];
    return card ? resourceField(card, resourceMatch[2]) : null;
}

function validatePacInputs() {
    const names = ["currentCapital", "annualAccumulationReturnRate", "annualContributionGrowthRate"];
    for (const name of names) {
        const control = form.elements.namedItem(name);
        if (!control.checkValidity()) {
            control.reportValidity();
            return false;
        }
    }
    return true;
}

function setLoading(button, loading, idleLabel) {
    button.disabled = loading;
    button.classList.toggle("is-loading", loading);
    button.setAttribute("aria-busy", String(loading));
    button.querySelector(".button-label").textContent = loading
        ? "Calcolo in corso"
        : idleLabel;
}

function value(name) {
    return form.elements.namedItem(name).value;
}

function number(name) {
    return Number(value(name));
}

function percent(name) {
    return number(name) / 100;
}

function money(valueToFormat) {
    return currency.format(valueToFormat);
}

function formatMonths(months) {
    const years = Math.floor(months / 12);
    const remainingMonths = months % 12;
    if (remainingMonths === 0) {
        return `${years} ${years === 1 ? "anno" : "anni"}`;
    }
    return `${years} ${years === 1 ? "anno" : "anni"} e ${remainingMonths} ${remainingMonths === 1 ? "mese" : "mesi"}`;
}

function setText(id, text) {
    document.querySelector(`#${id}`).textContent = text;
}

function escapeHtml(valueToEscape) {
    const element = document.createElement("div");
    element.textContent = valueToEscape;
    return element.innerHTML;
}

function renderProjectionCharts(data) {
    destroyCharts();

    renderAccumulationChart(data);
    renderDecumulationChart(data);
}

function renderAccumulationChart(data) {
    chartCleanups.accumulation?.();

    const initialCapital = data.accumulation.projection[0]?.totalAvailableBalance ?? 0;
    const accumulationData = data.accumulation.projection.map((point) => ({
        age: point.age,
        balance: point.totalAvailableBalance ?? point.closingBalance,
        contributions: (data.accumulation.projection[0]?.closingBalance ?? 0)
            + point.cumulativeContributions
            + (point.cumulativeAdditionalIncome ?? 0)
    }));

    setText(
        "accumulation-chart-summary",
        `Da ${money(initialCapital)} a ${money(data.accumulation.projectedFinalBalance)} tra ${data.accumulation.projection[0]?.age ?? 0} e ${data.accumulation.projection.at(-1)?.age ?? 0} anni.`
    );

    chartCleanups.accumulation = createProjectionChart({
        name: "accumulation",
        svgId: "accumulation-chart",
        tooltipId: "accumulation-tooltip",
        data: accumulationData,
        series: [
            { key: "balance", label: "Patrimonio", className: "series-one" },
            { key: "contributions", label: "Capitale versato", className: "series-two" }
        ]
    });
}

function renderDecumulationChart(data) {
    chartCleanups.decumulation?.();

    let cumulativeWithdrawals = 0;
    const decumulationData = data.decumulation.projection.map((point) => {
        cumulativeWithdrawals += point.actualWithdrawal;
        return {
            age: point.age,
            balance: point.closingBalance,
            withdrawals: cumulativeWithdrawals
        };
    });

    setText(
        "decumulation-chart-summary",
        `Da ${money(data.decumulation.personalStartBalance)} a ${money(data.decumulation.personalFinalBalance)} nei ${data.fireMonths / 12} anni di FIRE.`
    );

    chartCleanups.decumulation = createProjectionChart({
        name: "decumulation",
        svgId: "decumulation-chart",
        tooltipId: "decumulation-tooltip",
        data: decumulationData,
        series: [
            { key: "balance", label: "Patrimonio", className: "series-one" },
            { key: "withdrawals", label: "Prelievi cumulati", className: "series-two" }
        ]
    });
}

function createProjectionChart({ name, svgId, tooltipId, data, series }) {
    const svg = document.querySelector(`#${svgId}`);
    const tooltip = document.querySelector(`#${tooltipId}`);
    const buttons = [...document.querySelectorAll(`[data-chart="${name}"]`)];
    const activeSeries = new Set(series.map((item) => item.key));
    let resizeFrame;

    const draw = () => {
        const width = Math.max(300, Math.round(svg.parentElement.clientWidth));
        const height = width < 500 ? 282 : 320;
        const margin = { top: 18, right: 16, bottom: 48, left: width < 420 ? 66 : 76 };
        const plotWidth = width - margin.left - margin.right;
        const plotHeight = height - margin.top - margin.bottom;
        const visibleSeries = series.filter((item) => activeSeries.has(item.key));
        const ages = data.map((point) => point.age);
        const values = data.flatMap((point) => visibleSeries.map((item) => point[item.key]));
        const minimumAge = Math.min(...ages);
        const maximumAge = Math.max(...ages);
        const ageSpan = maximumAge - minimumAge || 1;
        const maximumValue = Math.max(1, ...values);
        const paddedMaximum = maximumValue * 1.08;
        const xScale = (age) => margin.left + ((age - minimumAge) / ageSpan) * plotWidth;
        const yScale = (amount) => margin.top + plotHeight - (amount / paddedMaximum) * plotHeight;
        const xTickCount = width < 460 ? 3 : 5;
        const yTickCount = 4;

        const horizontalGrid = Array.from({ length: yTickCount + 1 }, (_, index) => {
            const amount = paddedMaximum * index / yTickCount;
            const y = yScale(amount);
            return `<line class="chart-grid-line" x1="${margin.left}" x2="${width - margin.right}" y1="${y}" y2="${y}"></line>
                <text class="chart-axis-label" x="${margin.left - 9}" y="${y + 4}" text-anchor="end">${compactMoney(amount)}</text>`;
        }).join("");

        const verticalLabels = Array.from({ length: xTickCount }, (_, index) => {
            const ratio = xTickCount === 1 ? 0 : index / (xTickCount - 1);
            const age = minimumAge + ageSpan * ratio;
            const x = margin.left + plotWidth * ratio;
            const anchor = index === 0 ? "start" : index === xTickCount - 1 ? "end" : "middle";
            return `<text class="chart-axis-label" x="${x}" y="${height - 24}" text-anchor="${anchor}">${formatAge(age)}</text>`;
        }).join("");

        const lines = visibleSeries.map((item, index) => {
            const path = data.map((point, pointIndex) => {
                const command = pointIndex === 0 ? "M" : "L";
                return `${command}${xScale(point.age).toFixed(2)},${yScale(point[item.key]).toFixed(2)}`;
            }).join(" ");
            const classNumber = item.className === "series-one" ? "one" : "two";
            return `<path class="chart-line chart-line-series-${classNumber}" d="${path}"></path>`;
        }).join("");

        const markers = visibleSeries.map((item) => {
            const classNumber = item.className === "series-one" ? "one" : "two";
            return `<circle class="chart-hover-marker chart-hover-marker-series-${classNumber}" data-marker="${item.key}" r="4" visibility="hidden"></circle>`;
        }).join("");

        svg.setAttribute("viewBox", `0 0 ${width} ${height}`);
        svg.innerHTML = `
            <rect class="chart-frame" x="${margin.left}" y="${margin.top}" width="${plotWidth}" height="${plotHeight}"></rect>
            ${horizontalGrid}
            ${verticalLabels}
            <text class="chart-axis-title" x="${margin.left + plotWidth / 2}" y="${height - 3}" text-anchor="middle">Età (anni)</text>
            <text class="chart-axis-title" transform="translate(14 ${margin.top + plotHeight / 2}) rotate(-90)" text-anchor="middle">Valore (€)</text>
            ${lines}
            <line class="chart-hover-guide" y1="${margin.top}" y2="${margin.top + plotHeight}" visibility="hidden"></line>
            ${markers}
            <rect class="chart-hit-area" data-chart-hit x="${margin.left}" y="${margin.top}" width="${plotWidth}" height="${plotHeight}"
                  tabindex="0" aria-label="Esplora il grafico con il puntatore o con i tasti freccia"></rect>`;

        const hitArea = svg.querySelector("[data-chart-hit]");
        const guide = svg.querySelector(".chart-hover-guide");
        let selectedIndex = data.length - 1;

        const showPoint = (index) => {
            selectedIndex = Math.max(0, Math.min(data.length - 1, index));
            const point = data[selectedIndex];
            const x = xScale(point.age);
            guide.setAttribute("x1", x);
            guide.setAttribute("x2", x);
            guide.setAttribute("visibility", "visible");

            visibleSeries.forEach((item) => {
                const marker = svg.querySelector(`[data-marker="${item.key}"]`);
                marker.setAttribute("cx", x);
                marker.setAttribute("cy", yScale(point[item.key]));
                marker.setAttribute("visibility", "visible");
            });

            tooltip.innerHTML = `<strong>Età ${formatAge(point.age)}</strong>${visibleSeries.map((item) =>
                `<span><i class="${item.className}"></i>${item.label}<b>${money(point[item.key])}</b></span>`
            ).join("")}`;
            tooltip.hidden = false;

            const svgRect = svg.getBoundingClientRect();
            const xInContainer = x / width * svgRect.width;
            const highestY = Math.min(...visibleSeries.map((item) => yScale(point[item.key])));
            const yInContainer = highestY / height * svgRect.height;
            const tooltipWidth = tooltip.offsetWidth;
            const tooltipHeight = tooltip.offsetHeight;
            const proposedLeft = xInContainer + 12;
            tooltip.style.left = `${proposedLeft + tooltipWidth > svgRect.width ? Math.max(4, xInContainer - tooltipWidth - 12) : proposedLeft}px`;
            tooltip.style.top = `${Math.max(4, yInContainer - tooltipHeight - 10)}px`;
        };

        const hidePoint = () => {
            guide.setAttribute("visibility", "hidden");
            svg.querySelectorAll(".chart-hover-marker").forEach((marker) => marker.setAttribute("visibility", "hidden"));
            tooltip.hidden = true;
        };

        hitArea.addEventListener("pointermove", (event) => {
            const rect = svg.getBoundingClientRect();
            const viewBoxX = (event.clientX - rect.left) * width / rect.width;
            const ratio = Math.max(0, Math.min(1, (viewBoxX - margin.left) / plotWidth));
            showPoint(Math.round(ratio * (data.length - 1)));
        });
        hitArea.addEventListener("pointerleave", hidePoint);
        hitArea.addEventListener("focus", () => showPoint(selectedIndex));
        hitArea.addEventListener("blur", hidePoint);
        hitArea.addEventListener("keydown", (event) => {
            if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") {
                return;
            }
            event.preventDefault();
            showPoint(selectedIndex + (event.key === "ArrowRight" ? 1 : -1));
        });
    };

    const toggleHandlers = buttons.map((button) => {
        const handler = () => {
            const key = button.dataset.series;
            if (activeSeries.has(key) && activeSeries.size === 1) {
                return;
            }
            if (activeSeries.has(key)) {
                activeSeries.delete(key);
                button.setAttribute("aria-pressed", "false");
            } else {
                activeSeries.add(key);
                button.setAttribute("aria-pressed", "true");
            }
            tooltip.hidden = true;
            draw();
        };
        button.addEventListener("click", handler);
        return { button, handler };
    });

    const observer = new ResizeObserver(() => {
        cancelAnimationFrame(resizeFrame);
        resizeFrame = requestAnimationFrame(draw);
    });
    observer.observe(svg.parentElement);
    draw();

    return () => {
        observer.disconnect();
        cancelAnimationFrame(resizeFrame);
        toggleHandlers.forEach(({ button, handler }) => button.removeEventListener("click", handler));
        tooltip.hidden = true;
    };
}

function destroyCharts() {
    chartCleanups.accumulation?.();
    chartCleanups.decumulation?.();
    chartCleanups = { accumulation: null, decumulation: null };
}

function compactMoney(valueToFormat) {
    return new Intl.NumberFormat("it-IT", {
        notation: "compact",
        style: "currency",
        currency: "EUR",
        maximumFractionDigits: 1
    }).format(valueToFormat);
}

function formatAge(age) {
    return Number.isInteger(age) ? String(age) : age.toFixed(1).replace(".", ",");
}
