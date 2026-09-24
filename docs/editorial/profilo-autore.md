# Profilo autore

Versione: 1.0

Stato: definito per l'uso editoriale

Ultimo aggiornamento: 24 settembre 2026

## Identità editoriale

| Campo | Valore |
| --- | --- |
| Nome pubblico | Stefano Liga |
| Ruolo pubblico | Ideatore e sviluppatore di Calcolo FIRE Italia |
| URL autore previsto | `/autore/stefano-liga` |
| Ambiti trattati | Pianificazione FIRE, funzionamento del simulatore, ipotesi di calcolo, metodologia e lettura dei risultati |

Il profilo non deve attribuire a Stefano Liga qualifiche professionali, iscrizioni ad albi, certificazioni o esperienze lavorative che non siano state documentate e approvate esplicitamente.

## Firma breve

Da usare nell'intestazione delle guide e negli elenchi degli articoli:

> A cura di Stefano Liga, ideatore e sviluppatore di Calcolo FIRE Italia

## Descrizione breve

Da usare nel box autore in fondo alle guide:

> Stefano Liga è l'ideatore e sviluppatore di Calcolo FIRE Italia. Cura il simulatore, la documentazione della metodologia e le guide educative sul percorso FIRE, con attenzione alle ipotesi di calcolo, alle fonti e ai limiti dei risultati.

## Descrizione estesa

Da usare nella futura pagina autore:

> Stefano Liga ha ideato e sviluppato Calcolo FIRE Italia per rendere più comprensibili i calcoli e le ipotesi alla base di un percorso verso l'indipendenza finanziaria. Cura l'evoluzione del simulatore, la documentazione della metodologia e le guide pubblicate sul sito. Nei contenuti distingue i dati provenienti dalle fonti, le ipotesi del modello e gli esempi illustrativi, indicando i limiti dei risultati e la data di aggiornamento delle informazioni.

## Nota informativa associata

Questa nota deve restare distinta dalla biografia e comparire nelle guide secondo il template editoriale:

> I contenuti hanno finalità esclusivamente informative ed educative e non costituiscono consulenza finanziaria, fiscale, previdenziale o legale personalizzata. Prima di prendere decisioni, valuta la tua situazione e, quando necessario, rivolgiti a un professionista qualificato.

## Dati strutturati previsti

La futura pagina autore deve usare `ProfilePage` con Stefano Liga come `mainEntity` di tipo `Person`. Ogni guida deve indicare la stessa entità nel campo `author` del proprio markup `Article`, includendo almeno nome e URL della pagina autore.

La `Person` deve usare gli stessi dati visibili nella pagina:

| Proprietà | Valore o regola |
| --- | --- |
| `@type` | `Person` |
| `name` | `Stefano Liga` |
| `url` | URL assoluto corrispondente a `/autore/stefano-liga` |
| `jobTitle` | `Ideatore e sviluppatore di Calcolo FIRE Italia` |
| `description` | Descrizione breve definita in questo documento |
| `image` | Da aggiungere solo quando sarà disponibile un'immagine autore approvata e pubblica |
| `sameAs` | Da aggiungere solo per profili pubblici verificati e approvati da Stefano Liga |

I dati strutturati non devono contenere informazioni assenti dal contenuto visibile della pagina.

Questa struttura segue le indicazioni di Google Search Central per il [markup dell'autore negli articoli](https://developers.google.com/search/docs/appearance/structured-data/article) e per le [pagine profilo](https://developers.google.com/search/docs/appearance/structured-data/profile-page).

## Regole di utilizzo

- Usare sempre il nome `Stefano Liga` senza varianti o titoli aggiuntivi.
- Collegare la firma breve alla pagina autore quando questa sarà disponibile.
- Usare una sola descrizione breve canonica in tutte le guide.
- Riservare la descrizione estesa alla pagina autore o a contesti che richiedono una biografia completa.
- Applicare la nota sul processo automatizzato definita nella politica editoriale.
- Non presentare l'autore come consulente o come autorità fiscale, previdenziale, finanziaria o legale.
- Non aggiungere foto, link social, titoli di studio, certificazioni o anni di esperienza senza approvazione e riscontro verificabile.
- Tenere la nota informativa separata dalla descrizione dell'autore: la prima chiarisce i limiti dei contenuti, la seconda identifica chi li cura.

## Criterio di modifica

Le formulazioni contenute in questo documento sono la fonte di verità per firma e biografia. Qualsiasi modifica al ruolo pubblico, alle qualifiche o ai collegamenti esterni deve essere approvata da Stefano Liga prima di essere usata dal processo editoriale automatico.
