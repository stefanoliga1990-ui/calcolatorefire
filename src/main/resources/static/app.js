const form = document.querySelector("#fire-form");
const calculateButton = document.querySelector("#calculate-button");
const resetButton = document.querySelector("#reset-button");
const editButton = document.querySelector("#edit-button");
const errorBox = document.querySelector("#form-error");
const resultsPlaceholder = document.querySelector("#results-placeholder");
const resultsContent = document.querySelector("#results-content");
const projections = document.querySelector("#projections");

let chartCleanups = [];

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
    SWR: "Calcola il capitale dividendo la spesa annua iniziale per il tasso di prelievo scelto."
};

const defaults = Object.fromEntries(new FormData(form).entries());
const methodSelect = form.elements.namedItem("method");
methodSelect.addEventListener("change", () => {
    updateMethodFields();
    resultsContent.hidden = true;
    resultsPlaceholder.hidden = false;
    projections.hidden = true;
    destroyCharts();
});
updateMethodFields();

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
    resultsContent.hidden = true;
    resultsPlaceholder.hidden = false;
    projections.hidden = true;
    destroyCharts();
    form.querySelector("input, select")?.focus();
});

editButton.addEventListener("click", () => {
    document.querySelector("#form-title").scrollIntoView({ behavior: "smooth", block: "start" });
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
        safetyMargin: percent("safetyMargin"),
        terminalCapitalToday: value("method") === "FINITE" ? number("terminalCapitalToday") : 0,
        currentCapital: number("currentCapital"),
        annualAccumulationReturnRate: percent("annualAccumulationReturnRate"),
        annualContributionGrowthRate: percent("annualContributionGrowthRate")
    };
}

function renderResults(data) {
    const method = value("method");
    setText("result-method", methodLabels[method]);
    setText("recommended-target", money(data.target.recommendedTarget));
    setText("recommended-target-today", `${money(data.target.recommendedTargetToday)} in euro di oggi`);
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
    setText("projected-current-capital", money(data.accumulation.projectedCurrentCapitalAtFire));
    setText("total-contributions", money(data.accumulation.totalNominalContributions));
    setText("personal-final-balance", money(data.decumulation.personalFinalBalance));
    setText("total-shortfall", money(data.decumulation.totalShortfall));

    const contribution = data.accumulation.initialMonthlyContribution;
    const note = contribution === 0
        ? "Il patrimonio che possiedi oggi è già sufficiente nello scenario inserito: il PAC richiesto è zero."
        : `Il versamento indicato è quello del primo mese. Avviene a fine mese e ${number("annualContributionGrowthRate") === 0 ? "resta costante" : "cresce nel tempo"}.`;
    setText("result-note", note);

    resultsPlaceholder.hidden = true;
    resultsContent.hidden = false;
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
