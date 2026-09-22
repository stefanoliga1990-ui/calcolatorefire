# API Calcolo FIRE Italia

## Calcolo completo

```http
POST /api/v1/fire/calculations
Content-Type: application/json
```

I tassi sono numeri decimali: `0.02` rappresenta il 2%.

### Richiesta

```json
{
  "method": "FINITE",
  "currentAge": 36,
  "fireAge": 50,
  "fireDurationYears": 35,
  "monthlyExpenseToday": 1600,
  "annualInflationRate": 0.02,
  "annualFireReturnRate": 0.05,
  "terminalCapitalToday": 0,
  "currentCapital": 10000,
  "annualAccumulationReturnRate": 0.07,
  "annualContributionGrowthRate": 0,
  "additionalResources": []
}
```

Valori ammessi per `method`:

- `FINITE`
- `SWR`

`annualSafeWithdrawalRate` è richiesto solo per `SWR` e può essere omesso o `null` per `FINITE`. `terminalCapitalToday` è usato solo da `FINITE`; per `SWR` inviare `0`. La durata FIRE e il rendimento FIRE restano richiesti per la proiezione del decumulo.

Nel metodo `SWR`, aumentare `annualSafeWithdrawalRate` riduce matematicamente il target. La sostenibilità sull'orizzonte scelto va letta nei campi `decumulation.depletionMonth` e `decumulation.totalShortfall`: un `depletionMonth` valorizzato indica il primo mese in cui il prelievo programmato non è interamente coperto.

Vincoli comuni della richiesta:

- età intere comprese tra 0 e 130, con età finale della simulazione non oltre 130;
- importi compresi tra 0 e `1.000.000.000.000` euro;
- inflazione, rendimenti e crescite maggiori di `-1` e non superiori a `1`;
- SWR finita e strettamente positiva, senza un massimo arbitrario;
- massimo 100 risorse aggiuntive.

### Risorse aggiuntive

`additionalResources` è una lista facoltativa. Se il campo è omesso, vale `[]`: richieste create prima dell'estensione conservano quindi lo stesso comportamento e gli stessi risultati numerici.

Ogni elemento usa `type` come discriminante e può avere un `name` facoltativo, lungo al massimo 100 caratteri. I tipi riconosciuti sono:

- `EXISTING_INVESTMENT`: investimento o PAC già esistente;
- `PERIODIC_INCOME`: rendita periodica attuale o futura;
- `FUTURE_LUMP_SUM`: capitale futuro ricevuto una sola volta.

Le età sono intere e rappresentano confini mensili. L'età iniziale è inclusa, quella finale è esclusa. Si applicano anche alle risorse i limiti comuni su età, importi e tassi; come nel resto dell'API, `0.05` rappresenta il 5%.

#### Investimento o PAC esistente

```json
{
  "type": "EXISTING_INVESTMENT",
  "name": "PAC già attivo",
  "currentCapital": 25000,
  "initialMonthlyContribution": 300,
  "contributionStartAge": 36,
  "contributionEndAge": 50,
  "annualReturnRate": 0.05,
  "annualContributionGrowthRate": 0,
  "availableAtFire": true
}
```

- `currentCapital` è distinto dal `currentCapital` principale della richiesta e non deve essere conteggiato anche lì;
- `initialMonthlyContribution` è il primo versamento futuro, eseguito a fine mese;
- se `initialMonthlyContribution` è maggiore di zero, entrambe le età dei versamenti sono obbligatorie e devono rispettare `currentAge <= contributionStartAge < contributionEndAge <= fireAge`;
- se non sono previsti nuovi versamenti, `initialMonthlyContribution` vale zero e le due età possono essere entrambe `null`;
- `annualReturnRate` è il total return della risorsa;
- `availableAtFire` indica se il saldo potrà contribuire al capitale FIRE.

#### Rendita periodica

```json
{
  "type": "PERIODIC_INCOME",
  "name": "Pensione",
  "monthlyAmountToday": 1000,
  "annualGrowthRate": 0.02,
  "startAge": 67,
  "endAge": null,
  "investBeforeFire": false,
  "offsetDuringFire": true
}
```

- `monthlyAmountToday` è un importo mensile netto in euro di oggi;
- `endAge: null` indica una rendita senza fine nell'orizzonte simulato;
- `investBeforeFire` stabilisce se i flussi precedenti al FIRE confluiscono nel portafoglio di accumulo;
- `offsetDuringFire` stabilisce se i flussi durante il FIRE riducono il prelievo richiesto;
- almeno uno dei due indicatori deve essere `true` e la rendita deve sovrapporsi alla fase nella quale viene usata.

#### Capitale futuro una tantum

```json
{
  "type": "FUTURE_LUMP_SUM",
  "name": "Capitale futuro",
  "amount": 50000,
  "amountBasis": "TODAY",
  "receiptAge": 60,
  "investAfterReceipt": true,
  "annualReturnRateAfterReceipt": 0.03
}
```

- `amountBasis` ammette `TODAY`, per un importo espresso in euro di oggi, e `NOMINAL`, per un importo nominale alla data di ricezione;
- `receiptAge` deve essere compresa tra `currentAge` e `fireAge + fireDurationYears`;
- `annualReturnRateAfterReceipt` è usato prima del FIRE soltanto quando `investAfterReceipt` è `true`; negli altri casi viene ignorato, ma deve comunque essere fornito.

Gli investimenti esistenti e le rendite periodiche sono applicati dal motore:

- il saldo degli investimenti con `availableAtFire: true` e le rendite reinvestite prima del FIRE riducono il capitale ancora da costruire con il nuovo PAC;
- le rendite con `offsetDuringFire: true` riducono il prelievo netto e modificano il target `FINITE` o il capitale ponte del metodo `SWR`;
- un investimento con `availableAtFire: false` viene proiettato e restituito dall'API, ma non riduce il PAC e non entra nel capitale FIRE.

I capitali `FUTURE_LUMP_SUM` sono applicati in base al momento di ricezione:

- prima o esattamente all'ingresso nel FIRE entrano nel capitale disponibile e riducono il nuovo PAC richiesto;
- prima del FIRE maturano `annualReturnRateAfterReceipt` soltanto quando `investAfterReceipt` è `true`;
- durante il FIRE entrano all'inizio del mese, prima del prelievo, e possono ridurre i target `FINITE` e `SWR`;
- esattamente alla fine dell'orizzonte entrano dopo il rendimento dell'ultimo mese e possono soddisfare il capitale finale desiderato.

### Risposta `200 OK`

La risposta contiene:

- `accumulationMonths` e `fireMonths`;
- `rates`, con i tassi mensili equivalenti;
- `target`, con il solo target del metodo selezionato;
- `accumulation`, con PAC richiesto, risorse disponibili e proiezioni mensili;
- `decumulation`, con spesa lorda, rendite, prelievi netti, saldo ed eventuale shortfall.

In `target`:

- `firstMonthlyWithdrawal` conserva per compatibilità la spesa lorda nominale del primo mese FIRE;
- `firstMonthlyAdditionalIncome` è la rendita che concorre nel primo mese;
- `firstMonthlyNetWithdrawal` è il prelievo effettivamente richiesto al portafoglio dopo la rendita;
- per `SWR`, `safeWithdrawalRateBaseTarget` è il target senza risorse e `safeWithdrawalRateTarget` è il target selezionato dopo capitale ponte e riserva stabile.

In `accumulation`:

- `totalNominalContributions` contiene soltanto i versamenti del nuovo PAC calcolato;
- `totalNominalAdditionalIncomeInvested` contiene le rendite reinvestite durante l'accumulo;
- `totalNominalExistingInvestmentContributions` contiene i versamenti dei PAC esistenti;
- `mainPortfolioFinalBalance` comprende patrimonio principale, nuovo PAC e rendite reinvestite;
- `investedIncomeFinalBalance` isola nel portafoglio principale la quota costruita dalle rendite;
- `availableExistingInvestmentsFinalBalance` somma gli investimenti disponibili al FIRE;
- `availableFutureLumpSumsFinalBalance` somma i capitali una tantum ricevuti entro l'ingresso nel FIRE;
- `projectedFinalBalance` è il capitale totale disponibile al FIRE;
- `existingInvestments` mantiene una proiezione distinta per ogni investimento, identificato dalla posizione `resourceIndex` nella lista della richiesta.
- `futureLumpSums` mantiene provenienza, mese di ricezione, importo nominale alla ricezione, saldo al FIRE ed eventuale mese FIRE di accredito. `fireReceiptMonth` usa valori da `1` a `fireMonths`; il valore `fireMonths + 1` identifica il confine finale.

Ogni voce di `decumulation.projection` espone `grossExpense`, `additionalIncome`, `scheduledWithdrawal` netto, `capitalInflow`, `actualWithdrawal`, `shortfall` e saldo. `terminalCapitalInflow` distingue un capitale ricevuto al confine finale da quelli disponibili all'inizio di un mese. Il riepilogo `decumulation` espone anche `totalCapitalInflows` e `terminalCapitalInflow`.

Esempio sintetico, con le serie mensili omesse:

```json
{
  "accumulationMonths": 168,
  "fireMonths": 420,
  "target": {
    "firstMonthlyWithdrawal": 2111.1660209006,
    "finiteTarget": 557770.7040214724,
    "selectedTarget": 557770.7040214724,
    "selectedTargetToday": 422720.4860249008,
    "firstMonthlyAdditionalIncome": 0,
    "firstMonthlyNetWithdrawal": 2111.1660209006
  },
  "accumulation": {
    "initialMonthlyContribution": 1905.5163192213,
    "mainPortfolioFinalBalance": 557770.7040214724,
    "availableExistingInvestmentsFinalBalance": 0,
    "availableFutureLumpSumsFinalBalance": 0,
    "projectedFinalBalance": 557770.7040214724,
    "projection": ["una voce iniziale e una per ogni mese"],
    "existingInvestments": [],
    "futureLumpSums": []
  },
  "decumulation": {
    "personalStartBalance": 557770.7040214724,
    "personalFinalBalance": 0,
    "totalShortfall": 0,
    "depletionMonth": null,
    "projection": ["una voce iniziale e una per ogni mese"]
  }
}
```

Gli importi non vengono arrotondati dall'API. Il frontend applicherà la formattazione in euro senza usare i valori visualizzati per altri calcoli.

## Errori

Gli errori usano `application/problem+json` e includono sempre `status`, `title`, `detail`, `instance`, `timestamp` e `code`.

### `400 Bad Request`

- `VALIDATION_ERROR`: uno o più campi obbligatori sono mancanti o formalmente invalidi. La proprietà `fieldErrors` contiene campo e messaggio.
- `MALFORMED_REQUEST`: JSON non valido o valore enum non riconosciuto.

### `422 Unprocessable Content`

La richiesta è formalmente corretta, ma viola una regola del dominio. I codici possibili sono:

- `INVALID_AGE_ORDER`
- `INVALID_FIRE_DURATION`
- `INVALID_RATE`
- `INVALID_SWR`
- `INVALID_AMOUNT`
- `INVALID_METHOD`
- `INVALID_RESOURCE`: configurazione della risorsa incompleta o priva di un utilizzo;
- `INVALID_RESOURCE_PERIOD`: età o intervallo della risorsa incompatibile con lo scenario;
- `UNREACHABLE_WITH_ZERO_MONTHS`

