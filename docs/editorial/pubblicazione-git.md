# Pubblicazione Git sicura e ripetibile

## Obiettivo

Il processo pubblica una sola guida per esecuzione su `main`, senza force push e senza attendere Railway. Se una condizione di sicurezza non è rispettata, si arresta lasciando prove e stato locale in `.git/editorial-publication/`.

## Flusso a due fasi

Prima dell'avvio si ricava l'identificatore deterministico della finestra giornaliera. Due tentativi nello stesso giorno
usano quindi lo stesso ID e non possono creare due pubblicazioni equivalenti:

```powershell
./scripts/publish-guide.ps1 run-id
```

### 1. Avvio prima della ricerca e della scrittura

```powershell
./scripts/publish-guide.ps1 start -RunId 20260924-guide-0001 -ContentId GUIDE-0001
```

`start` richiede un working tree pulito e il branch `main`, acquisisce un lock atomico, esegue `fetch`, consente soltanto un aggiornamento fast-forward e fotografa lo SHA di `origin/main`. Il `RunId` deve essere univoco e lungo almeno sei caratteri.

L'output contiene un `owner_token` casuale. Il task deve conservarlo in memoria e passarlo ai comandi successivi; non
deve inserirlo nei contenuti, nei commit o nei log pubblici.

Se una nuova guida deve aggiornare i link interni di guide già pubblicate, queste vanno dichiarate esplicitamente:

```powershell
./scripts/publish-guide.ps1 start -RunId 20260925-guide-0002 -ContentId GUIDE-0002 -RelatedContentId GUIDE-0001
```

Sono ammesse come correlate soltanto guide con stato `pushed_to_main`.

### 2. Pubblicazione dopo ricerca, generazione e aggiornamento del backlog

Prima della pubblicazione, la voce della guida deve avere stato `pushed_to_main` nel backlog. Questo stato descrive il contenuto del commit che diventerà vero quando il push sarà verificato.

```powershell
./scripts/publish-guide.ps1 publish -RunId 20260924-guide-0001 -OwnerToken <token-ricevuto-da-start>
```

`publish`:

1. accetta soltanto i file previsti dalla allowlist della sessione;
2. blocca modifiche a codice, script, test e politiche editoriali;
3. esegue validazione editoriale in modalità `publication`, test Python e test Java;
4. verifica che `origin/main` non sia avanzato dall'avvio;
5. crea un solo commit con messaggio deterministico;
6. ricontrolla il remoto e invia un push non forzato;
7. verifica che lo SHA pubblicato sia quello del commit locale;
8. registra l'esito, senza controllare Railway.

## Comandi operativi

Consultare lo stato di un'esecuzione non modifica il repository:

```powershell
./scripts/publish-guide.ps1 status -RunId 20260924-guide-0001
```

Durante ricerca e redazione il task rinnova il lock almeno ogni 30 minuti:

```powershell
./scripts/publish-guide.ps1 heartbeat -RunId 20260924-guide-0001 -OwnerToken <token>
```

Alla conclusione di una fase registra un checkpoint monotono. Gli ID delle fonti e i controlli confluiscono nella
cronologia strutturata del run:

```powershell
./scripts/publish-guide.ps1 checkpoint -RunId 20260924-guide-0001 -OwnerToken <token> `
  -Phase research -SourceId SRC-2026-0001 -Check "fonte primaria verificata"
```

Annullare un'esecuzione è possibile soltanto se non esistono modifiche e non è stato creato alcun commit:

```powershell
./scripts/publish-guide.ps1 cancel -RunId 20260924-guide-0001 -OwnerToken <token>
```

## Ripetibilità e recupero

- Un secondo `start` mentre il lock è fresco termina con `STOP-ACTIVE-EDITORIAL-LOCK`, senza modifiche.
- Ripetere `publish` dopo un successo restituisce lo stesso esito senza richiedere il token e senza creare commit o push aggiuntivi.
- Se il push è riuscito ma il processo si è interrotto prima di aggiornare il log, il comando confronta lo SHA remoto e completa la sessione.
- Se l'esecuzione si arresta prima del commit, il lock resta attivo: dopo la correzione si può ripetere `publish` con lo stesso `RunId`.
- Se esiste un commit locale non pubblicato, il retry ordinario si arresta. Dopo la verifica consapevole di HEAD, diff,
  test e remoto, il recupero va autorizzato esplicitamente con `publish -ManualRecovery`.
- Un lock è classificato come obsoleto dopo 480 minuti senza heartbeat. Non viene mai rimosso o acquisito nuovamente
  in automatico.
- Dopo aver verificato che non esistano modifiche o commit locali, un lock obsoleto può essere chiuso manualmente con
  `cancel -ManualRecovery`. Se esistono artefatti, il comando rifiuta la rimozione.

## Identità e checkpoint

Le fasi ammesse sono, nell'ordine: `selection`, `research`, `drafting`, `generation`, `validation`, `commit`, `push` e
`complete`. I checkpoint pubblici accettano le quattro fasi comprese tra ricerca e validazione e non consentono salti o
regressioni. Ogni sessione conserva almeno:

- ID del run, guida e slug;
- orari di avvio, heartbeat e fine;
- fase raggiunta, fonti e controlli registrati;
- HEAD iniziale, SHA remoto iniziale, file modificati e commit;
- esiti di commit e push;
- eventuale codice di arresto;
- `deployment_checked: false`.

## Perimetro dei file

Per la guida selezionata sono autorizzati manifesto, corpo HTML e pagina generata. Sono inoltre ammessi backlog, registro delle fonti, sitemap e indice della home. Le guide correlate devono essere dichiarate a `start`. Il limite complessivo predefinito è 12 file.

La configurazione vincolante è in `docs/editorial/git-publication-policy.json`; le condizioni di arresto sono in `docs/editorial/condizioni-arresto.json`.

## Confine con il deploy

Il processo termina dopo la verifica dello SHA su `origin/main`. `deployment_checked` resta sempre `false`: controllo di Railway e verifica pubblica della pagina rimangono attività manuali esterne a questa automazione.
