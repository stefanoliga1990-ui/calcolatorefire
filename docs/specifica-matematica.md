# Calcolo FIRE Italia — specifica matematica dell'MVP

Stato: convenzioni matematiche approvate per l'implementazione dell'MVP.

Questa specifica è la fonte primaria del motore Java. Il workbook Excel resta un riferimento funzionale, ma non è un golden master finché le differenze indicate in fondo al documento non vengono corrette.

## 1. Perimetro

L'MVP calcola:

1. il capitale necessario all'ingresso nel FIRE;
2. il versamento mensile iniziale necessario per raggiungerlo;
3. le proiezioni mensili di accumulo e decumulo.

Sono esclusi dall'MVP:

- altri redditi, pensioni e rendite esterne;
- fiscalità sulle vendite e sulle plusvalenze;
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

## 5. Spesa all'ingresso nel FIRE

Il primo prelievo nominale è la spesa mensile di oggi rivalutata per tutti i mesi di accumulo:

```text
W_1 = S_0 × (1 + i_m)^N_acc
```

Il prelievo programmato del mese `k`, con `k` che parte da 1, è:

```text
W_k = W_1 × (1 + i_m)^(k − 1)
```

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
6. Gli altri redditi presenti nel workbook sono esclusi dall'MVP.
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
