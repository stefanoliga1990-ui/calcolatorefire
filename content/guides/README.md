# Sorgenti delle guide

Ogni guida nasce da due file con lo stesso nome base:

- `GUIDE-NNNN.json`: manifesto strutturato conforme a [`guide.schema.json`](guide.schema.json);
- `GUIDE-NNNN.body.html`: solo il corpo editoriale, suddiviso in elementi `section`.

Il corpo non è una pagina HTML completa. Non deve contenere `html`, `head`, `body`, `h1`, script, stili inline,
metadati SEO, firma, fonti finali o call to action: questi elementi vengono prodotti dal generatore in modo uniforme.

Ogni sezione deve avere questa forma:

```html
<section class="content-section" id="identificatore" aria-labelledby="identificatore-title">
    <h2 id="identificatore-title">Titolo descrittivo</h2>
    <p>Contenuto originale della sezione.</p>
</section>
```

Per citare una fonte vicino a un'affermazione si usa un link HTTPS con l'identificatore registrato:

```html
<a href="https://esempio.it/documento" data-source-id="SRC-2026-0003">fonte istituzionale</a>
```

Il generatore accetta soltanto un insieme ristretto di tag e attributi semantici, verifica i riferimenti contro backlog
e registro delle fonti, genera la pagina finale in `src/main/resources/static/guida/` e aggiorna la sitemap.

Prima della redazione usare il [template editoriale](../../docs/editorial/template-editoriale-guida.md). I file starter
copiabili per manifesto e corpo si trovano nella cartella [`_template`](_template/).

Il comando operativo e tutte le condizioni di blocco sono descritti in
[`docs/editorial/sistema-generazione-guide.md`](../../docs/editorial/sistema-generazione-guide.md).
La validazione complessiva prima della pubblicazione è descritta in
[`docs/editorial/validatori-automatici.md`](../../docs/editorial/validatori-automatici.md).
