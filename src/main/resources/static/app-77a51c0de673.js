const form = document.querySelector("#fire-form");
const calculateButton = document.querySelector("#calculate-button");
const resetButton = document.querySelector("#reset-button");
const editButton = document.querySelector("#edit-button");
const errorBox = document.querySelector("#form-error");
const resultsPlaceholders = document.querySelectorAll("[data-results-placeholder]");
const resultsContents = document.querySelectorAll("[data-results-content]");
const projections = document.querySelector("#projections");

let chartCleanups = [];
let renderedMethod = null;

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
        "T_finite = W₁ × [1 − (1 + r_reale,m)^(-N_FIRE)] ÷ r_reale,m × (1 + r_reale,m) + L_FIRE ÷ (1 + r_reale,m)^N_FIRE",
        "Se r_reale,m = 0:  T_finite = W₁ × N_FIRE + L_FIRE"
    ],
    symbols: [
        ["T_finite", "target nominale calcolato con il metodo Durata finita"],
        ["W₁", "primo prelievo mensile nominale all’ingresso nel FIRE"],
        ["r_reale,m", "rendimento reale mensile nel FIRE: (1 + r_f,m) ÷ (1 + i_m) − 1"],
        ["N_FIRE", "numero di mesi della durata FIRE"],
        ["L_FIRE", "capitale finale desiderato, rivalutato fino all’ingresso nel FIRE"]
    ]
};

const swrTargetFormula = {
    expressions: ["T_SWR = W₁ × 12 ÷ SWR"],
    symbols: [
        ["T_SWR", "target nominale calcolato con il metodo Safe Withdrawal Rate"],
        ["W₁", "primo prelievo mensile nominale all’ingresso nel FIRE"],
        ["12", "numero di mesi usato per trasformare il prelievo mensile in spesa annua"],
        ["SWR", "tasso annuo di prelievo iniziale, espresso in forma decimale"]
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
        "Gap = max(0, T_target − V₀ × (1 + r_a,m)^N_acc)",
        "F = [(1 + r_a,m)^N_acc − (1 + g_m)^N_acc] ÷ (r_a,m − g_m)",
        "Se r_a,m = g_m:  F = N_acc × (1 + r_a,m)^(N_acc − 1)"
    ],
    symbols: [
        ["C₁", "PAC del primo mese, versato a fine mese"],
        ["Gap", "capitale che i nuovi versamenti devono ancora costruire"],
        ["F", "fattore di capitalizzazione dei versamenti mensili"],
        ["T_target", "patrimonio necessario secondo il metodo selezionato: T_finite oppure T_SWR"],
        ["V₀", "patrimonio investito oggi"],
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
        "W_k = W₁ × (1 + i_m)^(k − 1)",
        "A_k = min(B_(k−1), W_k)",
        "B_k = max(0, [B_(k−1) − A_k] × (1 + r_f,m))",
        "Capitale_finale = B_N_FIRE"
    ],
    symbols: [
        ["B₀", "capitale personale disponibile all’inizio del FIRE"],
        ["T_target", "patrimonio necessario secondo il metodo selezionato"],
        ["B_acc", "patrimonio effettivamente raggiunto al termine dell’accumulo"],
        ["k", "numero progressivo del mese FIRE"],
        ["W_k", "prelievo programmato all’inizio del mese k"],
        ["W₁", "primo prelievo mensile nominale"],
        ["i_m", "tasso mensile equivalente dell’inflazione"],
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

const defaults = Object.fromEntries(new FormData(form).entries());
const methodSelect = form.elements.namedItem("method");
methodSelect.addEventListener("change", updateMethodFields);
updateMethodFields();

function attachParameterHelp() {
    document.querySelectorAll("[data-help]").forEach((container) => {
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

    helpDialogClose.addEventListener("click", () => helpDialog.close());
    helpDialog.addEventListener("click", (event) => {
        if (event.target === helpDialog) {
            helpDialog.close();
        }
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

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    clearErrors();

    if (!form.reportValidity()) {
        return;
    }

    setLoading(true);
    try {
        const response = await fetch("/api/v1/fire/calculations", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(buildRequest())
        });

        const body = await response.json();
        if (!response.ok) {
            showApiError(body);
            return;
        }

        renderResults(body);
    } catch (error) {
        showError("Non è stato possibile contattare il calcolatore. Riprova tra poco.");
    } finally {
        setLoading(false);
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
    resultsContents.forEach((content) => content.hidden = true);
    resultsPlaceholders.forEach((placeholder) => placeholder.hidden = false);
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
        annualContributionGrowthRate: percent("annualContributionGrowthRate")
    };
}

function renderResults(data) {
    const method = value("method");
    renderedMethod = method;
    setText("result-method", methodLabels[method]);
    setText("selected-target", money(data.target.selectedTarget));
    setText("selected-target-today", `${money(data.target.selectedTargetToday)} in euro di oggi`);
    setText("monthly-contribution", `${money(data.accumulation.initialMonthlyContribution)} / mese`);
    setText("accumulation-time", `${formatMonths(data.accumulationMonths)} per raggiungere il target`);
    setText("first-withdrawal", `${money(data.target.firstMonthlyWithdrawal)} / mese`);
    const isSwr = method === "SWR";
    document.querySelector("#finite-target-row").hidden = isSwr;
    document.querySelector("#swr-target-row").hidden = !isSwr;
    if (isSwr) {
        setText("swr-target", money(data.target.safeWithdrawalRateTarget));
    } else {
        setText("finite-target", money(data.target.finiteTarget));
    }
    setText("total-contributions", money(data.accumulation.totalNominalContributions));
    setText("personal-final-balance", money(data.decumulation.personalFinalBalance));

    const contribution = data.accumulation.initialMonthlyContribution;
    const depletionMonth = data.decumulation.depletionMonth;
    const fireResultNote = document.querySelector("#fire-result-note");
    fireResultNote.hidden = depletionMonth === null;
    if (depletionMonth !== null) {
        setText(
            "fire-result-note",
            `Attenzione: con una SWR del ${number("annualSafeWithdrawalRate").toLocaleString("it-IT")}% il capitale non copre tutti i ${number("fireDurationYears")} anni. Il primo prelievo non interamente coperto si verifica al ${depletionMonth}° mese FIRE.`
        );
    }

    const pacNote = contribution === 0
        ? "Il patrimonio che possiedi oggi è già sufficiente nello scenario inserito: il PAC richiesto è zero."
        : `Il versamento indicato è quello del primo mese. Avviene a fine mese e ${number("annualContributionGrowthRate") === 0 ? "resta costante" : "cresce nel tempo"}.`;
    setText("pac-result-note", pacNote);

    resultsPlaceholders.forEach((placeholder) => placeholder.hidden = true);
    resultsContents.forEach((content) => content.hidden = false);
    projections.hidden = false;
    renderProjectionCharts(data);
    if (window.matchMedia("(max-width: 920px)").matches) {
        document.querySelector("#results").scrollIntoView({ behavior: "smooth", block: "start" });
    }
}

function showApiError(problem) {
    if (Array.isArray(problem.fieldErrors)) {
        for (const error of problem.fieldErrors) {
            const control = form.elements.namedItem(error.field);
            control?.setAttribute("aria-invalid", "true");
        }
        const items = problem.fieldErrors
            .map((error) => `<li>${escapeHtml(error.message)}</li>`)
            .join("");
        showError(`${escapeHtml(problem.detail || "Controlla i dati inseriti.")}<ul>${items}</ul>`, true);
        return;
    }
    showError(problem.detail || "Lo scenario non può essere calcolato con questi valori.");
}

function showError(message, isHtml = false) {
    if (isHtml) {
        errorBox.innerHTML = message;
    } else {
        errorBox.textContent = message;
    }
    errorBox.hidden = false;
    errorBox.focus();
}

function clearErrors() {
    errorBox.hidden = true;
    errorBox.textContent = "";
    for (const control of form.elements) {
        control.removeAttribute?.("aria-invalid");
    }
}

function setLoading(loading) {
    calculateButton.disabled = loading;
    calculateButton.classList.toggle("is-loading", loading);
    calculateButton.setAttribute("aria-busy", String(loading));
    calculateButton.querySelector(".button-label").textContent = loading
        ? "Calcolo in corso"
        : "Calcola il mio scenario";
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

    const initialCapital = data.accumulation.projection[0]?.closingBalance ?? 0;
    const accumulationData = data.accumulation.projection.map((point) => ({
        age: point.age,
        balance: point.closingBalance,
        contributions: initialCapital + point.cumulativeContributions
    }));

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
        "accumulation-chart-summary",
        `Da ${money(initialCapital)} a ${money(data.accumulation.projectedFinalBalance)} tra ${number("currentAge")} e ${number("fireAge")} anni.`
    );
    setText(
        "decumulation-chart-summary",
        `Da ${money(data.decumulation.personalStartBalance)} a ${money(data.decumulation.personalFinalBalance)} nei ${data.fireMonths / 12} anni di FIRE.`
    );

    chartCleanups.push(createProjectionChart({
        name: "accumulation",
        svgId: "accumulation-chart",
        tooltipId: "accumulation-tooltip",
        data: accumulationData,
        series: [
            { key: "balance", label: "Patrimonio", className: "series-one" },
            { key: "contributions", label: "Capitale versato", className: "series-two" }
        ]
    }));

    chartCleanups.push(createProjectionChart({
        name: "decumulation",
        svgId: "decumulation-chart",
        tooltipId: "decumulation-tooltip",
        data: decumulationData,
        series: [
            { key: "balance", label: "Patrimonio", className: "series-one" },
            { key: "withdrawals", label: "Prelievi cumulati", className: "series-two" }
        ]
    }));
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
    chartCleanups.forEach((cleanup) => cleanup());
    chartCleanups = [];
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
