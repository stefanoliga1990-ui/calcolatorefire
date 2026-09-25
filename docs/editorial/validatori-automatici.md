# Validatori automatici editoriali

Versione: 1.0

Stato: obbligatori prima della pubblicazione

Ultimo aggiornamento: 24 settembre 2026

## 1. Scopo

I validatori automatici impediscono che una guida venga pubblicata con dati editoriali incoerenti, fonti non
tracciabili, output non rigenerabile o difetti SEO tecnici rilevabili in modo deterministico. Sono controlli locali,
non interattivi e privi di richieste verso servizi esterni.

Il comando principale è `scripts/validate-editorial.ps1`. Un solo errore produce un codice di uscita diverso da zero e
blocca le fasi successive. Il superamento dei controlli non sostituisce lo studio delle fonti o il giudizio editoriale.

## 2. Modalità

### Development

```powershell
.\scripts\validate-editorial.ps1
```

Controlla l'intero repository durante la preparazione. Una guida sorgente può non avere ancora un output HTML, ma ogni
output eventualmente presente deve coincidere esattamente con il rendering atteso.

### Publication

```powershell
.\scripts\validate-editorial.ps1 -Mode publication
```

Applica gli stessi controlli e richiede inoltre:

- l'HTML generato per ogni guida sorgente;
- corrispondenza byte per byte tra sorgenti e output;
- firma autore presente nelle guide, senza dipendere da una pagina profilo;
- tutti i requisiti di pagina, collegamento e sitemap previsti per la pubblicazione.

Questa è la modalità obbligatoria dopo la generazione e prima del commit editoriale.

### Publication con suite completa

```powershell
.\scripts\validate-editorial.ps1 -Mode publication -IncludeTests
```

Dopo la validazione esegue i test Python del sistema editoriale e l'intera suite Maven dell'applicazione. Un fallimento
in una qualsiasi fase conserva il codice di uscita non zero e impedisce commit e push.

## 3. Controlli sul backlog

Il validatore verifica:

- struttura e campi ammessi dal contratto JSON;
- versione dello schema e politica di selezione concordata;
- formati di ID, slug, cluster, priorità, stato e modalità di esecuzione;
- coerenza tra `execution_mode` e `automation_eligible`;
- unicità di ID, slug, sequenza e query primaria;
- esistenza di parent e dipendenze;
- assenza di autoriferimenti e cicli nelle dipendenze;
- coerenza tra tipo di contenuto, prefisso dell'ID e URL;
- presenza e unicità degli elenchi richiesti dal brief.

Questi controlli impediscono selezioni ambigue, guide duplicate per errore e dipendenze impossibili da soddisfare.

## 4. Controlli sul registro delle fonti

Per ogni fonte vengono verificati:

- campi obbligatori e assenza di campi inattesi;
- identificatore, livello, stato, tipo e URL HTTPS;
- validità e ordine delle date di accesso;
- applicabilità, locator e verifica del contenuto originale;
- utilizzi collegati a contenuti realmente presenti nel backlog;
- unicità delle coppie contenuto-affermazione;
- vincoli specifici delle fonti approvate e dei livelli C e D;
- riferimenti e risoluzione dei conflitti;
- formato e presenza delle eventuali copie locali;
- unicità degli identificatori;
- coerenza di `next_id_by_year` con l'ultimo ID assegnato.

Il controllo generale del registro è completato dai controlli più severi del generatore sulle sole fonti usate da una
guida: stato approvato, accesso integrale, fonte originale, uso registrato, importanza centrale e conflitti irrisolti.

## 5. Controlli sulle guide sorgente

Ogni coppia `GUIDE-NNNN.json` e `GUIDE-NNNN.body.html` attraversa gli stessi controlli del generatore:

- corrispondenza con backlog e stato ammesso;
- completezza e limiti dei metadati;
- fonti dichiarate, registrate e citate nel corpo;
- HTML semantico ammesso e correttamente annidato;
- ID, titoli e riferimenti ARIA coerenti;
- link interni pubblicabili;
- assenza di segnaposto;
- unicità di title, meta description e canonical.

Se l'output esiste, il validatore rigenera la pagina in memoria e la confronta byte per byte con il file versionato. Una
modifica manuale dell'HTML finale o un output obsoleto provocano quindi un errore di drift.

Una pagina presente in `static/guida/` senza i corrispondenti sorgenti viene sempre rifiutata.

Il validatore controlla inoltre il [catalogo delle condizioni di arresto](condizioni-arresto.md): schema e versione,
codici e ordine univoci, classificazione, retry, notifiche, stato Git atteso e presenza di tutte le condizioni minime
richieste dal contratto.

## 6. Controlli SEO e delle pagine

Per homepage, metodologia e guide vengono controllati:

- lingua italiana dichiarata;
- un title tra 10 e 65 caratteri;
- una meta description tra 70 e 160 caratteri;
- un solo H1;
- un solo canonical, coincidente con la rotta pubblica;
- `og:url` coerente;
- assenza di `noindex` e `nofollow`;
- assenza di ID HTML duplicati;
- JSON-LD valido nelle pagine editoriali;
- ancore locali esistenti;
- link interni e asset locali risolvibili;
- uso di HTTPS per i collegamenti esterni;
- unicità globale di title, description e canonical.

Il controllo è statico: dimostra che i riferimenti esistono nel repository, non che un sito esterno sia raggiungibile.

## 7. Sitemap e robots

La sitemap deve contenere esattamente una voce per ogni pagina pubblica riconosciuta, senza URL aggiuntivi, mancanti o
duplicati. Per le guide, `lastmod` deve coincidere con `dates.updated` del manifesto.

`robots.txt` deve consentire la scansione generale e indicare la sitemap canonica del dominio.

## 8. Sequenza di utilizzo

Durante una esecuzione editoriale:

1. usare la modalità development mentre si preparano registro, manifesto e corpo;
2. eseguire il generatore in modalità `-CheckOnly`;
3. generare la pagina e aggiornare la sitemap;
4. eseguire `publication`;
5. eseguire `publication -IncludeTests` prima del commit;
6. fermarsi senza commit o push se un comando restituisce errore.

Il validatore non corregge automaticamente i dati: indica la violazione e lascia la modifica al processo editoriale.

## 9. Test dei validatori

I test dedicati si eseguono con:

```powershell
python -m unittest scripts/editorial/test_validate_editorial.py
```

Oltre al caso positivo sul repository, la suite introduce deliberatamente cicli nelle dipendenze, contatori fonti
errati e discrepanze nella sitemap per verificare che tali difetti vengano effettivamente bloccati.

## 10. Confini

I validatori non:

- interrogano motori di ricerca, Railway o Google Search Console;
- verificano il deploy pubblico;
- decidono se una fonte è autorevole soltanto perché formalmente valida;
- misurano originalità, chiarezza o utilità con una soglia automatica;
- eseguono commit o push;
- cambiano backlog, registro, sorgenti o pagine.

La verifica Railway resta esclusa come stabilito dal contratto dell'automazione.
