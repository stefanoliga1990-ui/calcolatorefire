# Verbale test interfaccia — Fase 9

## Ambiente

- data: 21 settembre 2026;
- branch: `rendite-aggiuntive`;
- revisione verificata: `efa9e1b3484650a899d4c3b95c8064854b09f270`;
- applicazione locale: Spring Boot su `http://127.0.0.1:8080/`;
- browser: Codex In-app Browser;
- backend e frontend reali, senza risposte API simulate.

## Scenari eseguiti

| ID | Verifica | Evidenza osservata | Esito |
| --- | --- | --- | --- |
| `UI-STATE-INITIAL` | Stato iniziale | Placeholder FIRE e PAC visibili; PAC disabilitato; nessuna risorsa | Passa |
| `UI-METHOD` | Cambio FINITE/SWR | Descrizione aggiornata; SWR e capitale finale mostrati solo nel metodo pertinente | Passa |
| `UI-CALC-FINITE` | Calcolo completo predefinito | Target `557.771 €`; PAC `2.319 €/mese`; righe FINITE corrette | Passa |
| `UI-STATE-PRESERVE` | Cambio metodo dopo il calcolo | Target e badge FINITE restano visibili fino al calcolo successivo | Passa |
| `UI-CALC-PAC` | Ricalcolo del solo PAC | Con capitale portato a `100.000 €`, PAC da `2.319` a `1.496 €/mese`; FIRE invariato | Passa |
| `UI-ADD` | Aggiunta dei tre tipi | Tre card create; area azioni visibile; stato vuoto nascosto | Passa |
| `UI-CONDITIONAL` | Campo condizionale capitale futuro | Disattivando l'investimento, il rendimento successivo è nascosto e disabilitato | Passa |
| `UI-CALC-RESOURCES` | Calcolo con tre risorse | Riepilogo visibile; target `407.513 €`; PAC `688 €/mese`; tutte le card marcate come incluse | Passa |
| `UI-OUTPUT-RESOURCES` | Rendering contributi risorse | Rendite investite `84.000 €`; investimento al FIRE `121.656 €`; rendita iniziale `500 €/mese`; capitale FIRE `80.422 €` | Passa |
| `UI-REMOVE` | Rimozione dopo inclusione | Rimossa la rendita, ricalcolo automatico: target `508.399 €`, PAC `1.608 €/mese`, rendite azzerate | Passa |
| `UI-ERROR` | Errore di dominio su una risorsa | Messaggio mostrato nella sezione risorse e risultati precedenti conservati | Passa |
| `UI-RECOVERY` | Correzione dopo errore | Corretto il periodo, errore nascosto e risultati aggiornati | Passa |
| `UI-RESET` | Ripristino | Card eliminate, placeholder ripristinati, grafici nascosti e PAC disabilitato | Passa |
| `UI-CALC-SWR` | Calcolo SWR al 6% | Target `422.233 €`; sola riga SWR; avviso di esaurimento al mese 273; nessun `NaN` | Passa |
| `UI-HELP` | Popup informativo risultato | Titolo, formula e significato dei simboli presenti | Passa |
| `UI-CHARTS` | Grafici | Due serie SVG in accumulo e due in decumulo dopo il calcolo | Passa |
| `UI-MOBILE` | Viewport mobile | Nessun overflow orizzontale; card FIRE, risultati e risorse entro il viewport | Passa |
| `UI-CONSOLE` | Console browser | Nessun errore o warning durante l'intera sessione | Passa |

## Osservazioni

I valori del calcolo predefinito riflettono i default PAC correnti: patrimonio
iniziale `0 €`, rendimento di accumulo `5%` e crescita del PAC `0%`.

La rimozione di una risorsa già inclusa attende la risposta del nuovo calcolo e
aggiorna FIRE, PAC, riepilogo risorse e grafici senza richiedere un secondo clic.
Il test di errore conferma che una risposta non valida non cancella l'ultimo
risultato corretto.

Non sono emersi difetti funzionali o grafici che richiedano modifiche al codice.
