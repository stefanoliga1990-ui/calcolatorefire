# Sistema di generazione delle guide

Versione: 1.0

Stato: pronto per il processo pilota manuale

Ultimo aggiornamento: 24 settembre 2026

## 1. Scopo

Il sistema trasforma una guida redatta in due sorgenti controllabili in una pagina HTML completa, coerente con la
politica editoriale e pronta per essere servita all'URL canonico previsto dal backlog. Non svolge ricerca, non inventa
fonti e non decide autonomamente quali contenuti pubblicare.

La generazione è deterministica: a parità di manifesto, corpo, template, backlog e registro delle fonti produce la
stessa pagina. La pagina finale non è la fonte editoriale da modificare manualmente; ogni correzione deve essere fatta
nei sorgenti e rigenerata.

## 2. Componenti

| Componente | Responsabilità |
| --- | --- |
| `content/guides/guide.schema.json` | Contratto formale del manifesto di una guida. |
| `content/guides/guide-page.template.html` | Struttura uniforme della pagina pubblica. |
| `docs/editorial/template-editoriale-guida.md` | Metodo vincolante per progettare e redigere il contenuto. |
| `content/guides/_template/` | File starter copiabili per manifesto e corpo. |
| `content/guides/GUIDE-NNNN.json` | Metadati, sintesi, fonti, link interni e CTA di una singola guida. |
| `content/guides/GUIDE-NNNN.body.html` | Corpo originale della guida in HTML semantico limitato. |
| `scripts/editorial/generate_guide.py` | Validazione semantica, rendering e aggiornamento della sitemap. |
| `scripts/generate-guide.ps1` | Comando PowerShell destinato al processo manuale e all'automazione locale. |
| `scripts/validate-editorial.ps1` | Validazione complessiva di dati editoriali, output, SEO e sitemap. |
| `GuidePageController` | Pubblicazione delle pagine all'URL pulito `/guida/<slug>`. |

## 3. Flusso obbligatorio

Per una singola guida il processo deve avvenire in quest'ordine:

1. selezionare la voce dal backlog secondo il contratto dell'automazione;
2. portare la voce in stato `in_progress`, oppure mantenerla `pilot` per la prima esecuzione manuale;
3. cercare, studiare e registrare le fonti, collegando ogni utilizzo al `content_id`;
4. creare il manifesto `GUIDE-NNNN.json` conforme allo schema;
5. redigere `GUIDE-NNNN.body.html` secondo le regole descritte nel README della cartella;
6. eseguire prima la modalità `CheckOnly`;
7. correggere ogni errore: un errore del generatore è bloccante;
8. eseguire la generazione effettiva;
9. eseguire i [validatori automatici](validatori-automatici.md), i test e i controlli del runbook;
10. aggiornare lo stato nel backlog soltanto secondo l'esito Git previsto dal contratto.

## 4. Comandi

Validazione senza scritture:

```powershell
.\scripts\generate-guide.ps1 -Manifest content/guides/GUIDE-0001.json -CheckOnly
```

Generazione della pagina e aggiornamento della sitemap:

```powershell
.\scripts\generate-guide.ps1 -Manifest content/guides/GUIDE-0001.json
```

Controllo di tutte le guide sorgente già presenti:

```powershell
.\scripts\generate-guide.ps1 -All -CheckOnly
```

Il corpo viene cercato automaticamente accanto al manifesto con suffisso `.body.html`. L'opzione `-Body` è disponibile
solo per diagnosi o test espliciti e non deve essere usata per separare stabilmente i due sorgenti.

## 5. Controlli bloccanti

Il generatore termina con codice diverso da zero e non scrive la pagina quando rileva almeno una di queste condizioni:

- manifesto incompleto, campi aggiuntivi o valori fuori dai limiti dello schema;
- `content_id` o slug assente, duplicato o non coerente con il backlog;
- guida non in stato `pilot` o `in_progress`;
- date future, data di aggiornamento anteriore alla pubblicazione o segnaposto irrisolti;
- title, meta description o canonical già usati da un'altra pagina;
- fonte assente, non approvata, non verificata integralmente o con conflitto irrisolto;
- fonte priva di un utilizzo registrato per la guida o assenza di almeno una fonte centrale;
- fonte dichiarata nel manifesto ma non citata vicino ad almeno un'affermazione del corpo;
- URL della citazione diverso da quello presente nel registro;
- link esterno non HTTPS o privo di `data-source-id`;
- tag, attributi o classi non ammessi nel corpo;
- presenza nel corpo di `h1`, script, stili o metadati riservati al template;
- meno di due sezioni, ID duplicati o relazione errata tra `section`, `h2` e `aria-labelledby`;
- mancanza dei collegamenti alla metodologia e al simulatore;
- collegamento interno a una guida che non esiste ancora.

Questi controlli verificano la struttura e la tracciabilità, non la verità sostanziale del testo. La correttezza delle
affermazioni resta soggetta allo studio delle fonti, alle policy e alla checklist editoriale.

## 6. Elementi generati automaticamente

Il template produce in modo uniforme:

- `title`, meta description, canonical, Open Graph e Twitter Card;
- un solo `H1`, intestazione, date e tempo di lettura indicativo;
- markup JSON-LD `Article` e `BreadcrumbList`;
- breadcrumb e indice ricavati dai titoli delle sezioni;
- sintesi iniziale;
- collegamenti interni dichiarati nel manifesto;
- elenco finale delle fonti nell'ordine dichiarato;
- firma e descrizione canonica di Stefano Liga;
- nota sul processo editoriale automatizzato;
- nota informativa sui limiti del contenuto;
- call to action e footer con identificatore editoriale.

L'output viene scritto in `src/main/resources/static/guida/<slug>.html`. La scrittura è atomica e la sitemap viene
aggiornata senza duplicare un URL già presente.

## 7. Routing e canonicalizzazione

La pagina finale è servita soltanto come contenuto canonico a `/guida/<slug>`. Le varianti con slash finale o `.html`
ricevono un redirect permanente verso l'URL pulito. Slug malformati e guide inesistenti restituiscono `404`.

Il file HTML resta nella cartella statica come artefatto di build e revisione, ma il canonical e tutti i collegamenti
pubblici devono usare l'URL senza estensione.

## 8. Idempotenza e confini

Rigenerare una guida con sorgenti invariati non deve aggiungere una seconda voce alla sitemap. Il sistema non modifica
automaticamente backlog o registro fonti perché tali cambiamenti rappresentano decisioni editoriali e devono essere
espliciti nello stesso commit.

Il sistema non esegue commit, push, ricerca web, controllo Railway o richiesta di indicizzazione. Queste operazioni
appartengono alle fasi successive definite nel contratto e nel [runbook operativo](runbook-operativo.md).

## 9. Verifica del sistema

I test del generatore si eseguono con:

```powershell
python -m unittest scripts/editorial/test_generate_guide.py
```

I test applicativi verificano il routing con:

```powershell
.\mvnw.cmd test
```

La prima guida pilota deve attraversare l'intero flusso manualmente. Solo dopo la correzione e l'approvazione del pilot
questo sistema potrà essere richiamato dall'attività schedulata.
