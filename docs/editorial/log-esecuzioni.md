# Log delle esecuzioni editoriali

## Obiettivo

Il log deve permettere di ricostruire cosa ha fatto ogni run senza includere credenziali o copie superflue delle fonti.
La configurazione e gli schemi sono versionati nel repository; i dati operativi restano locali in `.git` e non vengono
pubblicati sul sito.

## Struttura locale

```text
.git/editorial-publication/
├── sessions/<run-id>.json
├── audit/<run-id>/<timestamp>-<event-id>.json
└── history/<run-id>.json
```

- `sessions` contiene lo stato aggiornabile necessario a riprendere o diagnosticare un run;
- `audit` contiene eventi immutabili, creati con nomi univoci e mai sovrascritti;
- `history` contiene un solo risultato finale immutabile per run concluso.

Il formato del risultato finale è definito da [execution-log.schema.json](execution-log.schema.json); comportamento,
directory e redazioni sono vincolati da [execution-log-policy.json](execution-log-policy.json).

## Campi del risultato finale

Il risultato conserva:

- identificatore, inizio, fine, stato, esito e ultima fase;
- guida e slug;
- ID delle fonti usate e controlli completati;
- file modificati;
- HEAD e SHA remoto iniziali;
- SHA del commit ed esito di commit e push;
- `deployment_checked: false`;
- eventuale codice, messaggio, retry, notifica e procedura di recupero.

I campi non applicabili sono presenti con valore `null` oppure come array vuoti, così lettura e analisi non dipendono
dall'esito del run.

## Eventi di audit

Ogni comando mutante eseguito dall'interfaccia registra un evento con timestamp UTC, ID univoco, run, azione, esito,
fase, guida ed eventuale codice `STOP-*`. Anche un fallimento avvenuto prima della creazione della sessione viene
registrato quando il repository e la policy dei log sono leggibili.

Gli eventi sono append-only: un nuovo evento crea sempre un nuovo file con apertura esclusiva. Il risultato finale usa
la stessa protezione; una seconda scrittura è ammessa soltanto quando il contenuto è byte per byte identico.

## Protezione dei dati

Prima della scrittura vengono redatti:

- token proprietari e altri campi contenenti `token`;
- header o valori di autorizzazione;
- cookie;
- password e secret;
- credenziali incorporate negli URL.

I messaggi sono limitati a 500 caratteri. Il log non deve contenere testo integrale delle fonti, cookie di navigazione,
prompt riservati, chiavi API o output di comandi non necessari alla diagnosi.

## Consultazione

Lo stato sintetico corrente:

```powershell
./scripts/publish-guide.ps1 status -RunId editorial-20260924
```

Sessione, audit e risultato finale insieme:

```powershell
./scripts/publish-guide.ps1 log -RunId editorial-20260924
```

Entrambi i comandi sono in sola lettura e non aggiungono eventi.

## Esiti non conclusivi

Una condizione `STOP-*` aggiorna la sessione con classificazione, retry, notifica e recupero ricavati dal catalogo delle
condizioni di arresto. Il tentativo viene conservato nell'audit, ma il risultato finale non viene congelato finché il run
non è pubblicato, annullato in sicurezza o concluso come `no_op`. In questo modo una correzione pre-commit non può
riscrivere retroattivamente gli eventi già avvenuti.
