# Backlog editoriale: criteri e utilizzo

Il backlog ufficiale è [`backlog-editoriale.json`](backlog-editoriale.json) e deve rispettare [`backlog-editoriale.schema.json`](backlog-editoriale.schema.json).

## Composizione iniziale

Il backlog contiene:

- una pagina metodologia manuale e preliminare;
- una prima guida pilota da produrre manualmente;
- ventinove guide successive autorizzate per la produzione automatica;
- quattro livelli di priorità ordinati da `P0` a `P3`.

L'ordine è costruito attorno al percorso del lettore e alle funzioni reali del simulatore, non a stime di volume di ricerca non disponibili.

## Metodo usato

Gli argomenti sono stati ricavati da:

1. input, output e flussi presenti nell'applicazione;
2. specifica matematica e specifica fiscale del motore;
3. architettura di contenuti già concordata;
4. esplorazione delle intenzioni di ricerca italiane effettuata il 24 settembre 2026;
5. necessità di costruire cluster interni senza produrre varianti quasi duplicate.

L'esplorazione delle ricerche conferma interesse per guida FIRE italiana, numero FIRE, vivere di rendita, regola del 4%, PAC, pensione, inflazione e fiscalità. Non sono stati acquisiti dati affidabili sui volumi: il backlog non contiene quindi affermazioni su traffico potenziale o numero di ricerche.

## Priorità

| Priorità | Significato |
| --- | --- |
| `P0` | Prerequisito o contenuto pilastro necessario prima dell'automazione. |
| `P1` | Contenuto centrale per comprendere FIRE e funzioni principali del simulatore. |
| `P2` | Approfondimento direttamente collegato a input, formule o risorse dell'applicazione. |
| `P3` | Contenuto di supporto, interpretazione o uso avanzato. |

La priorità non sostituisce i controlli di qualità. Un elemento `P1` bloccato non autorizza a pubblicare materiale incompleto; l'automazione può passare al successivo elemento idoneo soltanto se il runbook lo consentirà esplicitamente.

## Stati

| Stato | Significato |
| --- | --- |
| `planned` | Contenuto definito ma non ancora pronto per l'esecuzione prevista. |
| `pilot` | Prima guida riservata al processo manuale. |
| `ready` | Brief approvato e selezionabile dall'automazione, se le dipendenze sono concluse. |
| `in_progress` | Esecuzione avviata e protetta dal meccanismo di esclusione. |
| `pushed_to_main` | Commit del contenuto presente su `origin/main`; deploy non verificato. |
| `needs_correction` | Contenuto pubblicato o preparato che richiede una correzione prioritaria. |
| `blocked` | Impossibile procedere senza risolvere una condizione esplicita. |
| `discarded` | Argomento rimosso dal piano con motivazione registrata. |

## Selezione automatica

Una voce è selezionabile soltanto quando:

- ha stato `ready`;
- ha `automation_eligible` uguale a `true`;
- usa `execution_mode` uguale a `automatic`;
- tutte le dipendenze risultano `pushed_to_main`;
- non esiste un contenuto equivalente già pubblicato;
- non è già in corso un'altra esecuzione editoriale.

Tra le voci idonee si sceglie prima la priorità più alta e poi il numero di sequenza più basso. Una singola esecuzione può selezionare al massimo una voce. Se nessuna voce è idonea, termina senza modifiche.

## Dipendenze iniziali

Le guide automatiche dipendono da `METH-0001` e `GUIDE-0001`. Di conseguenza non sono eseguibili finché metodologia e guida pilota non sono state integrate in `main` e marcate `pushed_to_main`.

`GUIDE-0024` dipende inoltre dalla guida generale sui tipi di FIRE, così che il contenuto specifico sul Coast FIRE non anticipi o duplichi la pagina di cluster.

## Prevenzione della cannibalizzazione

Ogni voce definisce un solo intento principale, un risultato atteso per il lettore e un valore originale. Prima della redazione occorre confrontarla con titoli, slug, intento e contenuto delle pagine esistenti.

In particolare:

- le varianti Lean, Fat, Barista e Coast sono introdotte insieme; solo Coast FIRE riceve un approfondimento perché aggiunge un calcolo distinto;
- non devono essere create guide separate per singole età, capitali, spese o percentuali;
- la guida generale sulle risorse aggiuntive coordina gli approfondimenti, senza ripeterne integralmente il contenuto;
- le guide fiscali specifiche spiegano singoli meccanismi e rimandano alla panoramica, evitando una seconda guida generale sulla tassazione.

## Aggiornamento del backlog

Il backlog deve essere aggiornato nello stesso commit del contenuto prodotto. L'automazione modifica almeno stato e `updated_at`, ma non può aggiungere nuovi argomenti autonomamente.

Una nuova voce richiede una decisione editoriale esplicita che documenti intento distinto, valore per il lettore, collegamento al progetto, fonti plausibili e rischio di sovrapposizione. Quando tutte le voci idonee sono esaurite, l'automazione si ferma.
