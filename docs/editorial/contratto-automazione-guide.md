# Contratto dell'automazione editoriale

Versione: 0.1

Stato: pre-attivazione

Ultimo aggiornamento: 24 settembre 2026

## 1. Scopo

L'automazione deve pubblicare guide originali e utili sugli argomenti trattati da Calcolo FIRE Italia, aumentando nel tempo la copertura organica del sito senza sacrificare accuratezza, tracciabilità delle fonti e qualità editoriale.

Questo documento è la fonte di verità per il comportamento dell'automazione. La schedulazione non deve essere attivata finché tutti i criteri della sezione 13 non sono soddisfatti.

## 2. Configurazione concordata

| Voce | Regola |
| --- | --- |
| Progetto | Calcolo FIRE Italia |
| Autore pubblico | Stefano Liga |
| Modalità | Automatica completa, senza approvazione preventiva |
| Frequenza | Ogni giorno alle 05:00 |
| Fuso orario | Europe/Rome |
| Quantità massima | Una guida per esecuzione |
| Destinazione Git | Push diretto su `origin/main` |
| Controllo Railway | Escluso |
| Notifiche ordinarie | Escluse |
| Notifiche di errore | Consentite solo per esecuzioni fallite o che richiedono intervento |
| Revisione mensile delle fonti | Esclusa |
| Revisione trimestrale delle guide | Esclusa |

## 3. Perimetro di ogni esecuzione

Ogni esecuzione deve completare, nell'ordine, un solo ciclo editoriale:

1. verificare i prerequisiti tecnici e lo stato del repository;
2. selezionare la prima guida idonea dal backlog editoriale;
3. cercare fonti autorevoli e pertinenti;
4. studiare e confrontare le fonti, distinguendo fatti, ipotesi e interpretazioni;
5. redigere una guida originale secondo template e policy editoriali;
6. integrare pagina, metadati SEO, dati strutturati, collegamenti interni e sitemap;
7. eseguire tutti i controlli editoriali, SEO e tecnici previsti;
8. aggiornare backlog e registro editoriale nello stesso insieme di modifiche;
9. creare un commit atomico dedicato alla guida;
10. effettuare il push del commit su `origin/main`;
11. registrare l'esito dell'esecuzione e terminare senza attendere Railway.

L'automazione non deve iniziare una seconda guida nella stessa esecuzione, neppure se la prima termina rapidamente.

## 4. Regola di selezione

La guida deve essere scelta esclusivamente dal backlog editoriale approvato, rispettandone priorità e stato. Sono selezionabili soltanto gli elementi marcati come pronti e privi di dipendenze aperte.

Se non esistono guide idonee, l'esecuzione deve terminare senza modifiche, commit o pubblicazione. L'automazione non può inventare autonomamente nuovi argomenti per riempire il backlog.

## 5. Regole sulle fonti

La ricerca deve privilegiare fonti primarie e istituzionali. Ogni affermazione quantitativa, normativa, fiscale o previdenziale deve essere riconducibile a una fonte registrata e consultata durante l'esecuzione.

L'automazione deve fermarsi se:

- non trova fonti sufficienti a sostenere le affermazioni centrali;
- le fonti autorevoli sono in conflitto e il conflitto non può essere rappresentato correttamente;
- una fonte indispensabile non è accessibile o non consente di verificarne data e contenuto;
- il tema richiede una valutazione professionale individuale invece di informazione generale.

Gerarchia, freschezza, citazione, registrazione e condizioni di blocco sono definite nella [politica delle fonti](politica-fonti.md).

## 6. Regole editoriali

Ogni guida deve:

- rispondere a un intento di ricerca distinto;
- contenere testo originale, specifico per il pubblico italiano e realmente utile;
- dichiarare ipotesi, limiti e data di aggiornamento;
- separare chiaramente informazione, esempi e risultati del simulatore;
- evitare promesse di rendimento e indicazioni finanziarie personalizzate;
- attribuire la paternità a Stefano Liga secondo il [profilo autore approvato](profilo-autore.md);
- rispettare la [politica editoriale](politica-editoriale.md), il template, il glossario e le checklist del progetto.

Fiscalità e previdenza possono essere trattate solo come informazione generale basata su fonti istituzionali, con data di validità e avvertenza esplicita. In presenza di ambiguità sostanziali, la pubblicazione deve essere bloccata.

## 7. Requisiti SEO e tecnici

Prima della pubblicazione devono risultare validi almeno:

- URL stabile e non duplicato;
- `title`, meta description, `H1` e canonical univoci;
- struttura semantica corretta dei titoli;
- collegamenti interni pertinenti in entrata e in uscita;
- inserimento nella sitemap applicabile;
- dati strutturati previsti dal progetto;
- assenza di direttive che impediscano scansione o indicizzazione;
- rendering e contenuto principale accessibili senza dipendere da interazioni utente;
- test, build e validatori definiti dal runbook.

Il mancato superamento di un solo controllo obbligatorio impedisce commit e push.

## 8. Regole Git e pubblicazione

All'avvio l'automazione deve operare su `main`, aggiornato da `origin/main`, con working tree pulito e senza divergenze. Sono vietati force push, reset distruttivi e risoluzioni automatiche di conflitti non banali.

Le modifiche devono essere limitate alla singola guida e ai file strettamente necessari per pubblicarla e registrarne lo stato. Il commit deve essere atomico e riconoscibile come pubblicazione editoriale automatizzata.

Se `main` cambia durante l'esecuzione o il push viene rifiutato, l'automazione deve fermarsi senza forzare l'operazione. La guida si considera inviata alla pubblicazione soltanto quando il commit è presente su `origin/main`.

## 9. Significato di pubblicazione

Per questo processo, "pubblicata" significa che la guida è stata inviata con successo a `origin/main`. Non significa che Railway abbia concluso il deploy, che l'URL pubblico risponda correttamente o che Google abbia indicizzato la pagina.

Dopo il push l'automazione non deve:

- attendere il deploy Railway;
- interrogare Railway;
- verificare l'URL pubblico;
- richiedere manualmente l'indicizzazione in Google Search Console.

La verifica del deploy resta a carico di Stefano Liga.

## 10. Idempotenza e concorrenza

Ogni guida e ogni esecuzione devono avere un identificatore univoco. Prima di modificare il repository, l'automazione deve verificare che lo slug, l'identificatore e il contenuto pianificato non siano già presenti nel backlog, nel registro editoriale, nella sitemap o nella cronologia Git.

Deve inoltre essere previsto un meccanismo di esclusione che impedisca a due esecuzioni di pubblicare contemporaneamente. Un tentativo duplicato deve terminare senza creare una seconda pagina o un secondo commit equivalente.

## 11. Stato, log ed esito

Il registro editoriale nel repository deve essere aggiornato nello stesso commit della guida. Lo stato `pushed_to_main` è valido soltanto se quel commit risulta raggiungibile da `origin/main`.

L'esito finale dell'esecuzione deve riportare almeno:

- identificatore e orari di inizio e fine;
- guida selezionata e slug;
- fonti utilizzate;
- controlli eseguiti e relativo esito;
- file modificati;
- hash del commit, se creato;
- esito del push;
- `deployment_checked: false`;
- eventuale motivo di arresto.

Il risultato resta consultabile nella cronologia delle esecuzioni. Non è richiesta una notifica per le esecuzioni riuscite o per l'assenza di guide idonee; sono ammesse notifiche soltanto per fallimenti o interventi richiesti.

## 12. Condizioni di arresto

L'esecuzione deve fermarsi senza pubblicare quando si verifica almeno una delle seguenti condizioni:

- repository sporco, non aggiornabile con fast-forward o in stato inatteso;
- altra esecuzione editoriale in corso;
- backlog vuoto o privo di elementi idonei;
- argomento duplicato o intento di ricerca non sufficientemente distinto;
- fonti insufficienti, non verificabili o sostanzialmente discordanti;
- affermazioni fiscali o previdenziali non supportate in modo adeguato;
- violazione della policy editoriale o delle fonti;
- fallimento di test, build, controlli SEO o validatori;
- modifiche estranee alla guida selezionata;
- rifiuto del push o avanzamento concorrente di `origin/main`.

In caso di arresto sono vietati contenuti parziali su `main`. Gli artefatti locali eventualmente prodotti devono restare fuori dalla pubblicazione e il motivo deve essere registrato nell'esito dell'esecuzione.

## 13. Criteri di attivazione

La schedulazione può essere creata e attivata soltanto quando:

1. tutti gli step preliminari editoriali e tecnici sono completati;
2. backlog, policy, template, registro delle fonti, checklist e runbook sono versionati nel repository;
3. la pagina autore e la pagina metodologia sono disponibili;
4. una prima guida è stata prodotta manualmente con lo stesso processo end-to-end;
5. il processo manuale è stato corretto e approvato da Stefano Liga;
6. gli strumenti di validazione sono eseguibili in modo non interattivo;
7. il repository consente commit e push automatici su `main`;
8. il branch `guide-editoriali`, inclusa la prima guida, è stato integrato e pubblicato su `main`;
9. una simulazione senza pubblicazione ha dimostrato selezione, idempotenza e condizioni di arresto;
10. il prompt finale dell'automazione incorpora questo contratto senza ridurne i vincoli.

La sequenza "esecuzione manuale, perfezionamento, schedulazione" segue la procedura raccomandata nella documentazione ufficiale di [OpenAI sulle attività pianificate](https://developers.openai.com/training/walkthroughs/scheduled-tasks).

## 14. Gestione delle modifiche

Qualsiasi variazione a frequenza, autonomia, destinazione Git, controlli obbligatori o condizioni di arresto richiede una modifica esplicita di questo documento prima di aggiornare l'automazione. In caso di contrasto tra il prompt schedulato e questo contratto, prevale la versione del contratto presente su `main` all'inizio dell'esecuzione.
