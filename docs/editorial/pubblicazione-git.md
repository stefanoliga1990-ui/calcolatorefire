# Pubblicazione Git sicura e ripetibile

## Obiettivo

Il processo pubblica una sola guida per esecuzione su `main`, senza force push e senza attendere Railway. Se una condizione di sicurezza non è rispettata, si arresta lasciando prove e stato locale in `.git/editorial-publication/`.

## Flusso a due fasi

### 1. Avvio prima della ricerca e della scrittura

```powershell
./scripts/publish-guide.ps1 start -RunId 20260924-guide-0001 -ContentId GUIDE-0001
```

`start` richiede un working tree pulito e il branch `main`, acquisisce un lock atomico, esegue `fetch`, consente soltanto un aggiornamento fast-forward e fotografa lo SHA di `origin/main`. Il `RunId` deve essere univoco e lungo almeno sei caratteri.

Se una nuova guida deve aggiornare i link interni di guide già pubblicate, queste vanno dichiarate esplicitamente:

```powershell
./scripts/publish-guide.ps1 start -RunId 20260925-guide-0002 -ContentId GUIDE-0002 -RelatedContentId GUIDE-0001
```

Sono ammesse come correlate soltanto guide con stato `pushed_to_main`.

### 2. Pubblicazione dopo ricerca, generazione e aggiornamento del backlog

Prima della pubblicazione, la voce della guida deve avere stato `pushed_to_main` nel backlog. Questo stato descrive il contenuto del commit che diventerà vero quando il push sarà verificato.

```powershell
./scripts/publish-guide.ps1 publish -RunId 20260924-guide-0001
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

Annullare un'esecuzione è possibile soltanto se non esistono modifiche e non è stato creato alcun commit:

```powershell
./scripts/publish-guide.ps1 cancel -RunId 20260924-guide-0001
```

## Ripetibilità e recupero

- Ripetere `publish` dopo un successo restituisce lo stesso esito senza creare commit o push aggiuntivi.
- Se il push è riuscito ma il processo si è interrotto prima di aggiornare il log, il comando confronta lo SHA remoto e completa la sessione.
- Se l'esecuzione si arresta prima del commit, il lock resta attivo: dopo la correzione si può ripetere `publish` con lo stesso `RunId`.
- Se esiste un commit locale non pubblicato, il comando può riprendere il push soltanto quando HEAD, working tree e remoto coincidono con lo stato registrato.
- Un lock non va cancellato manualmente senza aver verificato sessione, working tree, HEAD e remoto.

## Perimetro dei file

Per la guida selezionata sono autorizzati manifesto, corpo HTML e pagina generata. Sono inoltre ammessi backlog, registro delle fonti, sitemap e indice della home. Le guide correlate devono essere dichiarate a `start`. Il limite complessivo predefinito è 12 file.

La configurazione vincolante è in `docs/editorial/git-publication-policy.json`; le condizioni di arresto sono in `docs/editorial/condizioni-arresto.json`.

## Confine con il deploy

Il processo termina dopo la verifica dello SHA su `origin/main`. `deployment_checked` resta sempre `false`: controllo di Railway e verifica pubblica della pagina rimangono attività manuali esterne a questa automazione.
