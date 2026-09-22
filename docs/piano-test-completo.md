# Piano completo di verifica del calcolatore FIRE

## Scopo

Questo documento è la matrice di tracciabilità tra la specifica matematica, gli
scenari di accettazione esistenti e le verifiche da aggiungere. L'obiettivo è
coprire ogni comportamento distinto del motore, dell'API e dell'interfaccia.
Gli input numerici sono continui: la copertura completa viene quindi ottenuta
con classi di equivalenza, valori di confine, combinazioni a coppie e proprietà
matematiche, non enumerando ogni valore possibile.

Le fonti normative sono `docs/specifica-matematica.md` e, per la fiscalità,
`docs/specifica-fiscalita-semplificata.md`. I valori attesi non
devono mai essere rigenerati dal motore Java. Lo script
`scripts/verify_golden_reference.py` costituisce il calcolatore indipendente di
riconciliazione degli scenari golden.

## Livelli di verifica

| Livello | Strumento | Responsabilità |
| --- | --- | --- |
| Riferimento indipendente | Python standard library | Ricalcolo dei golden senza usare codice Java |
| Dominio | JUnit sul `FireCalculator` | Formule, timing, proiezioni, proprietà e confini |
| API | MockMvc e chiamate HTTP locali | JSON, validazioni, mapping e risposta |
| Interfaccia | Browser locale | Stato delle card, pulsanti, errori e rendering |

Tolleranza monetaria: `0,01 euro`. Tolleranza sui tassi: `1e-12`.

## Copertura già presente

| Area | Scenari versionati | Stato iniziale |
| --- | --- | --- |
| FINITE e SWR senza risorse | `BASE_FINITE`, `BASE_SWR` | Coperto |
| Rendimento reale nullo | `ZERO_REAL_FIRE` | Coperto |
| Capitale terminale | `TERMINAL_CAPITAL` | Coperto |
| Capitale già sufficiente | `ALREADY_FUNDED` | Coperto |
| Rendimento accumulo uguale alla crescita PAC | `EQUAL_ACCUM_R_G` | Coperto |
| Spesa nulla | `ZERO_SPENDING` | Coperto |
| Rendita permanente FINITE | `FINITE_PERMANENT_INCOME` | Coperto |
| Pensione futura e ponte SWR | `SWR_FUTURE_PENSION` | Coperto |
| Rendita temporanea SWR | `SWR_TEMPORARY_INCOME` | Coperto |
| Investimento con rendimento proprio | `EXISTING_INVESTMENT_OWN_RETURN` | Coperto |
| PAC esistente a fine mese | `EXISTING_PAC_END_OF_MONTH` | Coperto |
| Rendita investita prima del FIRE | `PERIODIC_INCOME_INVESTED_BEFORE_FIRE` | Coperto |
| Capitale durante FIRE | `FINITE_LUMP_SUM_DURING_FIRE` | Coperto |
| Capitale ponte SWR | `SWR_LUMP_SUM_BRIDGE` | Coperto |
| Capitale al FIRE | `LUMP_SUM_AT_FIRE_REDUCES_PAC` | Coperto |
| Capitale al confine finale | `LUMP_SUM_AT_TERMINAL_BOUNDARY` | Coperto |
| Zero mesi con capitale immediato | `ZERO_MONTHS_WITH_IMMEDIATE_LUMP_SUM` | Coperto |
| Tutte le tipologie insieme | `COMBINED_RESOURCES` | Copertura iniziale |
| Investimento non disponibile | `UNAVAILABLE_EXISTING_INVESTMENT` | Coperto |
| Capitale in euro di oggi | `TODAY_BASIS_LUMP_SUM` | Coperto |

Baseline al termine della Fase 1: 7 golden originari, 14 golden con risorse e
60 test Maven complessivi.

## Matrice delle fasi successive

Gli identificativi indicano famiglie di casi. Ogni famiglia può produrre più
test puntuali quando combina metodo, timing e valori di confine.

| Fase | ID | Area | Classi e confini da coprire | Metodo | Livello |
| --- | --- | --- | --- | --- | --- |
| 2 | `BASE-RATE` | Tassi generali | negativo, zero, positivo, prossimo a `-100%` | FINITE, SWR | Dominio |
| 2 | `BASE-TIME` | Asse temporale | 0, 12 e molti mesi; checkpoint del primo mese | FINITE, SWR | Dominio |
| 2 | `BASE-PAC` | Nuovo PAC | costante, crescente, decrescente, `r = g` | FINITE, SWR | Dominio |
| 2 | `BASE-DIR` | Proprietà direzionali | più spesa, rendimento, capitale e durata | FINITE, SWR | Dominio |
| 2 | `BASE-REC` | Riconciliazione | target, saldo mensile e capitale terminale | FINITE | Dominio |
| 3 | `INV-STOCK` | Investimento esistente | solo capitale, solo PAC, entrambi | FINITE, SWR | Dominio |
| 3 | `INV-PERIOD` | Periodo PAC esistente | inizio oggi/futuro, fine anticipata/al FIRE | FINITE, SWR | Dominio |
| 3 | `INV-RATE` | Tassi investimento | rendimento e crescita negativi, zero, positivi | FINITE, SWR | Dominio |
| 3 | `INV-AVAILABLE` | Disponibilità | `true`, `false`, zero mesi | FINITE, SWR | Dominio |
| 3 | `INV-MULTI` | Più investimenti | somma, separazione, ordine invertito | FINITE, SWR | Dominio |
| 4 | `INC-PHASE` | Uso rendita | accumulo, FIRE, entrambe le fasi | FINITE, SWR | Dominio |
| 4 | `INC-PERIOD` | Periodo rendita | attiva, futura, temporanea, permanente | FINITE, SWR | Dominio |
| 4 | `INC-BOUNDARY` | Confini rendita | inizio/fine al FIRE e fine orizzonte | FINITE, SWR | Dominio |
| 4 | `INC-AMOUNT` | Importo rendita | minore, uguale, maggiore della spesa | FINITE, SWR | Dominio |
| 4 | `INC-GROWTH` | Indicizzazione | negativa, zero, inflazione, altra positiva | FINITE, SWR | Dominio |
| 4 | `INC-MULTI` | Più rendite | periodi separati e sovrapposti | FINITE, SWR | Dominio |
| 5 | `LUMP-BASIS` | Base importo | nominale, euro di oggi | FINITE, SWR | Dominio |
| 5 | `LUMP-TIME` | Ricezione | oggi, accumulo, FIRE, durante FIRE, confine finale | FINITE, SWR | Dominio |
| 5 | `LUMP-RETURN` | Dopo ricezione | non investito; rendimento negativo, zero, positivo | FINITE, SWR | Dominio |
| 5 | `LUMP-MULTI` | Più capitali | stesso mese e mesi differenti | FINITE, SWR | Dominio |
| 5 | `LUMP-TERMINAL` | Capitale finale | inferiore, uguale, superiore al lascito | FINITE | Dominio |
| 6 | `MIX-PAIR` | Coppie di tipi | INV+INC, INV+LUMP, INC+LUMP | FINITE, SWR | Dominio |
| 6 | `MIX-ALL` | Tutti i tipi | 3 e almeno 5 risorse, periodi sovrapposti | FINITE, SWR | Dominio |
| 6 | `MIX-ZERO` | Risorse neutre | importo zero e risorsa non utilizzabile | FINITE, SWR | Dominio |
| 6 | `MIX-ZERO-MONTHS` | Nessun accumulo | sufficiente e insufficiente dopo le risorse | FINITE, SWR | Dominio |
| 7 | `PROP-FINITE` | Proprietà FINITE | riconciliazione e monotonicità | FINITE | Generativo |
| 7 | `PROP-SWR` | Proprietà SWR | target risorse non sopra il base | SWR | Generativo |
| 7 | `PROP-ORDER` | Ordine risorse | permutazione invariata | FINITE, SWR | Generativo |
| 7 | `PROP-SPLIT` | Additività | una risorsa divisa in due equivalenti | FINITE, SWR | Generativo |
| 7 | `PROP-MONTHLY` | Identità mensili | conservazione saldi e somme | FINITE, SWR | Generativo |
| 8 | `API-LIST` | Lista risorse | omessa, `null`, vuota, multipla | FINITE, SWR | API |
| 8 | `API-TYPE` | Polimorfismo | tre tipi, tipo nullo o sconosciuto | FINITE, SWR | API |
| 8 | `API-FIELD` | Campi | mancanti, negativi, tassi e nomi invalidi | FINITE, SWR | API |
| 8 | `API-PERIOD` | Periodi | invertiti e fuori orizzonte | FINITE, SWR | API |
| 8 | `API-RESPONSE` | Risposta | totali, proiezioni, indici e provenienza | FINITE, SWR | API |
| 9 | `UI-ADD` | Aggiunta card | tre tipi e campi condizionali | FINITE, SWR | Browser |
| 9 | `UI-CALC` | Pulsanti | calcolo completo e solo PAC | FINITE, SWR | Browser |
| 9 | `UI-REMOVE` | Rimozione | prima e dopo inclusione nel calcolo | FINITE, SWR | Browser |
| 9 | `UI-STATE` | Stato | cambio metodo, modifica, errore e reset | FINITE, SWR | Browser |
| 9 | `UI-OUTPUT` | Rendering | riepilogo risorse, risultati e grafici | FINITE, SWR | Browser |
| 10 | `STRESS-HORIZON` | Orizzonte | lungo ma valido | FINITE, SWR | Dominio/API |
| 10 | `STRESS-COUNT` | Numero risorse | molte card e molti flussi | FINITE, SWR | Dominio/Browser |
| 10 | `STRESS-NUMERIC` | Stabilità | importi piccoli/grandi e tassi limite | FINITE, SWR | Dominio |
| 11 | `TAX-BASIS` | Costo fiscale | uguale, minore e maggiore del patrimonio; default e override | FINITE, SWR | Dominio/API/UI |
| 11 | `TAX-SALE` | Vendita fiscalizzata | nessun guadagno, guadagno parziale, vendita totale e shortfall | FINITE, SWR | Dominio |
| 11 | `TAX-STAMP` | Imposta di bollo | zero, default, modificata e riconciliazione su 12 mesi | FINITE, SWR | Dominio |
| 11 | `TAX-ACC` | Accumulo | versamenti, rendimenti, bollo e costo fiscale residuo | FINITE, SWR | Dominio |
| 11 | `TAX-RESOURCE` | Risorse aggiuntive | investimento con base propria, rendite nette e capitali netti | FINITE, SWR | Dominio/API |
| 11 | `TAX-SOLVER` | Soluzione congiunta | convergenza di target e PAC, zero mesi e capitale già sufficiente | FINITE, SWR | Dominio |
| 11 | `TAX-ZERO` | Compatibilità | aliquota e bollo a zero sui 21 golden esistenti | FINITE, SWR | Riferimento/Dominio |
| 11 | `TAX-UI` | Interfaccia fiscale | default, collegamento “modificarlo”, reset, output lordo/netto | FINITE, SWR | Browser |

## Proprietà trasversali obbligatorie

1. Ogni valore monetario prodotto deve essere finito; saldi, prelievi e
   shortfall non possono essere negativi.
2. Ogni proiezione contiene `N_acc + 1` oppure `N_fire + 1` punti, incluso il
   mese zero.
3. Il versamento avviene dopo il rendimento del mese di accumulo.
4. Capitale e rendita FIRE sono disponibili prima del prelievo; il rendimento
   è applicato al residuo.
5. Una risorsa nulla non modifica alcun risultato.
6. La permutazione delle risorse non modifica i risultati aggregati.
7. La divisione di una risorsa in componenti equivalenti conserva i risultati.
8. Un investimento con `availableAtFire = false` non riduce il nuovo PAC.
9. Una rendita eccedente la spesa non crea un prelievo negativo.
10. Il target SWR con risorse è minore o uguale al target SWR base.
11. Il target FINITE riconcilia il capitale terminale entro un centesimo.
12. A parità di altri input, più patrimonio disponibile non aumenta il PAC.
13. A parità di saldo e fabbisogno, una plusvalenza latente maggiore non riduce
    la vendita lorda necessaria.
14. Il costo fiscale non aumenta per effetto dei rendimenti o diminuisce per il
    bollo; aumenta soltanto con nuovi apporti netti.
15. Con aliquota e bollo a zero, tutti i risultati coincidono con il motore
    precedente entro la tolleranza monetaria.

## Procedura per ogni fase

1. Definire prima input e risultato atteso dalla specifica.
2. Aggiungere scenari semplici con risultato manualmente verificabile.
3. Aggiungere casi realistici riconciliati dal calcolatore indipendente.
4. Eseguire i test mirati e poi l'intera suite Maven con `settings.xml`.
5. Se un valore diverge, classificare il problema come specifica, golden,
   implementazione o interfaccia. Non aggiornare l'atteso per far passare il test.
6. Registrare copertura, esito e rischio residuo nel riepilogo della fase.

## Comandi locali

```powershell
.\scripts\verify-golden-reference.ps1
mvn -s .\settings.xml -gs .\settings.xml test
```

Il verificatore indipendente deve passare insieme alla suite Maven prima di
considerare completa ogni fase numerica.

## Esiti delle fasi

### Fase 1 — Matrice e riferimento indipendente

- 7 golden originari e 14 golden con risorse riconciliati entro `0,01 euro`;
- nessuna divergenza tra valori versionati e calcolatore indipendente;
- suite iniziale: 60 test, nessun errore o test ignorato.

### Fase 2 — FIRE e PAC senza risorse

Classe aggiunta: `FireCalculatorBaseCoverageTest`, 29 casi eseguiti.

- conversione mensile equivalente per tassi annui negativi, nulli, positivi e
  prossimi a `-100%`;
- orizzonti di accumulo di 0, 12 e 480 mesi per FINITE e SWR;
- PAC decrescente, costante e crescente, incluso il limite `r = g` con tassi
  negativi, nulli e positivi;
- monotonicità rispetto a spesa, rendimento FIRE, capitale corrente e durata;
- target SWR verificato con tassi del 2%, 4% e 10%;
- riconciliazione di ogni mese di accumulo e decumulo in uno scenario FINITE
  con inflazione, rendimento, crescita PAC e capitale terminale;
- validazione dei quattro tassi annuali a `-100%` e della SWR non positiva;
- stabilità numerica di un rendimento FIRE pari a `-99,9999%`.

La matrice `BASE-TIME` è stata precisata: con età intere `N_acc` è sempre un
multiplo di 12, quindi il primo mese viene verificato come checkpoint di una
proiezione annuale e non come durata di accumulo configurabile.

Esito complessivo al termine della fase: 89 test Maven, 0 errori, 0 fallimenti,
0 test ignorati; tutti i 21 golden ancora riconciliati dal riferimento
indipendente.

### Fase 3 — Investimenti e PAC già esistenti

Classe aggiunta: `FireCalculatorExistingInvestmentCoverageTest`, 30 casi
eseguiti.

- capitale iniziale, PAC esistente e combinazione dei due verificati sia con
  FINITE sia con SWR;
- finestre di versamento con inizio incluso e fine esclusa: da oggi, future,
  anticipate e concluse esattamente al FIRE;
- rendimento proprio dell'investimento negativo, nullo e positivo, verificato
  sulla capitalizzazione decennale;
- crescita del PAC negativa, nulla e positiva, verificata sulla somma dei
  versamenti mensili equivalenti;
- riconciliazione di ogni mese di un investimento con capitale, rendimento e
  PAC crescente;
- disponibilità al FIRE attiva e disattiva, inclusi PAC non disponibili e
  scenari con zero mesi di accumulo;
- più investimenti verificati per additività, separazione dei saldi e
  indipendenza dall'ordine nella lista;
- equivalenza tra una risorsa e la sua suddivisione in due risorse con gli
  stessi tassi e periodi;
- rifiuto di periodi incompleti, precedenti all'età attuale, successivi al FIRE
  o vuoti;
- rifiuto di rendimento e crescita del PAC minori o uguali a `-100%`.

Esito complessivo al termine della fase: 119 test Maven, 0 errori,
0 fallimenti, 0 test ignorati; tutti i 21 golden riconciliati dal riferimento
indipendente. Nessuna modifica al motore è risultata necessaria.

### Fase 4 — Rendite periodiche

Classe aggiunta: `FireCalculatorPeriodicIncomeCoverageTest`, 36 casi eseguiti.

- utilizzo della rendita soltanto in accumulo, soltanto durante il FIRE oppure
  in entrambe le fasi, verificato con FINITE e SWR;
- timing a fine mese delle rendite investite, incluso il rendimento positivo
  del portafoglio principale;
- rendite attive e future, con crescita calcolata dal mese zero e inizio
  incluso;
- confini all'ingresso nel FIRE e alla fine dell'orizzonte, con estremo finale
  escluso;
- importi inferiori, uguali e superiori alla spesa, senza prelievi negativi;
- crescita negativa, nulla, uguale all'inflazione e positiva, verificata sia
  durante l'accumulo sia durante il FIRE;
- ponte SWR con due rendite permanenti future e tre regimi di prelievo: target
  base `600.000 euro`, target con risorse `570.000 euro`;
- più rendite verificate per additività e indipendenza dall'ordine;
- equivalenza tra una rendita e due rendite che ne suddividono l'importo;
- neutralità di una rendita con importo zero;
- rifiuto di importi negativi, crescita a `-100%`, rendite inutilizzate,
  periodi vuoti o esterni alla fase nella quale la rendita dovrebbe essere
  usata.

Esito complessivo al termine della fase: 155 test Maven, 0 errori,
0 fallimenti, 0 test ignorati; tutti i 21 golden riconciliati dal riferimento
indipendente. Nessuna modifica al motore è risultata necessaria.

### Fase 5 — Capitali futuri una tantum

Classe aggiunta: `FireCalculatorFutureLumpSumCoverageTest`, 36 casi eseguiti.

- importi nominali ed espressi in euro di oggi verificati con FINITE e SWR;
- rivalutazione dalla data corrente fino alla ricezione, inclusa la ricezione
  durante il FIRE;
- ricezione al mese zero, durante l'accumulo, esattamente al FIRE, durante il
  FIRE e al confine terminale, senza doppio conteggio;
- visibilità del capitale ricevuto oggi nel checkpoint di accumulo del mese
  zero;
- rendimento proprio dopo la ricezione negativo, nullo e positivo prima del
  FIRE;
- rendimento configurato ignorato quando il capitale non viene investito e,
  durante il FIRE, sostituito dal rendimento del portafoglio di decumulo;
- più capitali in mesi diversi verificati per additività e indipendenza
  dall'ordine;
- equivalenza tra un capitale e due capitali contemporanei che ne suddividono
  l'importo;
- capitale terminale inferiore, uguale e superiore al capitale finale
  desiderato, senza finanziamento retroattivo dei prelievi;
- capitale terminale escluso dalla riduzione del target SWR;
- neutralità di un capitale di importo zero;
- rifiuto di importi negativi, base mancante, ricezioni fuori orizzonte e
  rendimento minore o uguale a `-100%`.

Esito complessivo al termine della fase: 191 test Maven, 0 errori,
0 fallimenti, 0 test ignorati; tutti i 21 golden riconciliati dal riferimento
indipendente. Nessuna modifica al motore è risultata necessaria.

### Fase 6 — Combinazioni di più risorse

Classe aggiunta: `FireCalculatorMixedResourcesCoverageTest`, 19 casi eseguiti.

- tutte le coppie di tipologie verificate con FINITE e SWR: investimento più
  rendita, investimento più capitale futuro, rendita più capitale futuro;
- combinazione delle tre tipologie verificata con valori semplici calcolabili
  manualmente, sia per il target sia per il PAC;
- scenario realistico con cinque risorse sovrapposte: due investimenti con
  diversa disponibilità, affitto, pensione e capitale futuro;
- provenienza e indici delle singole risorse conservati nelle proiezioni e nei
  riepiloghi, inclusa la separazione degli investimenti non disponibili;
- indipendenza dei risultati aggregati dall'ordine di risorse eterogenee;
- neutralità simultanea di investimento non disponibile, rendita nulla e
  capitale futuro nullo;
- scenari con zero mesi di accumulo coperti sia quando le risorse finanziano
  interamente il target sia quando resta un deficit non colmabile;
- riconciliazione mensile di saldi, rendite investite, capitali ricevuti e
  nuovo PAC in presenza di periodi sovrapposti, senza doppi conteggi.

Esito complessivo al termine della fase: 210 test Maven, 0 errori,
0 fallimenti, 0 test ignorati; tutti i 21 golden riconciliati dal riferimento
indipendente. Nessuna modifica al motore è risultata necessaria.

### Fase 7 — Proprietà matematiche su scenari generati

Classe aggiunta: `FireCalculatorGeneratedPropertiesTest`, 5 famiglie JUnit,
320 casi generati con seed deterministici e 600 esecuzioni comparative del
motore.

- scenari FINITE riconciliati con il capitale finale desiderato e senza
  shortfall; aumento della spesa e del patrimonio verificato nelle rispettive
  direzioni attese per target e PAC;
- target SWR con risorse sempre non negativo e non superiore al target base,
  che resta indipendente dalle risorse aggiunte;
- risultati aggregati e proiezioni mensili invariati dopo la permutazione di
  investimento, rendita periodica e capitale futuro;
- equivalenza verificata dopo la divisione di ciascuna risorsa in due metà con
  gli stessi tassi, periodi e opzioni;
- conservazione mensile dei saldi in accumulo, negli investimenti esistenti e
  nel FIRE, inclusi contributi cumulati, rendite, capitali, prelievi,
  rendimenti, shortfall e capitale terminale;
- lunghezza, indice e finitezza delle proiezioni verificati per entrambi i
  metodi su orizzonti, importi e tassi variabili, inclusi tassi negativi;
- ogni errore generativo riporta il seed necessario per riprodurre esattamente
  lo scenario.

Esito complessivo al termine della fase: 215 test Maven, 0 errori,
0 fallimenti, 0 test ignorati; tutti i 21 golden riconciliati dal riferimento
indipendente. Nessuna modifica al motore è risultata necessaria.

### Fase 8 — Contratto API delle risorse aggiuntive

Classe aggiunta: `FireCalculationAdditionalResourcesApiCoverageTest`, 28 casi
MockMvc eseguiti.

- compatibilità delle richieste con `additionalResources` omesso, `null` o
  vuoto, tutte equivalenti al calcolo base senza risorse;
- rifiuto di elementi nulli nella lista e di discriminatori `type` mancanti,
  nulli o sconosciuti;
- deserializzazione e validazione distinte per `EXISTING_INVESTMENT`,
  `PERIODIC_INCOME` e `FUTURE_LUMP_SUM`;
- campi obbligatori mancanti o nulli, importi negativi e tassi pari a `-100%`
  verificati per tutte le tipologie, con controllo del percorso restituito in
  `fieldErrors`;
- rifiuto di nomi oltre 100 caratteri e di una base importo non riconosciuta;
- mapping a `422` e ai codici `INVALID_RESOURCE` o
  `INVALID_RESOURCE_PERIOD` per periodi incompleti, invertiti o fuori
  orizzonte e per rendite prive di utilizzo;
- risposta completa verificata con FINITE e SWR: target specifici del metodo,
  totali nominali, saldi delle risorse, proiezioni, nomi, indici di provenienza
  e accrediti mensili durante il FIRE;
- formato `application/problem+json`, stato e URI dell'istanza verificati per
  gli errori di dominio.

Esito complessivo al termine della fase: 243 test Maven, 0 errori,
0 fallimenti, 0 test ignorati; tutti i 21 golden riconciliati dal riferimento
indipendente. Nessuna modifica al contratto o al motore è risultata necessaria.

### Fase 9 — Flussi dell'interfaccia nel browser

Eseguiti 18 scenari sul frontend e backend locali reali. Il dettaglio con input,
valori osservati ed esito è conservato in `docs/verbale-test-ui-fase-9.md`.

- stato iniziale, cambio tra FINITE e SWR e visibilità dei campi condizionali;
- calcolo completo FINITE e SWR, inclusi avviso di esaurimento, assenza di
  `NaN`, righe specifiche del metodo e mantenimento del risultato precedente;
- ricalcolo del solo PAC dopo la modifica degli input di accumulo, senza
  alterare target e decumulo già calcolati;
- aggiunta di investimento, rendita periodica e capitale futuro, con verifica
  dei campi condizionali e del riepilogo dei contributi;
- rimozione di una risorsa già inclusa e ricalcolo automatico senza quella
  risorsa;
- errore di dominio, conservazione dell'ultimo risultato corretto e recupero
  dopo la correzione dell'input;
- reset completo di risultati, risorse, grafici e stato del pulsante PAC;
- rendering delle due serie di entrambi i grafici e popup informativo con
  formula e significato dei simboli;
- viewport mobile senza overflow orizzontale e console priva di errori o
  warning durante la sessione.

Esito complessivo al termine della fase: 243 test Maven invariati, 18 scenari
browser superati e tutti i 21 golden riconciliati dal riferimento indipendente.
Nessuna modifica al frontend, al contratto API o al motore è risultata
necessaria.

### Fase 10 — Stress e stabilità numerica

Classi aggiunte: `FireCalculatorStressCoverageTest`, 18 esecuzioni JUnit, e
`FireCalculationStressApiTest`, 3 casi MockMvc. La prova browser è documentata
in `docs/verbale-test-stress-fase-10.md`.

- età finale massima di 130 anni verificata con FINITE e SWR: 600 mesi di
  accumulo, 720 mesi di FIRE e presenza del mese zero nelle due proiezioni;
- serializzazione API delle proiezioni lunghe verificata con 601 e 721 punti;
- 90 risorse sovrapposte elaborate dal dominio e accettate in una singola
  richiesta API, con quantità e indici di provenienza conservati;
- scenario browser con 30 card, dieci per ogni tipologia, calcolato con FINITE
  e SWR senza valori non finiti, errori di console o perdita dei grafici;
- importi al centesimo e nell'ordine dei miliardi verificati con entrambi i
  metodi;
- tassi immediatamente sopra `-100%` applicati separatamente a rendimento
  FIRE, rendimento di accumulo, crescita PAC e inflazione con FINITE e SWR;
- età finali oltre 130 anni e più di 100 risorse rifiutate in modo controllato.

Esito complessivo aggiornato dopo l'introduzione dei limiti: 280 test Maven, 0 errori,
0 fallimenti, 0 test ignorati; prova browser superata con entrambi i metodi e
tutti i 21 golden riconciliati dal riferimento indipendente.

### Vincoli di input e avvisi successivi alla Fase 10

- aggiunti test di dominio e API per età massima 130, importi massimi pari a
  `1.000.000.000.000 euro`, tassi massimi del 100% e massimo 100 risorse;
- mantenuti validi i tassi negativi strettamente maggiori di `-100%`;
- mantenuta la SWR senza massimo arbitrario, con avvisi non bloccanti sopra il
  5% e il 6%;
- aggiunti avvisi frontend per ipotesi estreme, spesa nulla, orizzonte oltre
  110 anni, risorse nulle e scenari con più di 20 risorse;
- corretti tutti i campi monetari a `step="1"`, così gli importi non devono più
  essere multipli di 10, 50 o 1.000 euro.

Esito della suite dopo questi test di confine: 280 test Maven, 0 errori,
0 fallimenti e 0 ignorati; tutti i 21 golden restano riconciliati.

### Fase 11 — Step 2, motore fiscale isolato

- introdotti 6 test JUnit direttamente collegati ai golden fiscali;
- coperti default e validazioni di aliquota, bollo e costo fiscale;
- verificati apporti, rendimenti positivi e negativi, vendita parziale e totale,
  shortfall, bollo mensile e riconciliazione su dodici mesi;
- mantenuto il `FireCalculator` invariato: nessun risultato pubblico include
  ancora la fiscalità.

Esito dopo lo Step 2: 302 test Maven, 0 errori, 0 fallimenti e 0 ignorati;
7 golden base, 14 golden con risorse e 6 primitive fiscali riconciliati dal
riferimento indipendente.

### Fase 12 — Step 3, accumulo fiscalizzato

- introdotta la proiezione fiscale mensile completa, dal mese zero al FIRE,
  con saldo e costo fiscale distinti;
- coperti PAC costante e crescente, apporti netti, rendimenti, bollo e
  riconciliazione dei totali;
- modellati separatamente gli investimenti e PAC esistenti, inclusi periodo di
  versamento proprio e disponibilità al FIRE;
- verificate plusvalenza e minusvalenza latente, accumulo di zero mesi,
  immutabilità dei calendari e validazioni dei periodi;
- aggiunti 4 scenari golden fiscali di accumulo, verificati da JUnit e dal
  riferimento Python indipendente;
- mantenuti invariati `FireCalculator`, API e frontend.

Esito dopo lo Step 3: 314 test Maven, 0 errori, 0 fallimenti e 0 ignorati;
7 golden base, 14 golden con risorse, 6 primitive fiscali e 4 scenari fiscali
di accumulo riconciliati dal riferimento indipendente.
