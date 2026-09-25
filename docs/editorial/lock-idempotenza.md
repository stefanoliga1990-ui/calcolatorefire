# Lock, checkpoint e idempotenza delle esecuzioni editoriali

## Scopo

Questo runbook impedisce che due esecuzioni lavorino contemporaneamente sulla stessa coda editoriale e che un retry
produca una seconda guida o un secondo commit equivalente. Lo stato operativo è locale al clone e viene conservato in
`.git/editorial-publication/`; non entra nei commit e non può modificare il sito.

## Identità del run

La schedulazione ogni 6 ore usa `editorial-AAAAMMGG-HH`, calcolato sul calendario `Europe/Rome`; `HH` vale `00`, `06`,
`12` o `18`:

```powershell
./scripts/publish-guide.ps1 run-id
```

La stessa finestra restituisce lo stesso identificatore. Un run concluso con successo viene riconosciuto dalla
cronologia locale e dal fatto che il relativo commit è ancora raggiungibile da `origin/main`.

## Acquisizione esclusiva

`start` crea `lock.json` con apertura atomica: due processi non possono acquisirlo entrambi. Il lock contiene ID del run,
guida, fase, istante di acquisizione, ultimo heartbeat e un token proprietario casuale. Il token è necessario per ogni
operazione mutante successiva; `status` rimane in sola lettura e non lo espone.

Gli esiti possibili quando il file esiste già sono:

| Stato | Esito | Azione automatica |
| --- | --- | --- |
| heartbeat entro 480 minuti | `STOP-ACTIVE-EDITORIAL-LOCK` | Terminare e riprovare alla schedulazione successiva. |
| heartbeat assente, futuro o più vecchio di 480 minuti | `STOP-STALE-EDITORIAL-LOCK` | Nessuna; richiedere intervento manuale. |
| file illeggibile o incoerente | `STOP-STALE-EDITORIAL-LOCK` | Nessuna; richiedere intervento manuale. |

Un lock obsoleto non viene mai sovrascritto o rimosso automaticamente.

## Heartbeat e checkpoint

Durante attività lunghe il proprietario aggiorna l'heartbeat almeno ogni 30 minuti. Al termine di ogni fase registra un
checkpoint nell'ordine `selection → research → drafting → generation → validation → commit → push → complete`.
Regressioni e salti effettuati dai comandi pubblici sono rifiutati.

Il checkpoint può aggiungere ID di fonti e controlli sintetici; duplicati identici vengono eliminati mantenendo l'ordine.
La cronologia registra inoltre file modificati, SHA iniziali, commit, push, orari ed eventuale codice `STOP-*`.

## Regole di retry

- Un duplicato mentre il run è attivo non riceve il token e non può proseguire.
- Un run già pubblicato restituisce lo stesso risultato con `idempotent_replay: true`; non crea commit o push.
- Se il commit risulta già nella storia di `origin/main`, il run viene concluso come pubblicato anche quando il processo
  precedente non aveva salvato l'ultimo checkpoint.
- Un commit soltanto locale non viene inviato da un retry ordinario. Dopo una verifica umana si usa `-ManualRecovery`.
- Prima del commit, una correzione conserva il medesimo run e ripete validatori e controlli sullo stato corrente.
- `cancel -ManualRecovery` rimuove un lock obsoleto soltanto se HEAD è quello iniziale, il working tree è pulito e non
  esiste un commit locale associato.

## Invarianti

- Il token non va copiato in contenuti, messaggi di commit o log pubblici.
- `deployment_checked` resta sempre `false`.
- Il rilascio del lock avviene soltanto dopo push verificato o annullamento sicuro.
- Errori dopo il commit preservano lock, SHA e prove per il recupero manuale.
- Nessuna procedura di recupero può usare force push, reset distruttivi o rimozione cieca del lock.

I comandi completi sono documentati in [Pubblicazione Git sicura e ripetibile](pubblicazione-git.md); timeout e frequenza
sono vincolati da [git-publication-policy.json](git-publication-policy.json).
Formato, redazione e consultazione della cronologia sono descritti nel [log delle esecuzioni](log-esecuzioni.md).
