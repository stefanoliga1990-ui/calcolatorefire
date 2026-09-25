# Template editoriale delle guide

Versione: 1.0

Stato: vincolante per la guida pilota e per le guide automatiche

Ultimo aggiornamento: 24 settembre 2026

## 1. Funzione del template

Questo documento definisce il percorso editoriale comune a tutte le guide di Calcolo FIRE Italia: dalla voce del backlog
ai due sorgenti consumati dal generatore. Non impone una lunghezza o un numero rigido di sezioni; impone invece che la
domanda del lettore, le affermazioni, le fonti e il valore originale siano identificabili prima della scrittura.

Il template va usato insieme alla [politica editoriale](politica-editoriale.md), alla
[politica delle fonti](politica-fonti.md), al [profilo autore](profilo-autore.md) e al
[sistema di generazione](sistema-generazione-guide.md). In caso di conflitto prevale la regola più prudente.

## 2. Scheda preparatoria obbligatoria

Prima di redigere il testo, compilare mentalmente o nelle note temporanee dell'esecuzione questa scheda usando soltanto
dati presenti nel backlog e risultati verificati durante la ricerca.

### Identità e intento

| Campo | Contenuto richiesto |
| --- | --- |
| ID | Identificatore esatto `GUIDE-NNNN` del backlog. |
| Slug | URL approvato nel backlog, senza varianti. |
| Domanda principale | La domanda a cui la guida deve rispondere in una frase. |
| Intento di ricerca | Il bisogno pratico espresso dal lettore, non una lista di parole chiave. |
| Risultato per il lettore | Ciò che saprà comprendere, calcolare o verificare dopo la lettura. |
| Risposta breve | Risposta diretta in due o tre frasi, completa anche senza leggere il resto. |
| Valore originale | Collegamento specifico con Italia, simulatore, formule, esempi o limiti. |
| Fuori perimetro | Aspetti che la guida non tratta e pagine alle quali non deve sovrapporsi. |

La redazione deve fermarsi se domanda principale, risultato e valore originale non coincidono con il backlog o se il
contenuto previsto duplica sostanzialmente una pagina esistente.

### Mappa affermazioni-fonti

Preparare una riga per ogni affermazione centrale, quantitativa, normativa, fiscale, previdenziale o storica:

| Affermazione da sostenere | Tipo | Fonte registrata | Livello | Locator | Data o periodo valido | Esito |
| --- | --- | --- | --- | --- | --- | --- |
| Formulazione precisa, non il nome del tema | fatto, dato, norma, formula o interpretazione | `SRC-AAAA-NNNN` | A, B o C | sezione, articolo, pagina o serie | data verificata | confermata, limitata, conflittuale |

Regole:

- ogni affermazione centrale deve avere almeno una fonte adeguata e registrata;
- una fonte di livello C può sostenere soltanto il contesto;
- norme, aliquote, soglie e prestazioni devono indicare la data o il periodo di validità;
- una conclusione ottenuta dal progetto deve essere presentata come calcolo o interpretazione, non come fatto esterno;
- un conflitto sostanziale irrisolto blocca la guida;
- ogni fonte elencata nel manifesto deve comparire almeno una volta vicino all'affermazione che sostiene.

### Progetto dell'esempio

Quando un esempio aiuta davvero a rispondere alla domanda, definirlo prima della scrittura:

| Elemento | Regola |
| --- | --- |
| Obiettivo | Illustrare un passaggio specifico, non simulare una persona reale. |
| Input | Elencare tutti i valori determinanti con unità e periodo. |
| Ipotesi | Separare rendimento, inflazione, imposte e altre semplificazioni. |
| Calcolo | Usare la formula documentata o un risultato riproducibile del simulatore. |
| Arrotondamento | Dichiarare dove viene applicato e non mostrare precisione apparente. |
| Interpretazione | Spiegare che cosa dimostra e che cosa non dimostra il risultato. |

Un esempio non può essere presentato come previsione, rendimento atteso, raccomandazione personale o risultato garantito.

## 3. Struttura della pagina

### Elementi compilati nel manifesto

Il file `GUIDE-NNNN.json` deve contenere:

1. `seo.title`: title unico, descrittivo e coerente con l'intento;
2. `seo.description`: sintesi utile della risposta, senza promesse o formule sensazionalistiche;
3. `seo.og_title` e `seo.og_description`: versione leggibile nella condivisione;
4. `page.kicker`: etichetta breve del tipo o cluster della guida;
5. `page.h1`: titolo visibile che espone chiaramente il tema;
6. `page.lead`: introduzione che identifica domanda, perimetro e utilità della pagina;
7. `summary_points`: da tre a otto conclusioni comprensibili senza il resto dell'articolo;
8. `source_ids`: soltanto fonti approvate, consultate e usate nel testo;
9. `internal_links`: collegamenti pertinenti con spiegazione del motivo;
10. `cta`: azione coerente con il contenuto, normalmente l'uso del simulatore.

### Elementi redatti nel corpo

Il file `GUIDE-NNNN.body.html` contiene soltanto sezioni editoriali. La sequenza consigliata è:

1. **Contesto e definizioni:** chiarisce i termini indispensabili e delimita il problema;
2. **Spiegazione principale:** risponde progressivamente alla domanda e distingue fatti, ipotesi e interpretazioni;
3. **Metodo o passaggi operativi:** mostra come ragionare, calcolare o usare il simulatore;
4. **Esempio riproducibile:** applica il metodo con input, unità e ipotesi espliciti, se utile;
5. **Fattori che cambiano il risultato:** confronta scenari, eccezioni o sensibilità rilevanti;
6. **Limiti e rischi:** rende visibili incertezze, semplificazioni e casi non coperti;
7. **Conclusione operativa:** riassume come applicare prudentemente ciò che è stato spiegato.

La sequenza può essere adattata all'intento. Sono obbligatorie almeno due sezioni sostanziali, ma è vietato creare
sezioni vuote o ripetitive soltanto per rispettare lo schema consigliato.

## 4. Modello di ogni sezione

Ogni sezione deve avere un titolo che anticipa la risposta fornita e una sola responsabilità principale:

```html
<section class="content-section" id="titolo-breve" aria-labelledby="titolo-breve-title">
    <h2 id="titolo-breve-title">Titolo che anticipa il contenuto</h2>
    <p>Frase iniziale che fornisce subito il punto principale della sezione.</p>
    <p>Spiegazione, prova, esempio o conseguenza necessaria per comprenderlo.</p>
</section>
```

Un sottotitolo `h3` è ammesso soltanto quando divide aspetti realmente distinti della stessa sezione. Tabelle, formule,
liste e riquadri devono semplificare un rapporto difficile da spiegare in prosa, non decorare la pagina.

## 5. Citazioni nel testo

Una fonte deve essere collegata vicino all'affermazione sostenuta usando URL e identificatore esatti del registro:

```html
<p>
    L'affermazione verificabile è sostenuta dalla
    <a href="https://dominio-istituzionale.it/documento" data-source-id="SRC-AAAA-NNNN">fonte primaria</a>.
</p>
```

Il testo dell'anchor deve descrivere l'ente, il documento o il dato. Sono vietati anchor generici come “clicca qui”,
liste di fonti decorative e citazioni che non sostengono realmente la frase vicina.

Il template di pagina genera automaticamente l'elenco completo delle fonti; non deve essere ricreato nel corpo.

## 6. Formule, tabelle e riquadri

Per una formula controllabile:

```html
<pre class="formula"><code>risultato = input × fattore</code></pre>
```

Ogni simbolo deve essere spiegato in prosa e l'unità di misura deve risultare non ambigua. Una formula interna al
simulatore deve essere coerente con la metodologia e con l'implementazione corrente.

Per un'avvertenza che modifica l'interpretazione:

```html
<aside class="content-notice warning" aria-labelledby="avvertenza-title">
    <h3 id="avvertenza-title">Limite da considerare</h3>
    <p>Spiegazione concreta del limite e delle sue conseguenze.</p>
</aside>
```

Una tabella è appropriata per confrontare almeno due opzioni su più attributi. Non deve contenere dati privi di fonte o
duplicare integralmente paragrafi già presenti.

## 7. Collegamenti interni

Ogni manifesto deve includere almeno:

- `/metodologia`, quando formule, ipotesi o limiti del simulatore sono pertinenti;
- `/`, per consentire al lettore di verificare uno scenario nel simulatore;
- la pagina padre o una guida correlata già pubblicata, quando esiste e aggiunge un passaggio utile.

Il campo `reason` deve spiegare il valore del collegamento. L'anchor deve descrivere la destinazione. Non inserire link a
guide future, URL non canonici o collegamenti scelti soltanto per ripetere parole chiave.

## 8. Contenuti ad alto rischio

Per fiscalità, previdenza, norme o decisioni personali:

- usare fonti istituzionali applicabili al contesto italiano;
- indicare nel testo data, periodo di validità e principali esclusioni;
- distinguere norma, prassi, interpretazione e semplificazione del simulatore;
- evitare qualsiasi indicazione personalizzata su cosa comprare, vendere, dichiarare o richiedere;
- esplicitare quando serve la verifica di un professionista qualificato;
- bloccare la pubblicazione quando la regola non può essere rappresentata senza ambiguità sostanziali.

La nota informativa generale viene aggiunta dal generatore, ma non sostituisce le avvertenze specifiche necessarie nel
corpo della guida.

## 9. Elementi generati e da non duplicare

Il corpo non deve contenere:

- `H1`, title, meta description, canonical o dati strutturati;
- breadcrumb o indice;
- box autore, firma o biografia;
- nota sul processo automatizzato;
- nota informativa generale;
- elenco finale delle fonti;
- call to action finale o footer.

Questi elementi provengono dal template tecnico per restare identici e verificabili in tutte le guide.

## 10. Controllo editoriale prima della generazione

La guida è pronta per `-CheckOnly` soltanto se tutte le risposte seguenti sono positive:

- risponde alla domanda principale già nel lead e nella sintesi;
- aggiunge il valore originale promesso dal backlog;
- non sovrappone sostanzialmente una guida esistente;
- distingue fatti, ipotesi, esempi e interpretazioni;
- collega ogni affermazione rilevante a una fonte adeguata;
- rende riproducibili formule ed esempi;
- mostra limiti, rischi e condizioni che cambiano la risposta;
- usa italiano naturale, titoli descrittivi e paragrafi leggibili;
- evita promesse, urgenza artificiale, keyword stuffing e consulenza personale;
- contiene soltanto link interni già pubblicabili e fonti HTTPS registrate;
- non contiene segnaposto, note di lavorazione o testo provvisorio.

Il superamento di questa verifica autorizza esclusivamente la validazione tecnica. Non equivale a pubblicazione.

## 11. File starter

I file copiabili si trovano in `content/guides/_template/`:

- `guide-manifest.template.json` per metadati, sintesi, fonti e collegamenti;
- `guide-body.template.html` per la struttura semantica del corpo.

Per iniziare una guida, copiarli nella cartella `content/guides/` rinominandoli con l'ID reale, quindi sostituire ogni
segnaposto. I file starter non sono contenuti pubblicabili e non vengono selezionati dal comando `-All`.
