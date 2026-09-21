# Calcolo FIRE Italia — specifica matematica del motore

Stato: convenzioni matematiche dell'MVP approvate; estensione per risorse aggiuntive proposta nella Fase 1 del branch `rendite-aggiuntive`.

Questa specifica è la fonte primaria del motore Java. Il workbook Excel resta un riferimento funzionale, ma non è un golden master finché le differenze indicate in fondo al documento non vengono corrette.

## 1. Perimetro

L'MVP calcola:

1. il capitale necessario all'ingresso nel FIRE;
2. il versamento mensile iniziale necessario per raggiungerlo;
3. le proiezioni mensili di accumulo e decumulo.

L'estensione descritta nelle sezioni 16–24 aggiunge investimenti o PAC esistenti, rendite periodiche e capitali futuri una tantum. In assenza di queste risorse, formule e risultati devono restare identici all'MVP attuale.

Restano esclusi:

- fiscalità sulle vendite e sulle plusvalenze;
- calcolo automatico delle imposte su affitti, pensioni e altre rendite: gli importi periodici sono inseriti già al netto delle imposte personali stimate dall'utente;
- rendimenti variabili, Monte Carlo e rischio di sequenza simulato;
- account, database e salvataggio cloud.

I rendimenti inseriti sono nominali, al netto dei costi ricorrenti dell'investimento e prima delle imposte personali. L'interfaccia deve dichiarare esplicitamente che la fiscalità sui prelievi non è inclusa.

## 2. Convenzioni temporali

### 2.1 Granularità

Tutti i calcoli usano mesi interi.

- `N_acc = 12 × (età FIRE − età attuale)`
- `N_fire = 12 × durata FIRE in anni`

Nell'MVP le età rappresentano confini di pianificazione espressi in anni interi. Il FIRE inizia subito dopo gli `N_acc` mesi di accumulo. Questa modalità semplice è stata approvata: tra 36 e 50 anni ci sono sempre 168 mesi, indipendentemente dal calendario e dal mese di nascita.

### 2.2 Ordine degli eventi in accumulo

Per ogni mese di accumulo:

1. il capitale iniziale del mese produce rendimento;
2. il PAC viene versato alla fine del mese;
3. si ottiene il saldo finale del mese.

Il primo versamento avviene alla fine del primo mese.

### 2.3 Ordine degli eventi nel FIRE

Per ogni mese di FIRE:

1. il prelievo avviene all'inizio del mese;
2. il capitale rimasto produce rendimento;
3. si ottiene il saldo finale del mese.

Il primo prelievo avviene immediatamente all'ingresso nel FIRE. Formula chiusa e proiezione mensile devono adottare questo stesso ordine.

## 3. Input e unità

| Simbolo | Input | Unità e significato |
| --- | --- | --- |
| `A_0` | Età attuale | anni interi |
| `A_f` | Età FIRE | anni interi |
| `Y_f` | Durata FIRE | anni interi positivi |
| `S_0` | Spesa mensile di oggi | euro di oggi al mese |
| `i_a` | Inflazione | tasso annuo effettivo |
| `r_fa` | Rendimento nel FIRE | tasso annuo nominale effettivo |
| `swr` | Safe Withdrawal Rate | tasso annuo iniziale, richiesto solo per `SWR` |
| `L_0` | Capitale finale desiderato | euro di oggi; default zero; usato dal target `FINITE` |
| `V_0` | Patrimonio investito oggi | euro nominali di oggi |
| `r_aa` | Rendimento in accumulo | tasso annuo nominale effettivo |
| `g_a` | Crescita del PAC | tasso annuo effettivo; default 0% |
| `metodo` | Metodo del target | `FINITE` o `SWR` |

Gli importi monetari restano in piena precisione nei calcoli. L'arrotondamento avviene solo nella visualizzazione.

Gli input delle risorse aggiuntive e i relativi simboli sono definiti nelle sezioni 16 e 17.

## 4. Conversione dei tassi

Ogni tasso annuo effettivo `x_a` viene trasformato nel tasso mensile equivalente:

```text
x_m = (1 + x_a)^(1/12) − 1
```

Il rendimento reale mensile durante il FIRE è:

```text
r_real_m = (1 + r_fm) / (1 + i_m) − 1
```

Non si usa l'approssimazione `rendimento − inflazione`.

## 5. Spesa lorda all'ingresso nel FIRE

Il primo prelievo nominale è la spesa mensile di oggi rivalutata per tutti i mesi di accumulo:

```text
W_1 = S_0 × (1 + i_m)^N_acc
```

Il prelievo programmato del mese `k`, con `k` che parte da 1, è:

```text
W_k = W_1 × (1 + i_m)^(k − 1)
```

`W_k` rappresenta il fabbisogno lordo prima delle rendite. Quando sono presenti rendite periodiche, il prelievo netto richiesto al portafoglio è `D_k`, definito nella sezione 19.

## 6. Target a durata finita

Il capitale finale desiderato viene inserito in euro di oggi. Il suo valore, espresso nel potere d'acquisto nominale dell'inizio del FIRE, è:

```text
L_fire = L_0 × (1 + i_m)^N_acc
```

Poiché il primo prelievo avviene subito, il target usa una rendita anticipata.

Se `r_real_m` è diverso da zero:

```text
T_finite =
    W_1 × [1 − (1 + r_real_m)^(-N_fire)] / r_real_m × (1 + r_real_m)
    + L_fire / (1 + r_real_m)^N_fire
```

Se il rendimento reale mensile è numericamente zero:

```text
T_finite = W_1 × N_fire + L_fire
```

Partendo da `T_finite`, senza margine, la proiezione mensile deve terminare al capitale finale nominale:

```text
L_end_nominal = L_0 × (1 + i_m)^(N_acc + N_fire)
```

Questa formula chiusa resta il riferimento quando non esistono rendite o capitali una tantum durante il FIRE. La sezione 20 definisce la ricorrenza equivalente e la sua generalizzazione ai flussi variabili.

## 7. Target SWR e selezione del metodo

Il target SWR usa il primo prelievo annuo all'ingresso nel FIRE:

```text
T_swr = W_1 × 12 / swr
```

La SWR è un benchmark annuale. Non rappresenta né un rendimento né una percentuale da applicare ogni anno al saldo residuo.

Per costruzione, un valore SWR più alto produce un target più basso. Questo non implica una maggiore sostenibilità: aumenta la quota iniziale prelevata rispetto al capitale. La proiezione mensile deve quindi segnalare chiaramente quando il target SWR non copre tutti i prelievi della durata FIRE selezionata, indicando il primo mese con shortfall. Non si applicano limiti arbitrari alla SWR e non si sostituisce implicitamente il target SWR con quello a durata finita.

La selezione del target necessario è:

```text
FINITE       → T_target = T_finite
SWR          → T_target = T_swr
```

Si calcola solo il target del metodo selezionato. Nel metodo `FINITE` la SWR non è richiesta né calcolata; nel metodo `SWR` il target a durata finita non è calcolato. Durata FIRE e rendimento FIRE restano necessari alla proiezione mensile anche quando il metodo del target è `SWR`. Il capitale finale desiderato non influisce sul target `SWR`.

Quando sono presenti rendite o capitali futuri durante il FIRE, la sezione 21 definisce il target SWR generalizzato con capitale ponte e riserva SWR. Senza risorse aggiuntive tale formula deve restituire esattamente `W_1 × 12 / swr`.

## 8. Piano di accumulo

Il valore futuro del patrimonio già investito è:

```text
FV_current = V_0 × (1 + r_am)^N_acc
```

Il capitale ancora da costruire è:

```text
Gap = max(0, T_target − FV_current)
```

Il versamento del mese `j`, con `j` che parte da 1, è:

```text
C_j = C_1 × (1 + g_m)^(j − 1)
```

Il fattore di capitalizzazione dei versamenti a fine mese è:

```text
F = [(1 + r_am)^N_acc − (1 + g_m)^N_acc] / (r_am − g_m)
```

Quando `r_am` e `g_m` sono numericamente uguali:

```text
F = N_acc × (1 + r_am)^(N_acc − 1)
```

Il PAC mensile iniziale è:

```text
C_1 = Gap / F
```

Se `Gap = 0`, il PAC necessario è zero. Il patrimonio finale mostrato resta il valore effettivamente proiettato, anche quando supera il target.

Se `N_acc = 0` e `Gap > 0`, non esiste un PAC mensile capace di colmare il divario: il risultato è `UNREACHABLE_WITH_ZERO_MONTHS`, non zero.

La sezione 18 sostituisce `FV_current` con il capitale complessivo disponibile quando sono presenti investimenti, PAC, rendite reinvestite o capitali futuri prima del FIRE. La formula del PAC residuo resta invariata.

## 9. Proiezioni mensili

### 9.1 Accumulo

Con `B_0 = V_0`, per ogni mese `j`:

```text
return_j = B_(j−1) × r_am
contribution_j = C_1 × (1 + g_m)^(j − 1)
B_j = B_(j−1) + return_j + contribution_j
```

Al termine, `B_N_acc` deve coincidere con il target selezionato entro la tolleranza, salvo il caso di capitale già sufficiente, nel quale sarà superiore.

### 9.2 Decumulo

La proiezione personale parte dal patrimonio effettivamente disponibile all'ingresso nel FIRE:

```text
B_0 = max(T_target, saldo finale effettivo dell'accumulo)
```

Per ogni mese `k`:

```text
scheduled_withdrawal_k = W_k
actual_withdrawal_k = min(max(B_(k−1), 0), scheduled_withdrawal_k)
shortfall_k = scheduled_withdrawal_k − actual_withdrawal_k
return_k = (B_(k−1) − actual_withdrawal_k) × r_fm
B_k = max(0, B_(k−1) − actual_withdrawal_k + return_k)
```

La simulazione registra il primo mese con shortfall e non permette che il saldo mostrato diventi negativo. Se il metodo è `SWR` e si verifica uno shortfall, l'interfaccia mostra un avviso esplicito che il capitale non copre l'intera durata FIRE e indica il primo mese non interamente finanziato.

La proiezione del target parte esattamente da `T_target` e consente di confrontarla con la proiezione personale.

Con risorse aggiuntive, la sezione 22 generalizza la proiezione usando il prelievo netto `D_k` e gli eventuali capitali una tantum `K_k`.

## 10. Risultati minimi dell'MVP

- mesi disponibili prima del FIRE;
- primo prelievo nominale;
- target del metodo selezionato (`FINITE` oppure `SWR`);
- equivalenti in euro di oggi;
- PAC mensile iniziale;
- totale nominale dei versamenti;
- saldo mensile di accumulo e decumulo;
- capitale finale nominale ed equivalente in euro di oggi;
- eventuale mese di esaurimento e shortfall complessivo nei dati API e nelle proiezioni; la schermata dei risultati non mostra il campo shortfall complessivo.

Con risorse aggiuntive si mostrano inoltre almeno:

- capitale disponibile al FIRE prodotto dagli investimenti e dai PAC esistenti;
- rendite considerate nell'accumulo e nel FIRE;
- capitali futuri considerati;
- PAC aggiuntivo richiesto, distinto dai versamenti già programmati;
- contributo complessivo delle risorse aggiuntive al piano.

## 11. Validazioni

Non vengono imposti limiti commerciali arbitrari. Sono obbligatorie le seguenti condizioni matematiche:

| Codice | Condizione |
| --- | --- |
| `INVALID_AGE_ORDER` | `A_f < A_0` |
| `INVALID_FIRE_DURATION` | durata FIRE non positiva |
| `INVALID_RATE` | inflazione, rendimento o crescita PAC minori o uguali a −100% |
| `INVALID_SWR` | metodo `SWR` con SWR mancante, non finita o `swr <= 0` |
| `INVALID_AMOUNT` | importo negativo o non finito |
| `UNREACHABLE_WITH_ZERO_MONTHS` | nessun mese di accumulo e capitale insufficiente |

Il metodo `FINITE` non richiede una SWR. L'interfaccia mostra solo i parametri e il target del metodo selezionato.

Le validazioni specifiche delle risorse aggiuntive sono definite nella sezione 23.

## 12. Precisione e criteri di test

- Calcoli interni Java con `BigDecimal` o `double` senza arrotondamenti intermedi. La scelta definitiva verrà presa nell'implementazione del dominio; i test confrontano risultati numerici, non la rappresentazione binaria.
- Tolleranza monetaria dei test di accettazione: `0,01 euro`.
- Tolleranza sui tassi: `1e-12`.
- Un target a durata finita deve riconciliarsi con la proiezione mensile entro `0,01 euro`.
- Le proprietà direzionali devono essere testate oltre ai valori puntuali: più spesa aumenta il target; più rendimento FIRE riduce il target a durata finita; più patrimonio corrente riduce o annulla il PAC.

Gli scenari numerici versionati sono in `src/test/resources/golden-scenarios.csv`.

## 13. Differenze rispetto al workbook attuale

Il workbook non va copiato letteralmente nei punti seguenti:

1. `FIRE Calculator!B22` usa una rendita posticipata; l'MVP usa una rendita anticipata perché il primo prelievo è immediato.
2. `Proiezione mensile` calcola rendimento e poi prelievo; l'MVP preleva e poi applica il rendimento al capitale rimasto.
3. Il workbook applica un margine effettivo del 5% mentre la nota indica 10%; l'MVP non applica alcun margine aggiuntivo.
4. Il capitale finale non ha un'unità temporale coerente; l'MVP lo interpreta in euro di oggi.
5. I rendimenti del workbook sono descritti come netti anche di tasse; l'MVP li definisce al netto dei costi ricorrenti ma prima delle imposte personali.
6. Gli altri redditi del workbook non vengono copiati automaticamente. L'estensione accetta soltanto le risorse tipizzate e validate definite nelle sezioni 16–24.
7. Il workbook non gestisce correttamente come risultato distinto il caso di zero mesi di accumulo con capitale insufficiente.

## 14. Convenzione sulle età approvata

L'MVP usa la modalità semplice: le età sono confini esatti e `N_acc = 12 × (A_f − A_0)`. Non vengono richieste data di nascita o data iniziale della simulazione.

Una futura modalità calendario potrà far partire il FIRE a gennaio dell'anno in cui si raggiunge l'età scelta. Richiederà almeno l'anno o la data di nascita e una data iniziale della simulazione, ma non fa parte dell'MVP.

## 15. Criterio di completamento del primo step

Il primo step è approvato quando:

- questa specifica è accettata;
- la convenzione sulle età della sezione 14 è registrata nei test;
- tutti gli scenari golden sono riconciliati da un calcolatore indipendente;
- il futuro motore Java potrà implementare le formule senza dipendere da celle Excel.

## 16. Principi dell'estensione per risorse aggiuntive

Le risorse aggiuntive sono rappresentate da una struttura grafica comune, ma appartengono a tre tipi matematici distinti:

1. **investimento o PAC esistente**: uno stock di capitale con eventuali versamenti periodici;
2. **rendita periodica**: un flusso mensile netto che può essere investito prima del FIRE e/o ridurre il fabbisogno durante il FIRE;
3. **capitale futuro una tantum**: un importo disponibile una sola volta a un confine temporale futuro.

Il nome assegnato dall'utente, come “PAC ETF”, “Affitto appartamento” o “Obbligazioni”, è descrittivo e non cambia le formule. Il comportamento dipende esclusivamente dal tipo e dai suoi parametri.

Tutte le risorse sono additive. Il motore non può riconoscere automaticamente che due input rappresentano lo stesso capitale: l'interfaccia deve quindi spiegare che `V_0` e i patrimoni delle risorse aggiuntive devono riferirsi a somme distinte.

Un patrimonio che produce reddito può richiedere due risorse collegate concettualmente:

- lo stock patrimoniale, se sarà disponibile per finanziare il FIRE;
- la rendita periodica, se il reddito viene incassato e usato separatamente.

Se il rendimento dello stock è un **total return** comprensivo dei proventi reinvestiti, gli stessi proventi non devono essere inseriti anche come rendita. Se cedole o canoni vengono modellati come rendita, il rendimento dello stock deve escluderli. Questa scelta resta esplicita e sotto la responsabilità dell'utente.

## 17. Asse temporale e input delle risorse

Si introduce il mese assoluto `t`, contato dal confine dell'età attuale:

```text
t = 0                         → istante iniziale della simulazione
t = N_acc                     → ingresso nel FIRE
t = N_acc + N_fire            → fine dell'orizzonte FIRE
```

Un intervallo periodico definito da `n_start` e `n_end` è attivo negli intervalli mensili:

```text
n_start <= t < n_end
```

L'estremo iniziale è incluso e quello finale è escluso. Durante l'accumulo, il flusso dell'intervallo `t` viene versato alla fine di quel mese. Durante il FIRE, una rendita attiva nell'intervallo `t` è disponibile all'inizio del corrispondente mese e riduce il prelievo eseguito nello stesso istante.

Le età delle risorse seguono la convenzione semplice dell'MVP e vengono trasformate in mesi con:

```text
n_x = 12 × (A_x − A_0)
```

Non vengono introdotte date di calendario o frazioni di mese.

### 17.1 Investimento o PAC esistente

Per ogni risorsa `q`:

| Simbolo | Input | Significato |
| --- | --- | --- |
| `I_q,0` | Patrimonio attuale | capitale nominale al mese zero |
| `P_q,1` | Versamento mensile iniziale | primo versamento della risorsa |
| `r_qa` | Rendimento annuo | total return nominale effettivo della risorsa |
| `h_qa` | Crescita annua del versamento | tasso annuo effettivo |
| `n_q,start` | Inizio dei nuovi versamenti | confine mensile; default zero |
| `n_q,end` | Fine dei nuovi versamenti | confine mensile; non oltre `N_acc` |
| `available_q` | Disponibile al FIRE | se vero, il saldo concorre al capitale FIRE |

Per un PAC già attivo, i versamenti storici sono già compresi in `I_q,0`. La data di inizio riguarda soltanto i versamenti futuri inclusi nella simulazione.

### 17.2 Rendita periodica

Per ogni rendita `q`:

| Simbolo | Input | Significato |
| --- | --- | --- |
| `R_q,0` | Importo mensile netto in euro di oggi | importo al potere d'acquisto e al livello nominale del mese zero |
| `u_qa` | Crescita o indicizzazione annua | tasso annuo effettivo della rendita |
| `n_q,start` | Inizio della rendita | confine mensile incluso |
| `n_q,end` | Fine della rendita | confine mensile escluso, oppure fine aperta |
| `investBeforeFire_q` | Investita prima del FIRE | se vero, i flussi di accumulo vengono investiti |
| `offsetDuringFire_q` | Riduce la spesa nel FIRE | se vero, la rendita riduce il prelievo lordo |

`R_q,0` è sempre espresso in euro netti di oggi, anche se la rendita inizierà in futuro. L'importo nominale del mese assoluto `t` è:

```text
R_q(t) = R_q,0 × (1 + u_qm)^t
```

Una rendita nominalmente fissa usa `u_qa = 0`. Una rendita indicizzata come l'inflazione usa `u_qa = i_a`. Il simulatore non calcola imposte, costi, sfitto o insolvenza: tali effetti devono essere già incorporati nell'importo netto inserito.

### 17.3 Capitale futuro una tantum

Per ogni capitale `l`:

| Simbolo | Input | Significato |
| --- | --- | --- |
| `K_l,0` | Importo dichiarato | euro di oggi oppure euro nominali futuri |
| `basis_l` | Base dell'importo | `TODAY` o `NOMINAL` |
| `n_l` | Mese di disponibilità | confine mensile della ricezione |
| `investAfterReceipt_l` | Investito dopo la ricezione | se vero, prima del FIRE matura il rendimento indicato |
| `r_la` | Rendimento annuo dopo la ricezione | usato tra ricezione e FIRE; ignorato quando il capitale resta liquido |

Il valore nominale al momento della ricezione è:

```text
basis_l = TODAY    → K_l(receipt) = K_l,0 × (1 + i_m)^n_l
basis_l = NOMINAL  → K_l(receipt) = K_l,0
```

Il capitale è accreditato al confine `n_l`. Se `n_l = 0`, è disponibile all'inizio della simulazione. Se `n_l = N_acc`, è disponibile esattamente all'ingresso nel FIRE e partecipa al capitale di accumulo; non viene contato una seconda volta come flusso del primo mese FIRE.

Se `n_l > N_acc`, il capitale entra direttamente nel portafoglio di decumulo al momento della ricezione e da quel momento usa `r_fm`. In questo caso `investAfterReceipt_l` e `r_la` non modificano il flusso durante il FIRE.

## 18. Accumulo con risorse aggiuntive

### 18.1 Investimenti e PAC esistenti

Per il mese di accumulo `j`, con `j` da 1 a `N_acc`, l'intervallo assoluto corrispondente è `t = j − 1`.

Il versamento della risorsa `q` è:

```text
P_q,j = P_q,1 × (1 + h_qm)^(t − n_q,start)    se n_q,start <= t < n_q,end
P_q,j = 0                                      altrimenti
```

Il capitale produce rendimento prima del versamento:

```text
I_q,j = I_q,j−1 × (1 + r_qm) + P_q,j
```

Il rendimento continua a maturare anche dopo la fine dei versamenti. Il saldo `I_q,N_acc` entra nel capitale disponibile al FIRE soltanto quando `available_q = true`.

### 18.2 Rendite reinvestite prima del FIRE

Le rendite con `investBeforeFire_q = true` confluiscono nel portafoglio principale e usano il rendimento mensile globale di accumulo `r_am`. Non viene richiesto un ulteriore rendimento alla rendita.

Con `E_0 = 0`:

```text
incomeInvested_j = Σ R_q(j − 1)
                    per le rendite attive con investBeforeFire_q = true

E_j = E_j−1 × (1 + r_am) + incomeInvested_j
```

Il versamento avviene alla fine del mese. Le rendite non investite prima del FIRE non influenzano il calcolo di accumulo.

### 18.3 Capitali una tantum ricevuti entro il FIRE

Per un capitale con `0 <= n_l <= N_acc`, il valore disponibile all'ingresso nel FIRE è:

```text
investAfterReceipt_l = true
    → FV_l = K_l(receipt) × (1 + r_lm)^(N_acc − n_l)

investAfterReceipt_l = false
    → FV_l = K_l(receipt)
```

Un capitale ricevuto al mese `N_acc` ha esponente zero ed è disponibile senza rendimento intermedio.

### 18.4 Capitale disponibile e PAC aggiuntivo

Prima del nuovo PAC richiesto dal simulatore, il capitale complessivo disponibile all'ingresso nel FIRE è:

```text
FV_available =
    V_0 × (1 + r_am)^N_acc
    + Σ I_q,N_acc per available_q = true
    + E_N_acc
    + Σ FV_l per n_l <= N_acc
```

Il capitale residuo da costruire è:

```text
Gap = max(0, T_target − FV_available)
```

Il nuovo PAC richiesto conserva il rendimento `r_am`, la crescita `g_m`, il timing a fine mese e il fattore `F` della sezione 8:

```text
C_1 = Gap / F
```

`C_1` rappresenta esclusivamente il PAC aggiuntivo ancora necessario. I versamenti `P_q,j` già programmati nelle risorse non vengono sommati al valore mostrato come nuovo PAC.

Se `N_acc = 0`, vengono considerate tutte le risorse disponibili al confine del FIRE. `UNREACHABLE_WITH_ZERO_MONTHS` si verifica soltanto quando, dopo tali risorse, `Gap > 0`.

## 19. Rendite e capitali durante il FIRE

Per il mese FIRE `k`, con `k` da 1 a `N_fire`, il mese assoluto all'inizio dell'intervallo è:

```text
t_k = N_acc + k − 1
```

La rendita disponibile nel mese è:

```text
R_k = Σ R_q(t_k)
      per n_q,start <= t_k < n_q,end
      e offsetDuringFire_q = true
```

Il prelievo netto richiesto al portafoglio è:

```text
D_k = max(0, W_k − R_k)
```

L'eventuale parte di `R_k` superiore a `W_k` non viene automaticamente reinvestita e non genera un prelievo negativo. Un futuro ampliamento potrà introdurre una scelta esplicita per il reinvestimento dell'eccedenza.

Un capitale una tantum ricevuto dopo l'ingresso nel FIRE genera `K_k` nel mese corrispondente:

```text
n_l > N_acc
k_l = n_l − N_acc + 1
K_k = Σ K_l(receipt) per k_l = k
```

`K_k` è disponibile all'inizio del mese, prima del prelievo. I capitali con `n_l <= N_acc` sono già inclusi in `FV_available` e non compaiono in `K_k`.

## 20. Target FINITE con flussi variabili

Il target a durata finita viene generalizzato con una ricorrenza all'indietro in termini nominali. Questa forma gestisce rendite variabili e capitali una tantum senza cambiare il timing del prelievo anticipato.

Il capitale richiesto alla fine dell'ultimo mese è:

```text
Q_N_fire = L_end_nominal
```

Per `k` da `N_fire` a 1:

```text
Q_(k−1) = max(0, D_k + Q_k / (1 + r_fm) − K_k)
```

Il target è:

```text
T_finite_resources = Q_0
```

La ricorrenza rispetta questo ordine nel mese `k`:

1. viene ricevuto l'eventuale capitale `K_k`;
2. avviene il prelievo netto `D_k`;
3. il capitale residuo produce rendimento `r_fm`.

Il `max(0, ...)` impedisce a un capitale futuro eccedente di creare un fabbisogno iniziale negativo e non consente di usare retroattivamente una risorsa per finanziare mesi precedenti alla sua disponibilità.

Quando una risorsa inevitabile produce un'eccedenza, il capitale finale desiderato è trattato come minimo da conservare: il saldo effettivo può risultare superiore, perché non sono ammessi patrimonio iniziale o prelievi negativi.

Senza rendite e senza capitali durante il FIRE, la ricorrenza deve coincidere entro `0,01 euro` con la formula chiusa della rendita anticipata della sezione 6.

## 21. Target SWR con capitale ponte e riserva

La SWR resta un benchmark sul primo prelievo annuo di un regime stabile. Una rendita futura non viene sottratta direttamente da `W_1` prima che sia disponibile.

### 21.1 Target base

Si conserva sempre il target che ignora le risorse esterne:

```text
T_swr_base = W_1 × 12 / swr
```

Questo valore garantisce la compatibilità con l'MVP e rappresenta l'alternativa nella quale l'utente non usa le risorse aggiuntive per ridurre il target.

### 21.2 Inizio del regime stabile

Si definisce `p` come il primo mese del regime successivo all'ultima variazione strutturale dei flussi durante l'orizzonte FIRE. Sono variazioni strutturali:

- inizio di una rendita;
- fine di una rendita;
- ricezione di un capitale una tantum durante il FIRE.

L'indicizzazione mensile di una rendita già attiva non costituisce una nuova variazione strutturale. Se non esistono variazioni dopo l'ingresso nel FIRE, `p = 1`. Una rendita attiva dal primo mese e valida per tutto l'orizzonte appartiene quindi al regime iniziale stabile.

Se l'ultima variazione cade nel mese `e`, allora `p = e`, perché `D_e` e `K_e` riflettono già il nuovo regime. Le variazioni successive alla fine dell'orizzonte selezionato non sono considerate.

### 21.3 Riserva SWR e capitale ponte

La riserva necessaria all'inizio del regime stabile è:

```text
S_p = D_p × 12 / swr
```

Un capitale una tantum disponibile proprio nel mese `p` può finanziare tale riserva:

```text
H_p = max(0, S_p − K_p)
```

Il capitale ponte viene calcolato all'indietro per i mesi precedenti. Per `k` da `p − 1` a 1:

```text
H_k = max(0, D_k + H_(k+1) / (1 + r_fm) − K_k)
```

Il candidato composto è:

```text
T_swr_bridge = H_1
```

Il target SWR selezionato non può aumentare a causa dell'aggiunta di una risorsa opzionale:

```text
T_swr_resources = min(T_swr_base, T_swr_bridge)
```

Questa regola ha le seguenti conseguenze intenzionali:

- una rendita permanente già attiva riduce direttamente la spesa sulla quale si applica la SWR;
- una rendita permanente futura richiede prima un capitale ponte e poi una riserva SWR sul fabbisogno residuo;
- una rendita temporanea può migliorare la proiezione senza ridurre il target quando preservare la riserva successiva produrrebbe un candidato superiore al target base;
- un capitale futuro riduce il target soltanto se il suo beneficio supera il costo del ponte necessario per raggiungerlo;
- nessuna risorsa può rendere il target maggiore di quello calcolato ignorandola.

Se non esistono risorse aggiuntive, `p = 1`, `D_1 = W_1`, `K_1 = 0` e quindi:

```text
T_swr_resources = T_swr_base = W_1 × 12 / swr
```

Se dopo `p` rendita e spesa crescono a tassi differenti, `S_p` resta un benchmark basato sul primo mese del regime. La proiezione mensile conserva il ruolo di verifica della sostenibilità e deve segnalare l'eventuale shortfall.

### 21.4 Selezione del target

Con risorse aggiuntive:

```text
FINITE  → T_target = T_finite_resources
SWR     → T_target = T_swr_resources
```

L'equivalente in euro di oggi resta:

```text
T_target_today = T_target / (1 + i_m)^N_acc
```

## 22. Proiezioni mensili generalizzate

### 22.1 Accumulo

La proiezione deve mantenere separati almeno:

- saldo del portafoglio principale e nuovo PAC richiesto;
- saldo di ogni investimento o PAC esistente;
- rendite reinvestite;
- capitali una tantum ricevuti;
- capitale complessivo disponibile al FIRE.

I grafici possono aggregare le serie, ma i dati di dominio e API non devono perdere la provenienza necessaria a verificare il calcolo.

### 22.2 Decumulo

La proiezione personale parte dal capitale effettivamente disponibile:

```text
B_0 = max(T_target, saldo finale effettivo delle sole risorse disponibili al FIRE)
```

Per ogni mese `k`:

```text
available_k = B_(k−1) + K_k
actual_withdrawal_k = min(max(available_k, 0), D_k)
shortfall_k = D_k − actual_withdrawal_k
return_k = (available_k − actual_withdrawal_k) × r_fm
B_k = max(0, available_k − actual_withdrawal_k + return_k)
```

Lo shortfall misura la parte del fabbisogno netto non coperta dopo le rendite. I risultati devono rendere disponibili, per ogni mese, almeno spesa lorda `W_k`, rendite `R_k`, prelievo netto programmato `D_k`, capitale una tantum `K_k`, prelievo effettivo e saldo finale.

L'eventuale eccedenza della rendita rispetto alla spesa resta esclusa dal saldo, come stabilito nella sezione 19.

## 23. Validazioni e prevenzione del doppio conteggio

Si applicano le validazioni generali della sezione 11. Inoltre:

- importi, patrimoni, versamenti e capitali devono essere finiti e non negativi;
- tutti i tassi annui di rendimento, crescita e indicizzazione devono essere finiti e maggiori di `−100%`;
- le età sono intere e trasformabili in mesi senza overflow;
- l'inizio di un intervallo periodico deve precedere la fine;
- i nuovi versamenti di un investimento o PAC non possono proseguire oltre l'ingresso nel FIRE;
- una rendita può avere fine aperta; se ha una fine esplicita, questa deve essere successiva all'inizio;
- una risorsa completamente esterna all'orizzonte di simulazione è rifiutata perché non produrrebbe alcun effetto;
- un capitale una tantum deve essere disponibile tra il mese zero e la fine dell'orizzonte FIRE;
- il rendimento successivo alla ricezione è ignorato quando `investAfterReceipt = false`;
- una risorsa patrimoniale con `available = false` non riduce il PAC né aumenta il capitale iniziale del FIRE; un suo eventuale reddito deve essere modellato separatamente come rendita periodica.

Non è possibile deduplicare matematicamente input descritti dall'utente. L'interfaccia deve mostrare queste regole:

1. `Patrimonio investito oggi` contiene soltanto il portafoglio principale non inserito in altre card;
2. il patrimonio iniziale di un investimento aggiuntivo non deve essere incluso anche in `V_0`;
3. cedole, dividendi o canoni non devono essere inseriti come rendita se sono già compresi nel total return reinvestito dello stesso patrimonio;
4. l'importo di affitti, pensioni e altre entrate è netto e non viene fiscalizzato dal motore.

## 24. Esempi numerici e criteri di accettazione

Gli esempi seguenti isolano le nuove regole usando, dove indicato, inflazione e rendimento pari a zero. Gli importi non sostituiscono i golden scenario della Fase 5.

### 24.1 Compatibilità senza risorse

Con elenco delle risorse vuoto:

- `D_k = W_k`;
- `K_k = 0`;
- il target `FINITE` coincide con la formula della sezione 6;
- il target `SWR` coincide con `W_1 × 12 / swr`;
- accumulo, PAC, decumulo e proiezioni coincidono entro le tolleranze della sezione 12 con l'MVP precedente.

### 24.2 Rendita permanente con FINITE

Ipotesi semplificate:

- spesa: `2.000 euro/mese`;
- rendita netta già attiva e permanente: `500 euro/mese`;
- durata FIRE: `360 mesi`;
- rendimento e inflazione: `0%`;
- capitale finale desiderato: zero.

Il prelievo netto è `1.500 euro/mese` e:

```text
T_finite_resources = 1.500 × 360 = 540.000 euro
```

Senza la rendita il target sarebbe `720.000 euro`.

### 24.3 Pensione futura permanente con SWR

Ipotesi semplificate:

- spesa: `2.000 euro/mese`;
- SWR: `4%`;
- pensione: `1.000 euro/mese` dall'inizio del mese 121 e poi permanente;
- rendimento e inflazione: `0%`.

Il target base è:

```text
T_swr_base = 2.000 × 12 / 4% = 600.000 euro
```

Dal mese 121 il fabbisogno stabile è `1.000 euro/mese`, quindi:

```text
S_121 = 1.000 × 12 / 4% = 300.000 euro
capitale ponte = 2.000 × 120 = 240.000 euro
T_swr_bridge = 540.000 euro
T_swr_resources = min(600.000, 540.000) = 540.000 euro
```

### 24.4 Rendita temporanea con SWR

Ipotesi semplificate:

- spesa: `2.000 euro/mese`;
- SWR: `4%`;
- rendita: `500 euro/mese` soltanto nei primi 120 mesi;
- rendimento e inflazione: `0%`.

Dopo la fine della rendita serve nuovamente la riserva piena di `600.000 euro`. Il candidato composto sarebbe:

```text
T_swr_bridge = 1.500 × 120 + 600.000 = 780.000 euro
```

La rendita opzionale non può aumentare il target:

```text
T_swr_resources = min(600.000, 780.000) = 600.000 euro
```

La rendita migliora comunque il saldo e la sostenibilità della proiezione dei primi 120 mesi.

### 24.5 Investimento già esistente

Un portafoglio obbligazionario distinto dal patrimonio principale contiene `100.000 euro`, non riceve nuovi versamenti, rende il `5% annuo effettivo` ed è disponibile tra 10 anni. La conversione mensile equivalente deve produrre lo stesso valore della capitalizzazione annua:

```text
I_N = 100.000 × (1 + 5%)^10 = 162.889,46 euro circa
```

Questo saldo riduce il `Gap`. Le cedole non vengono aggiunte come rendita se il 5% rappresenta già il total return reinvestito.

### 24.6 Confini temporali

- una rendita con inizio all'età FIRE riduce `D_1`;
- una rendita con fine all'età FIRE non riduce `D_1`;
- un capitale ricevuto all'età FIRE entra in `FV_available` e non in `K_1`;
- il primo versamento di un PAC che inizia oggi avviene alla fine del primo mese;
- l'ultimo versamento di un PAC con fine all'età FIRE avviene alla fine del mese `N_acc`;
- con zero mesi di accumulo, le risorse disponibili al confine iniziale vengono considerate prima di dichiarare lo scenario irraggiungibile.

### 24.7 Criterio di completamento della Fase 1

La Fase 1 dell'estensione è pronta per l'approvazione quando:

- le tre tipologie hanno input, unità e timing non ambigui;
- `FINITE` è definito per flussi mensili variabili;
- `SWR` distingue target base, capitale ponte e riserva stabile;
- nessuna risorsa può aumentare il target SWR;
- l'assenza di risorse garantisce compatibilità numerica con tutti i golden scenario esistenti;
- sono documentati doppio conteggio, importi netti, eccedenze di rendita e confini temporali;
- le decisioni possono essere tradotte in dominio e API senza dipendere dal workbook Excel.
