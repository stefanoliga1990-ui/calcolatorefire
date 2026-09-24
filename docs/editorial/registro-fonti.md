# Registro delle fonti: istruzioni operative

Il registro ufficiale è il file [`registro-fonti.json`](registro-fonti.json). La sua struttura è definita da [`registro-fonti.schema.json`](registro-fonti.schema.json) e le fonti devono rispettare la [politica delle fonti](politica-fonti.md).

Il registro contiene soltanto fonti usate da guide, metodologia o altri contenuti pubblici; le fonti impiegate esclusivamente per definire le policy interne restano citate nelle policy. Gli identificatori `SRC-2026-0001` e `SRC-2026-0002` sono stati assegnati alle specifiche versionate su cui si basa la pagina Metodologia. Il prossimo identificatore disponibile è indicato in `next_id_by_year` nel registro JSON.

## Identificatori

Ogni fonte riceve un identificatore immutabile nel formato `SRC-AAAA-NNNN`:

- `AAAA` è l'anno in cui la fonte viene registrata, non necessariamente l'anno di pubblicazione;
- `NNNN` è il contatore annuale a quattro cifre;
- `next_id_by_year` contiene il prossimo numero disponibile per ogni anno;
- un identificatore eliminato o superato non deve essere riutilizzato.

Prima di creare un nuovo record occorre cercare corrispondenze per URL canonico, DOI, altro identificatore persistente, titolo, ente e versione.

## Quando riutilizzare o creare un record

Riutilizzare il record esistente e aggiungere un elemento a `usages` quando documento, versione e contenuto rilevante non sono cambiati.

Creare un nuovo record e marcare il precedente come `superseded` quando:

- una pagina modificabile cambia in modo sostanziale;
- viene pubblicata una nuova edizione o versione;
- cambia il periodo di vigenza rilevante;
- il dataset viene revisionato in modo da modificare l'affermazione sostenuta.

Un semplice nuovo accesso alla stessa fonte immutata aggiorna `dates.last_accessed_at` e `verification`, senza generare un nuovo identificatore.

## Stati

| Stato | Significato |
| --- | --- |
| `approved` | Fonte verificata e utilizzabile secondo la politica. |
| `superseded` | Fonte conservata per tracciabilità ma sostituita da una versione successiva. |
| `unavailable` | Fonte precedentemente registrata che non è più accessibile. |
| `rejected` | Fonte deliberatamente non ammessa e conservata solo per evitare riutilizzi errati. |

Una fonte `approved` deve avere almeno un utilizzo e il contenuto completo rilevante deve essere stato consultato. Una fonte di livello `D` può avere soltanto stato `rejected` e non può sostenere alcuna affermazione.

## Collegamento alle affermazioni

Ogni elemento di `usages` collega la fonte a:

- identificatore del contenuto;
- descrizione precisa dell'affermazione sostenuta;
- importanza `central` oppure `context`;
- data e ora dell'ultima verifica per quell'uso.

Le fonti di livello `C` possono sostenere soltanto informazioni di contesto. Le affermazioni centrali devono rispettare i requisiti più severi della politica delle fonti.

## Campi di applicabilità

I campi sotto `applicability` non sono intercambiabili:

- `valid_from` e `valid_to` indicano efficacia normativa o validità dichiarata;
- `as_of` indica la data alla quale una fotografia informativa è valida;
- `reference_period` descrive il periodo coperto da un dato o studio;
- `version` identifica edizione, release o versione consolidata;
- `provisional` segnala dati non definitivi.

Usare `null` quando un campo non si applica o il dato non è pubblicato. Non dedurre date o versioni mancanti.

## Localizzazione e copie locali

`locator` deve indirizzare alla parte effettivamente usata: articolo di legge, sezione, pagina, tabella, figura, dataset o serie. I campi non pertinenti restano `null`.

`local_copy` resta `null` salvo che la copia sia lecita e necessaria. Quando presente deve indicare percorso nel repository, hash SHA-256 e nota sulla licenza o sul diritto di conservazione. Il registro non autorizza il salvataggio di materiali protetti.

## Conflitti e limitazioni

Le limitazioni note devono essere espresse in `limitations`. Un conflitto con un'altra fonte registrata deve indicare il relativo identificatore, lo stato e l'eventuale criterio di risoluzione.

Un conflitto sostanziale `unresolved` relativo a un'affermazione centrale impedisce la pubblicazione, anche se il file è formalmente valido rispetto allo schema.

## Aggiornamento atomico

L'automazione deve aggiornare il registro nello stesso commit della guida che usa le fonti. L'operazione deve:

1. verificare che il repository sia pulito e aggiornato;
2. impedire esecuzioni editoriali concorrenti;
3. assegnare gli identificatori senza duplicati;
4. aggiornare `updated_at` e il contatore annuale;
5. validare sintassi, schema e regole semantiche;
6. includere nel commit soltanto fonti realmente verificate per il contenuto.

La validità dello schema non sostituisce la valutazione editoriale: una fonte può essere formalmente ben registrata e risultare comunque inadeguata secondo la politica.
