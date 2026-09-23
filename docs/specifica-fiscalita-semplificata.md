# Calcolo FIRE Italia — specifica della fiscalità semplificata

Stato: Step 1 approvato; Step 2 implementato come motore fiscale isolato;
Step 3 implementato per l'accumulo; Step 4 implementato per target, PAC e
decumulo congiunti; Step 5 integrato nel contratto API. Il frontend non usa
ancora i nuovi input e risultati fiscali.

Questo documento estende `docs/specifica-matematica.md`. In caso di fiscalità
attiva, le regole seguenti prevalgono sulle formule prive di imposte soltanto
nei punti espressamente indicati. L'obiettivo è stimare l'effetto fiscale sulle
voci principali senza trasformare il simulatore in uno strumento dichiarativo.

## 1. Perimetro

La prima versione fiscale comprende esclusivamente:

1. imposta sulle plusvalenze realizzate tramite vendita del portafoglio;
2. imposta di bollo annuale sul valore degli investimenti;
3. costo fiscale del patrimonio principale e degli investimenti aggiuntivi.

Non vengono introdotti:

- aliquota agevolata per titoli di Stato;
- compensazione o riporto delle minusvalenze;
- distinzione fra redditi di capitale e redditi diversi;
- differenze tra regime amministrato, dichiarativo e gestito;
- ritenute estere, crediti d'imposta o convenzioni internazionali;
- tassazione separata di dividendi e cedole;
- fiscalità di successioni, donazioni, pensioni o immobili;
- calcolo di dichiarazioni o scadenze fiscali.

Il portafoglio è quindi trattato come un investimento ad accumulazione: i
rendimenti non sono tassati quando maturano, ma la plusvalenza viene tassata
proporzionalmente quando una parte del patrimonio viene venduta.

Le rendite periodiche restano importi netti inseriti dall'utente. I capitali
futuri una tantum sono considerati importi netti effettivamente disponibili.

## 2. Input fiscali e valori predefiniti

| Simbolo | Input | Default | Significato |
| --- | --- | --- | --- |
| `tau` | Aliquota ordinaria sulle plusvalenze | `26%` | applicata soltanto alla plusvalenza realizzata nelle vendite |
| `b_a` | Imposta di bollo annuale | `0,20%` | riduzione annuale equivalente del patrimonio investito |
| `F_0` | Costo fiscale del patrimonio principale | `V_0` | capitale fiscalmente investito ancora attribuibile al portafoglio corrente |
| `F_q,0` | Costo fiscale dell'investimento aggiuntivo `q` | `I_q,0` | equivalente di `F_0` per ciascun investimento o PAC esistente |

L'aliquota e il bollo sono modificabili. L'interfaccia deve spiegare:

- **Aliquota sulle plusvalenze — 26%**: si applica soltanto alla parte di
  guadagno realizzata quando vengono vendute quote del portafoglio;
- **Imposta di bollo annuale — 0,20%**: viene stimata sul valore del patrimonio
  investito indipendentemente dalla presenza di plusvalenze.

### 2.1 Comportamento del costo fiscale nell'interfaccia

Sotto `Patrimonio investito oggi` viene mostrato:

> Il costo fiscale viene inizialmente considerato uguale all'intero patrimonio.
> Puoi **modificarlo**.

- finché l'utente non seleziona **modificarlo**, `F_0` segue automaticamente
  ogni modifica di `V_0`;
- dopo la modifica manuale, `F_0` diventa indipendente da `V_0`;
- il ripristino dei valori riattiva la modalità automatica;
- lo stesso comportamento si applica a `F_q,0` nelle card degli investimenti
  o PAC esistenti;
- il costo fiscale può essere maggiore del valore corrente: rappresenta una
  minusvalenza latente e non genera un credito fiscale nel modello.

L'API usa il valore del patrimonio corrispondente quando il costo fiscale è
omesso. L'omissione e l'invio esplicito dello stesso valore devono produrre
risultati identici.

## 3. Conversione mensile del bollo

Il tasso mensile equivalente del bollo è definito in modo che dodici
applicazioni producano esattamente la riduzione annuale dichiarata:

```text
b_m = 1 - (1 - b_a)^(1/12)
```

Con `b_a = 0,20%`:

```text
b_m = 0,0166819639% circa
(1 - b_m)^12 = 99,80%
```

Il bollo è una riduzione diretta del saldo. Non viene trattato come una vendita,
non genera un'ulteriore plusvalenza imponibile e non riduce il costo fiscale.

## 4. Stato fiscale del portafoglio

In ogni mese il motore conserva almeno:

- `B`: valore di mercato del portafoglio;
- `F`: costo fiscale residuo;
- `U = max(0, B - F)`: plusvalenza latente positiva;
- `p = U / B`, se `B > 0`, altrimenti zero: quota imponibile di una vendita.

Rendimenti e bollo modificano `B` ma non `F`. Nuovi apporti netti aumentano
entrambi dello stesso importo. Una vendita riduce `B` dell'importo lordo venduto
e riduce `F` in proporzione alla quota di portafoglio ceduta.

Non si calcola una plusvalenza negativa. Se `F >= B`, allora `p = 0` e la
vendita non genera imposta.

## 5. Accumulo fiscalizzato

Per il mese `j`, il capitale iniziale produce rendimento, gli apporti entrano a
fine mese e infine viene stimato il bollo:

```text
grossBalance_j = B_(j-1) × (1 + r_am) + contribution_j + netInflows_j
stampDuty_j = grossBalance_j × b_m
B_j = max(0, grossBalance_j - stampDuty_j)

F_j = F_(j-1) + contribution_j + netInflows_j
```

`netInflows_j` comprende rendite nette reinvestite e capitali futuri netti
investiti nel portafoglio. I rendimenti non aumentano `F_j`.

Un capitale futuro mantenuto come liquidità e non investito non paga il bollo
prima del FIRE. Quando confluisce nel portafoglio FIRE, saldo e costo fiscale
aumentano entrambi dell'intero importo netto.

Ogni investimento o PAC esistente mantiene il proprio saldo e costo fiscale
durante l'accumulo. Se è disponibile all'ingresso nel FIRE, saldo e costo
fiscale residuo confluiscono nel portafoglio FIRE. Le risorse non disponibili
restano escluse dal target e dal PAC principale.

## 6. Vendita e imposta durante il FIRE

Nel mese FIRE `k`, `D_k` è il fabbisogno netto dopo le rendite. Prima della
vendita entrano gli eventuali capitali futuri netti `K_k`:

```text
B_available = B_(k-1) + K_k
F_available = F_(k-1) + K_k
p_k = max(0, B_available - F_available) / B_available
```

Se `B_available = 0`, `p_k = 0`.

La vendita lorda necessaria per ottenere `D_k` netti è:

```text
G_required_k = D_k / (1 - tau × p_k)
G_k = min(B_available, G_required_k)
tax_k = G_k × p_k × tau
netProceeds_k = G_k - tax_k
shortfall_k = max(0, D_k - netProceeds_k)
```

Il costo fiscale residuo dopo la vendita è:

```text
F_afterSale = F_available × (1 - G_k / B_available)
```

con valore zero quando viene venduto l'intero portafoglio.

Il patrimonio rimasto produce rendimento e paga il bollo a fine mese:

```text
B_afterSale = B_available - G_k
return_k = B_afterSale × r_fm
grossEndBalance_k = B_afterSale + return_k
stampDuty_k = grossEndBalance_k × b_m
B_k = max(0, grossEndBalance_k - stampDuty_k)
F_k = F_afterSale
```

Se il bollo esaurisce il saldo, anche il costo fiscale residuo viene azzerato.

## 7. Target FIRE e PAC: calcolo congiunto

Con la fiscalità, il target FIRE non può essere calcolato separatamente dal PAC:
il PAC determina il costo fiscale all'ingresso nel FIRE e questo determina la
tassa applicata alle vendite future.

Per ogni target candidato il motore deve:

1. simulare l'accumulo e determinare il PAC necessario per raggiungerlo;
2. determinare saldo e costo fiscale disponibili all'ingresso nel FIRE;
3. simulare tutte le vendite fiscali del decumulo;
4. verificare fabbisogno netto, shortfall e capitale finale;
5. cercare numericamente la soluzione minima con tolleranza monetaria di
   `0,01 euro`.

Se il capitale già disponibile supera il target candidato, la porzione destinata
al FIRE eredita proporzionalmente il rapporto tra costo fiscale e valore di
mercato del portafoglio disponibile. La proiezione personale continua invece a
usare tutto il patrimonio effettivamente disponibile.

### 7.1 Metodo FINITE

La formula chiusa della sezione 6 della specifica principale resta valida solo
con `tau = 0` e `b_a = 0`. Con fiscalità positiva, il target è il minimo saldo
iniziale che:

- copre ogni `D_k` al netto delle imposte;
- non produce shortfall;
- termina con almeno il capitale finale nominale desiderato.

Il capitale finale desiderato è un saldo di mercato lordo. Non viene simulata
una vendita totale al confine finale e quindi non viene calcolata una tassa
latente sul capitale lasciato investito.

### 7.2 Metodo SWR

La SWR continua a rappresentare il rapporto tra la prima vendita lorda annualizzata
e il patrimonio al FIRE. Il target fiscale usa quindi la vendita lorda necessaria
per produrre il primo fabbisogno netto:

```text
T_swr_tax = G_1 × 12 / swr
```

Poiché `G_1` dipende dal rapporto tra saldo e costo fiscale, il target viene
risolto numericamente. Con `tau = 0`, `b_a = 0` e senza risorse, deve tornare
esattamente `W_1 × 12 / swr`.

Le rendite e i capitali ponte seguono le regole delle sezioni 19–21 della
specifica principale, sostituendo ai fabbisogni netti le vendite lorde fiscali.
Come nel modello attuale, la proiezione verifica la durata e può segnalare uno
shortfall: la SWR non garantisce automaticamente la sostenibilità.

## 8. Risultati minimi

Oltre ai risultati attuali, dominio e API devono esporre almeno:

- costo fiscale all'ingresso nel FIRE;
- plusvalenza latente all'ingresso nel FIRE;
- prima vendita lorda;
- imposta sulla plusvalenza del primo mese;
- importo netto disponibile nel primo mese;
- totale imposte sulle plusvalenze in accumulo e FIRE;
- totale imposta di bollo in accumulo e FIRE;
- costo fiscale residuo a fine simulazione.

L'interfaccia deve distinguere chiaramente:

- **vendita/prelievo lordo dal portafoglio**;
- **imposta stimata**;
- **importo netto spendibile**.

Il termine `prelievo netto` non deve essere usato senza specificare se significa
al netto delle rendite oppure al netto delle imposte.

## 9. Validazioni

| Codice previsto | Condizione |
| --- | --- |
| `INVALID_CAPITAL_GAINS_TAX_RATE` | `tau` non finita, minore di zero oppure maggiore o uguale a 100% |
| `INVALID_STAMP_DUTY_RATE` | `b_a` non finita, minore di zero oppure maggiore o uguale a 100% |
| `INVALID_TAX_BASIS` | costo fiscale negativo, non finito o oltre il limite monetario generale |
| `FISCAL_SOLUTION_NOT_FOUND` | il risolutore non riesce a delimitare o convergere su una soluzione valida |

Il costo fiscale non è vincolato superiormente al valore del portafoglio perché
può esistere una minusvalenza latente. Tutti i denominatori devono essere
controllati e ogni risultato monetario deve essere finito e non negativo.

## 10. Scenari numerici di accettazione dello Step 1

Gli esempi isolano la fiscalità usando rendimento, inflazione e rendite pari a
zero, salvo indicazione contraria. I valori non vengono prodotti dal motore Java
e sono versionati in `src/test/resources/golden-tax-scenarios.json`. Il
verificatore indipendente `scripts/verify_golden_reference.py` li riconcilia
senza usare il codice di produzione.

### 10.1 Nessuna plusvalenza latente

```text
B = 100.000 euro
F = 100.000 euro
D = 1.000 euro
tau = 26%
```

Risultati:

```text
p = 0
vendita lorda = 1.000 euro
imposta = 0 euro
netto disponibile = 1.000 euro
```

### 10.2 Plusvalenza latente del 30%

```text
B = 100.000 euro
F = 70.000 euro
D = 1.000 euro
tau = 26%
```

Risultati prima di rendimento e bollo:

```text
p = 30%
vendita lorda = 1.084,59869848 euro
imposta = 84,59869848 euro
netto disponibile = 1.000 euro
saldo dopo vendita = 98.915,40130152 euro
costo fiscale residuo = 69.240,78091106 euro
```

### 10.3 Portafoglio in perdita

Con `B = 80.000 euro`, `F = 100.000 euro` e fabbisogno netto di `1.000 euro`,
la quota imponibile è zero. Vendita lorda e netto disponibile coincidono e il
modello non crea alcun credito da minusvalenza.

### 10.4 Bollo mensile equivalente

Con saldo imponibile di `100.000 euro` e `b_a = 0,20%`:

```text
b_m = 0,000166819639945581
bollo del mese = 16,68196399 euro
```

Dopo dodici mesi senza altri movimenti il saldo deve essere esattamente il
`99,80%` del valore iniziale entro la tolleranza numerica.

### 10.5 Vendita e bollo nello stesso mese

Applicando lo scenario 10.2, rendimento zero e bollo standard:

```text
saldo dopo vendita = 98.915,40130152 euro
bollo del mese = 16,50103163 euro
saldo finale = 98.898,90026989 euro
```

### 10.6 Compatibilità con il modello attuale

Con `tau = 0` e `b_a = 0`:

- tutti i 7 golden base devono restare invariati;
- tutti i 14 golden delle risorse aggiuntive devono restare invariati;
- formule FINITE, SWR, PAC e proiezioni devono coincidere entro `0,01 euro`.

## 11. Criterio di completamento dello Step 1

Lo Step 1 è completo quando:

- perimetro, input, default e testi di aiuto sono non ambigui;
- timing di rendimento, apporti, vendite, imposte e bollo è definito;
- costo fiscale e sua modalità automatica/manuale sono definiti;
- FINITE e SWR hanno una regola fiscale traducibile in codice;
- casi limite e validazioni sono elencati;
- gli scenari 10.1–10.6 sono verificabili indipendentemente;
- nessun comportamento dell'applicazione è stato ancora modificato.

## 12. Implementazione isolata dello Step 2

Lo Step 2 introduce nel dominio, senza modificare il `FireCalculator`:

- `FiscalSettings`, con default 26% e 0,20% e validazione dedicata;
- `FiscalPortfolioState`, che separa saldo, costo fiscale e plusvalenza latente;
- `FiscalCalculator`, con inizializzazione automatica del costo fiscale,
  accumulo mensile, vendita fiscalizzata, shortfall e bollo mensile;
- risultati mensili distinti per accumulo e decumulo;
- codici di errore fiscali stabili per le integrazioni successive.

I sei scenari golden fiscali sono eseguiti anche come test JUnit. Ulteriori test
coprono default, override, apporti netti, vendita completa, rendimento negativo,
limiti degli input e riconciliazione del bollo su dodici mesi.

Al termine dello Step 2 il comportamento pubblico dell'applicazione restava
invariato, in attesa degli step di integrazione successivi.

## 13. Integrazione nella fase di accumulo — Step 3

Lo Step 3 collega le primitive mensili dello Step 2 a una proiezione completa,
dal mese zero fino all'ingresso nel FIRE. Ogni portafoglio mantiene una coppia
indipendente `saldo / costo fiscale` e produce per ciascun mese:

- saldo e costo fiscale iniziali;
- rendimento maturato;
- versamento del PAC;
- rendite o capitali netti investiti;
- saldo prima del bollo e bollo applicato;
- saldo e costo fiscale finali.

`FiscalAccumulationPlan` descrive il calendario mensile. Le factory dedicate
generano sia il nuovo PAC costante o crescente, sia il PAC di un investimento
esistente con intervallo iniziale incluso e finale escluso. Gli apporti netti
permettono di rappresentare rendite reinvestite e capitali futuri ricevuti
durante l'accumulo.

`FiscalAccumulationProjector` proietta ogni portafoglio separatamente e
riconcilia versamenti, apporti, rendimenti e bollo. La proiezione aggregata
somma saldo e costo fiscale al FIRE soltanto per i portafogli marcati come
disponibili; gli investimenti non disponibili restano tracciati ma esclusi.

Sono coperti esplicitamente:

- PAC costante e crescente;
- patrimonio iniziale in plusvalenza e in minusvalenza latente;
- rendite e capitali netti investiti a fine mese;
- bollo mensile e riconciliazione annuale;
- PAC esistenti con rendimento, crescita e periodo propri;
- investimento non disponibile al FIRE;
- accumulo di zero mesi e calendari non validi.

Quattro nuovi scenari golden sono versionati in
`golden-tax-accumulation-scenarios.json` e verificati sia da JUnit sia dallo
script Python indipendente. Al termine dello Step 3 la suite contiene 314 test,
senza errori, fallimenti o test ignorati.

Il `FireCalculator`, il contratto HTTP e il frontend restano invariati in
questo step. Il collegamento al target e al decumulo fiscale avverrà nello step
successivo, quando il risolutore potrà usare il costo fiscale prodotto
dall'accumulo.

## 14. Target, PAC e decumulo congiunti — Step 4

Lo Step 4 introduce `FiscalFireCalculator`, un motore di dominio separato dal
contratto pubblico attuale. Il calcolo usa due ricerche numeriche annidate:

1. per un PAC candidato proietta saldo e costo fiscale disponibili al FIRE;
2. con il rapporto fiscale risultante cerca il target minimo del metodo scelto;
3. confronta il patrimonio accumulato con quel target;
4. ripete la ricerca sul PAC fino a individuare il minimo valore sufficiente.

La ricerca del target e quella del PAC conservano sempre un estremo superiore
verificato come sufficiente. La precisione monetaria richiesta è `0,01 euro`;
orizzonti o valori che non possono essere delimitati producono
`FISCAL_SOLUTION_NOT_FOUND`.

### 14.1 Metodo FINITE

Per ogni target candidato viene simulato l'intero decumulo mensile. Il target è
accettato soltanto se:

- ogni fabbisogno netto viene coperto senza shortfall;
- il saldo finale è almeno pari al capitale terminale nominale desiderato;
- vendite, imposte, rendimenti, bollo e capitali futuri rispettano il timing
  definito nelle sezioni precedenti.

### 14.2 Metodo SWR

Il target base usa la prima vendita lorda fiscalizzata, non la sola spesa
netta. In presenza di rendite o capitali ponte, il motore simula le vendite e
il bollo fino all'inizio del regime stabile e cerca il capitale che, in quel
mese, soddisfa la riserva SWR. Come nel motore attuale, la proiezione completa
può comunque evidenziare uno shortfall successivo.

### 14.3 Proiezioni e risorse

La proiezione di decumulo espone mese per mese:

- fabbisogno netto e capitale netto ricevuto;
- quota imponibile, vendita lorda richiesta ed effettiva;
- imposta sulla plusvalenza, ricavo netto e shortfall;
- rendimento, bollo, saldo e costo fiscale residui.

Il costo fiscale degli investimenti esistenti può essere fornito separatamente
e, se omesso, coincide con il loro valore corrente. Rendite reinvestite e
capitali futuri netti aumentano il costo fiscale per l'intero importo. Gli
investimenti non disponibili al FIRE restano esclusi dal saldo e dal costo
fiscale usati dal risolutore.

### 14.4 Verifiche

Sono coperti FINITE, SWR e ponti SWR, patrimonio in plusvalenza o minusvalenza,
bollo, capitale terminale, costo fiscale degli investimenti esistenti, rendite,
capitali futuri investiti e liquidità non investita. Con aliquota e bollo a
zero, target, PAC e saldi coincidono con il motore attuale entro un centesimo.

Cinque scenari golden completi sono versionati in
`golden-tax-fire-scenarios.json` e riconciliati sia da JUnit sia dal riferimento
Python indipendente. API e frontend restano invariati fino allo step di
integrazione successivo.

## 15. Integrazione API — Step 5

Lo Step 5 collega il motore fiscale all'endpoint esistente
`POST /api/v1/fire/calculations`, senza modificare i campi di risposta già
consumati dal frontend. Ogni richiesta produce sia il calcolo compatibile
precedente sia la nuova sezione autorevole `fiscal`.

Gli input facoltativi sono:

- `capitalGainsTaxRate`, default `0.26`;
- `annualStampDutyRate`, default `0.002`;
- `currentTaxBasis`, default pari al patrimonio principale corrente;
- `additionalResources[].taxBasis` per `EXISTING_INVESTMENT`, default pari al
  patrimonio corrente della risorsa.

La risposta fiscale espone impostazioni effettive, target e prima vendita,
proiezioni di accumulo per singolo portafoglio, proiezioni di decumulo del
target e del patrimonio personale e totali di bollo e plusvalenze. I
portafogli mantengono tipo di origine e indice della risorsa, così il frontend
può associare i risultati alla card corretta senza dipendere dal nome.

Otto test MockMvc coprono default, compatibilità con aliquote zero, costo
fiscale manuale, mapping degli investimenti esistenti, totali fiscali,
liquidità non soggetta a bollo e validazioni. È inoltre verificata la stabilità
con proiezioni lunghe, molte risorse e importi nell'ordine dei miliardi. Per i
confronti di copertura dello shortfall il risolutore usa la tolleranza monetaria
di un centesimo, evitando che residui floating point sub-centesimali rendano
irraggiungibile uno scenario.

Il frontend resta invariato in questo step e continuerà a mostrare i risultati
precedenti finché gli input e le viste fiscali non saranno collegati nello step
successivo.
