# Piano completo di verifica del calcolatore FIRE

## Scopo

Questo documento è la matrice di tracciabilità tra la specifica matematica, gli
scenari di accettazione esistenti e le verifiche da aggiungere. L'obiettivo è
coprire ogni comportamento distinto del motore, dell'API e dell'interfaccia.
Gli input numerici sono continui: la copertura completa viene quindi ottenuta
con classi di equivalenza, valori di confine, combinazioni a coppie e proprietà
matematiche, non enumerando ogni valore possibile.

La fonte normativa resta `docs/specifica-matematica.md`. I valori attesi non
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
