# Runbook operativo delle pubblicazioni editoriali

Versione: 1.2

Stato: vincolante per l'esecuzione automatica

Ultimo aggiornamento: 25 settembre 2026

## 1. Scopo e autorità

Questo documento è la procedura unica da seguire per trasformare una voce pronta del backlog in una guida pubblicata su
`origin/main`. Riunisce selezione, ricerca, redazione, generazione, validazione, commit, push, lock e log senza sostituire
le rispettive policy.

In caso di contrasto prevalgono, nell'ordine:

1. il [contratto dell'automazione](contratto-automazione-guide.md);
2. le [condizioni di arresto](condizioni-arresto.json);
3. le policy editoriali, delle fonti e di pubblicazione Git;
4. questo runbook.

Il processo pubblica al massimo una guida per esecuzione. Non attende Railway, non verifica il deploy pubblico, non
interroga Google Search Console e non invia notifiche ordinarie o di successo. Gli arresti seguono l'eventuale policy
`failed_runs_only` già dichiarata nel catalogo, senza implementare un canale di notifica separato.

## 2. Configurazione invariabile

| Voce | Valore |
| --- | --- |
| Avvio pianificato | Ogni 3 ore |
| Fuso orario | `Europe/Rome` |
| Branch e remoto | `main` su `origin` |
| Quantità | Massimo una guida per run |
| Modalità | Automatica completa |
| Push | Diretto, non forzato, su `origin/main` |
| Railway | Escluso dal run |
| Notifiche | Nessuna ordinaria; soli fallimenti secondo il catalogo `STOP-*` |
| Revisione periodica separata | Non prevista |

Una modifica a questi valori richiede prima l'aggiornamento del contratto e delle policy interessate.

## 3. File e comandi di riferimento

Il run usa esclusivamente:

- `docs/editorial/backlog-editoriale.json` per la selezione e lo stato;
- `docs/editorial/registro-fonti.json` per le fonti;
- `content/guides/GUIDE-NNNN.json` e `content/guides/GUIDE-NNNN.body.html` come sorgenti;
- `scripts/generate-guide.ps1` per controllo e generazione;
- `scripts/validate-editorial.ps1` per validazione e test;
- `scripts/publish-guide.ps1` per lock, checkpoint, commit, push e log.

La pagina generata non va mai modificata direttamente. Ogni correzione parte dai sorgenti e termina con una nuova
generazione.

## 4. Sequenza operativa completa

### Fase 0 — Identità del run

Ricavare l'identificatore della finestra di 3 ore:

```powershell
./scripts/publish-guide.ps1 run-id
```

Leggere `run_id` dall'output JSON e conservarlo come variabile operativa. Il valore atteso ha forma
`editorial-AAAAMMGG-HH`, dove `HH` identifica una delle finestre `00`, `03`, `06`, `09`, `12`, `15`, `18` o `21` nel fuso `Europe/Rome`.
Non inventare un secondo identificatore nella stessa finestra.

### Fase 1 — Selezione in sola lettura

Leggere e validare il backlog senza modificarlo. Una voce è idonea soltanto se:

- `status` è `ready`;
- `automation_eligible` è `true`;
- `execution_mode` è `automatic`;
- tutte le dipendenze sono `pushed_to_main`;
- slug, query e intento non duplicano una pagina esistente.

Ordinare prima per priorità crescente (`P0`, `P1`, `P2`, `P3`) e poi per `sequence` crescente. Selezionare solo la
prima voce.

Se non esiste alcuna voce idonea, terminare come `STOP-NO-ELIGIBLE-GUIDE`: nessun file modificato, nessun commit, nessun
push e nessuna notifica. Non inventare nuovi argomenti e non passare a operazioni di ricerca.

Se emerge duplicazione o cannibalizzazione, terminare come `STOP-DUPLICATE-OR-CANNIBALIZATION` e richiedere una
decisione editoriale.

### Fase 2 — Avvio protetto

Avviare la sessione prima di qualsiasi modifica:

```powershell
./scripts/publish-guide.ps1 start -RunId <run-id> -ContentId <GUIDE-NNNN>
```

Se devono essere aggiornati i sorgenti o la pagina di una guida già pubblicata, dichiararla all'avvio:

```powershell
./scripts/publish-guide.ps1 start -RunId <run-id> -ContentId <GUIDE-NNNN> `
  -RelatedContentId <GUIDE-GIA-PUBBLICATA>
```

`start` verifica working tree pulito, branch `main`, fast-forward da `origin/main`, idoneità della guida e assenza di un
altro lock. Conservare `owner_token` soltanto nella memoria del run. Non scriverlo in file, contenuti, log, output
pubblici o commit.

Dopo `start`, ricontrollare che la guida e le dipendenze siano ancora idonee. Portare la voce selezionata da `ready` a
`in_progress` e aggiornare `updated_at` del backlog.

### Fase 3 — Ricerca e registro delle fonti

Seguire la [politica delle fonti](politica-fonti.md): partire da fonti A, usare fonti B per integrazione e confronto e
non usare fonti C per affermazioni centrali. Consultare il contenuto completo e la fonte originale.

Per ogni affermazione centrale, quantitativa, fiscale, normativa o previdenziale:

1. identificare la fonte che la sostiene;
2. verificare autore o ente, data, versione, periodo e locator;
3. registrare o aggiornare la fonte in `registro-fonti.json`;
4. aggiungere l'uso riferito al `content_id` della guida;
5. registrare limiti e conflitti senza nasconderli.

Fermarsi se le fonti sono insufficienti, inaccessibili, discordanti o non adeguate a un'affermazione ad alto rischio.
Non aggirare autenticazioni, CAPTCHA o paywall.

Al termine della ricerca registrare il checkpoint:

```powershell
./scripts/publish-guide.ps1 checkpoint -RunId <run-id> -OwnerToken <owner-token> `
  -Phase research -SourceId @("SRC-AAAA-NNNN", "SRC-AAAA-NNNN") `
  -Check @("fonti originali consultate", "affermazioni centrali mappate")
```

Se la fase supera 30 minuti dall'ultimo comando mutante, rinnovare prima il lock:

```powershell
./scripts/publish-guide.ps1 heartbeat -RunId <run-id> -OwnerToken <owner-token>
```

### Fase 4 — Progettazione e redazione

Compilare la scheda prevista dal [template editoriale](template-editoriale-guida.md): domanda, intento, risultato per il
lettore, risposta breve, valore originale, fuori perimetro, mappa affermazioni-fonti ed eventuale esempio.

Creare:

- `content/guides/<GUIDE-NNNN>.json` conforme allo schema;
- `content/guides/<GUIDE-NNNN>.body.html` con solo il corpo semantico consentito.

La guida deve contenere una risposta autonoma, contenuto originale, limiti visibili, citazioni vicine alle affermazioni
e almeno i collegamenti a `/metodologia` e `/`. Fiscalità e previdenza restano informative e datate; nessun passaggio
può diventare consulenza personale.

Registrare il checkpoint:

```powershell
./scripts/publish-guide.ps1 checkpoint -RunId <run-id> -OwnerToken <owner-token> `
  -Phase drafting -Check @("manifesto completato", "corpo editoriale completato", "citazioni inline verificate")
```

### Fase 5 — Controllo e generazione

Eseguire prima il controllo senza scritture:

```powershell
./scripts/generate-guide.ps1 -Manifest content/guides/<GUIDE-NNNN>.json -CheckOnly
```

Qualsiasi errore è bloccante. Correggere manifesto, corpo, backlog o registro; non modificare l'HTML finale.

Solo dopo un `CheckOnly` riuscito generare pagina, indice delle guide e sitemap:

```powershell
./scripts/generate-guide.ps1 -Manifest content/guides/<GUIDE-NNNN>.json
```

Il generatore ricostruisce automaticamente `/guide` a partire dalle guide pubblicabili, quindi ogni nuova guida deve
comparire nell'indice senza modifiche manuali. Se sono state dichiarate guide correlate, aggiornarne i sorgenti e
rigenerarle esplicitamente. Aggiornare la home solo quando il nuovo collegamento è utile e resta nel perimetro della
singola pubblicazione.

Registrare il checkpoint:

```powershell
./scripts/publish-guide.ps1 checkpoint -RunId <run-id> -OwnerToken <owner-token> `
  -Phase generation -Check @("CheckOnly superato", "pagina generata", "indice guide aggiornato", "sitemap aggiornata")
```

### Fase 6 — Validazione e revisione del diff

Con la guida ancora `in_progress`, eseguire:

```powershell
./scripts/validate-editorial.ps1 -Mode publication -IncludeTests
git diff --check
git status --short
git diff --stat
```

Esaminare l'intero diff. Sono ammessi soltanto i file dichiarati dalla sessione e dalla policy Git, per un massimo di 12
file. Sono vietate modifiche a codice, script, test, configurazione di build o policy durante un run automatico.

Verificare almeno:

- corrispondenza tra brief, title, H1, lead e risposta;
- fonti dichiarate tutte citate e realmente pertinenti;
- esempio riproducibile e ipotesi non presentate come previsioni;
- canonical, dati strutturati, link interni, sitemap e assenza di `noindex`;
- assenza di segnaposto, duplicazioni, keyword stuffing e modifiche estranee;
- generazione idempotente a sorgenti invariati.

Registrare il checkpoint soltanto dopo il superamento dei controlli:

```powershell
./scripts/publish-guide.ps1 checkpoint -RunId <run-id> -OwnerToken <owner-token> `
  -Phase validation -Check @("validazione publication superata", "test Python superati", `
  "test Maven superati", "diff entro allowlist")
```

### Fase 7 — Preparazione dello stato destinato al commit

Aggiornare nello stesso diff la voce della guida:

- `status`: `pushed_to_main`;
- `updated_at`: data corrente del backlog;
- `notes`: solo se serve una nota operativa non già espressa dai campi strutturati.

Questo stato descrive il commit che il comando successivo deve creare e pubblicare. Non autorizza a dichiarare la guida
online e non implica che Railway abbia eseguito il deploy.

Non creare manualmente il commit e non eseguire direttamente `git push`.

### Fase 8 — Commit e push atomici

Eseguire:

```powershell
./scripts/publish-guide.ps1 publish -RunId <run-id> -OwnerToken <owner-token>
```

`publish` ripete la validazione completa, ricontrolla allowlist e `origin/main`, crea il commit deterministico, esegue il
push non forzato, verifica lo SHA remoto, conclude il log e rilascia il lock.

La pubblicazione ha successo soltanto se l'esito restituito è `success`, `push_performed` è `true` e lo SHA del commit è
raggiungibile da `origin/main`.

### Fase 9 — Chiusura

Consultare, senza modificare lo stato:

```powershell
./scripts/publish-guide.ps1 status -RunId <run-id>
./scripts/publish-guide.ps1 log -RunId <run-id>
```

Confermare nel riepilogo:

- guida e slug;
- fonti e controlli;
- file modificati;
- SHA del commit;
- commit e push riusciti;
- `deployment_checked: false`.

Terminare subito. Non attendere Railway, non aprire l'URL pubblico e non richiedere indicizzazione. Non iniziare una
seconda guida.

## 5. Regole di arresto

Un comando con codice diverso da zero interrompe la sequenza. Usare il codice `STOP-*` restituito o la condizione più
specifica del catalogo; non trasformare un arresto in un avviso ignorabile.

Dopo un arresto:

1. non creare commit o push manuali;
2. non usare force push, reset distruttivi o checkout per cancellare indiscriminatamente gli artefatti;
3. conservare lock, sessione, audit e file locali quando previsti;
4. consultare `status` e `log`;
5. seguire esclusivamente `retry` e `recovery` della condizione registrata;
6. non emettere notifiche ordinarie dal run; per gli errori applicare soltanto la policy indicata dal catalogo.

Un arresto prima di `start` lascia il repository invariato. Un arresto dopo `start` mantiene lo stesso `run-id` e
`owner_token` per una correzione ammessa prima del commit.

## 6. Recupero e retry

### Correzione prima del commit

Correggere soltanto la causa registrata, rigenerare gli output interessati, ripetere l'intera validazione e richiamare
`publish` con lo stesso run e token. Non creare una seconda sessione.

### Commit locale non confermato

Non effettuare un retry automatico. Dopo verifica manuale di HEAD, diff, test e remoto:

```powershell
./scripts/publish-guide.ps1 publish -RunId <run-id> -OwnerToken <owner-token> -ManualRecovery
```

### Lock obsoleto

Non rimuovere il lock manualmente. Solo dopo aver verificato che HEAD coincide con quello iniziale, il working tree è
pulito e non esiste un commit associato:

```powershell
./scripts/publish-guide.ps1 cancel -RunId <run-id> -OwnerToken <owner-token> -ManualRecovery
```

### Annullamento sicuro senza modifiche

Se non esistono modifiche né commit:

```powershell
./scripts/publish-guide.ps1 cancel -RunId <run-id> -OwnerToken <owner-token>
```

### Replay dopo successo

Ripetere `publish` su un run già pubblicato deve restituire il risultato esistente con `idempotent_replay: true`, senza
nuovo commit e senza nuovo push.

## 7. Invarianti finali

- Una guida, un run, un commit e al massimo un push.
- Le fonti sono studiate integralmente e registrate prima della redazione finale.
- Nessuna pagina generata viene corretta a mano.
- Nessun test o validatore fallito viene ignorato.
- Lo stato `pushed_to_main` appartiene allo stesso commit della guida.
- `deployment_checked` resta sempre `false`.
- Railway, URL pubblico e Search Console restano fuori dal processo.
- Nessuna notifica ordinaria viene inviata.
- Un nuovo argomento entra nel backlog soltanto tramite decisione editoriale esplicita.
