# Condizioni di arresto del processo editoriale

Versione: 1.0

Stato: vincolante per il processo manuale e automatico

Ultimo aggiornamento: 24 settembre 2026

## 1. Obiettivo

Le condizioni di arresto stabiliscono quando una esecuzione deve terminare prima della pubblicazione e che cosa può
fare dopo aver rilevato il problema. Servono a impedire pubblicazioni parziali, correzioni automatiche rischiose,
duplicazioni e push effettuati su uno stato Git non più valido.

Il catalogo operativo è [`condizioni-arresto.json`](condizioni-arresto.json), validato dallo
[`schema JSON`](condizioni-arresto.schema.json). Questo documento ne spiega la semantica. Il
[contratto dell'automazione](contratto-automazione-guide.md) resta la fonte normativa superiore.

## 2. Invarianti comuni

Quando si attiva una qualsiasi condizione `STOP-*`:

- l'esecuzione termina e non inizia un'altra guida;
- non vengono creati ulteriori contenuti o modifiche correttive speculative;
- non è consentito creare un commit successivo al rilevamento;
- non è consentito effettuare il push;
- non sono ammessi force push, reset distruttivi o risoluzioni automatiche di conflitti non banali;
- gli artefatti già prodotti possono restare locali ma non devono raggiungere `main`;
- il motivo deve essere registrato con codice, fase, evidenze e stato Git;
- `deployment_checked` resta sempre `false`;
- Railway, URL pubblico e Google Search Console non vengono interrogati.

Se il push su `origin/main` è già stato confermato, il ciclo editoriale è terminato con successo: un problema successivo
non appartiene a queste condizioni e non autorizza rollback automatici.

## 3. Tipi di esito

| Esito | Significato | Notifica | Nuovo tentativo |
| --- | --- | --- | --- |
| `no_op` | Nessun lavoro idoneo; esecuzione normale senza modifiche. | Nessuna. | Alla schedulazione successiva. |
| `deferred` | Un'altra attività o un cambio concorrente rende opportuno rinviare. | Nessuna. | Alla schedulazione successiva. |
| `failed` | Errore tecnico o risorsa temporaneamente indisponibile. | Solo policy `failed_runs_only`. | Manuale o alla schedulazione successiva come indicato nel catalogo. |
| `intervention_required` | Serve una decisione o correzione consapevole prima di proseguire. | Solo policy `failed_runs_only`. | Esclusivamente dopo intervento manuale. |

L'assenza di guide idonee e un lock attivo non sono errori. Non devono generare rumore o notifiche ordinarie.

## 4. Stato del repository all'arresto

| Stato | Regola |
| --- | --- |
| `unchanged` | La condizione viene rilevata prima delle modifiche; il repository deve restare identico allo stato iniziale. |
| `local_artifacts_allowed` | Possono esistere sorgenti o output locali non pubblicati; non devono essere committati o inclusi nella prossima esecuzione senza nuova verifica. |
| `local_commit_present` | Il commit atomico può essere già stato creato, ma non è su `origin/main`; sono vietati push forzati e tentativi ciechi. |

L'automazione non deve cancellare automaticamente artefatti o commit per simulare uno stato pulito. La successiva
esecuzione riparte sempre dai prerequisiti e, trovando modifiche residue, applica la relativa condizione di arresto.

## 5. Ordine dei controlli

### Preflight

Prima di selezionare una guida devono essere verificati, nell'ordine:

1. working tree pulito;
2. branch e HEAD attesi;
3. disponibilità e stato di `origin/main`;
4. aggiornabilità esclusivamente fast-forward;
5. assenza di un'altra esecuzione o di lock sospetti;
6. validità di backlog, registro fonti e configurazioni editoriali.

Un errore in preflight vieta qualsiasi modifica. Un lock valido produce rinvio silenzioso; un lock non verificabile
richiede intervento manuale e non può essere rimosso automaticamente.

### Selezione

Se nessuna guida è idonea, l'esecuzione termina come `no_op`. Dopo la selezione devono essere esclusi slug duplicati,
cannibalizzazione sostanziale e cambi concorrenti allo stato della voce o delle dipendenze.

### Ricerca

La redazione non inizia quando:

- mancano fonti adeguate per una affermazione centrale;
- una fonte indispensabile non è consultabile integralmente;
- fonti autorevoli restano in conflitto;
- una affermazione fiscale, previdenziale, normativa o finanziaria ad alto rischio non è rappresentabile in modo affidabile.

Una fonte non disponibile può essere un problema temporaneo. Insufficienza, conflitto e ambiguità richiedono invece una
decisione editoriale e non devono essere aggirati riducendo silenziosamente la qualità delle fonti.

### Redazione e generazione

Qualsiasi violazione di policy, originalità, template o limiti sulla consulenza personale arresta la guida. Un errore del
generatore deve essere corretto nei sorgenti: è vietato modificare manualmente l'HTML finale per superarlo.

### Validazione

Sono bloccanti:

- errori dei validatori development o publication;
- fallimenti dei test Python, della build o della suite Maven;
- drift tra sorgenti e pagina generata;
- diff contenente file estranei alla singola guida e ai relativi registri.

La correzione di un errore invalida i controlli successivi già eseguiti: la catena deve ripartire dalla prima fase
interessata e concludersi nuovamente con `publication -IncludeTests`.

### Commit e push

Il commit deve contenere soltanto l'unità editoriale approvata. Dopo il commit, prima del push, `origin/main` deve ancora
corrispondere allo SHA remoto memorizzato nel preflight.

Se il remoto è avanzato o il push viene rifiutato:

- conservare hash del commit locale e SHA remoti come evidenza;
- non usare force push;
- non fare rebase, merge o risoluzioni automatiche non previste;
- richiedere intervento e ripetere validatori e test sul nuovo stato prima di un eventuale nuovo push.

## 6. Interruzioni non classificate

Limiti di utilizzo, indisponibilità degli strumenti o altre interruzioni prima del commit usano
`STOP-EXECUTION-INTERRUPTED`. Se il commit locale esiste già ma il push non è stato confermato, si usa
`STOP-EXECUTION-INTERRUPTED-AFTER-COMMIT` e si richiede intervento manuale. Il log deve indicare l'ultima fase
completata e lo stato Git finale.

Una esecuzione successiva non può presumere che il lavoro precedente sia valido o completo. Deve ripartire dal
preflight; eventuali artefatti residui attiveranno `STOP-REPOSITORY-NOT-CLEAN` e richiederanno valutazione consapevole.

## 7. Evidenze e log minimi

Ogni arresto deve registrare almeno:

- codice `STOP-*` e tipo di esito;
- data e ora di rilevamento;
- fase raggiunta;
- ID esecuzione;
- ID e slug della guida, se già selezionata;
- evidenze elencate nel catalogo per quella condizione;
- file modificati e stato del working tree;
- HEAD iniziale, commit locale e SHA remoto quando disponibili;
- retry previsto;
- `commit_performed`, `push_performed` e `deployment_checked`;
- messaggio sintetico di recupero.

Non inserire nei log credenziali, cookie, token, contenuti riservati o copie integrali non necessarie delle fonti.

## 8. Regole di retry

- `none`: l'esecuzione è conclusa e non esiste un retry automatico associato;
- `next_schedule`: non restare in attesa; la prossima esecuzione ricomincia dal preflight;
- `manual`: nessun nuovo tentativo finché la causa non è stata valutata e corretta consapevolmente.

Un retry non riutilizza automaticamente uno stato `in_progress`, una ricerca parziale o un commit locale. Idempotenza,
lock e cronologia delle esecuzioni devono determinare il percorso corretto secondo il
[runbook operativo](runbook-operativo.md).

## 9. Validazione del catalogo

Il comando generale controlla anche il catalogo:

```powershell
.\scripts\validate-editorial.ps1
```

Verifica campi, codici e ordini univoci, esiti, notifiche, retry, stati del repository, condizioni minime richieste dal
contratto e invarianti di sicurezza. Una modifica non valida del catalogo blocca il processo già nel preflight.

## 10. Modifiche

Nuove condizioni possono essere aggiunte solo quando descrivono un rischio distinto. Non devono indebolire le condizioni
esistenti, trasformare un intervento manuale in retry cieco o consentire commit e push dopo un arresto.

Qualsiasi modifica a esito, retry, notifiche o comportamento Git deve aggiornare nello stesso commit catalogo, schema,
questo documento, validatori e contratto quando ne cambia le regole sostanziali.
