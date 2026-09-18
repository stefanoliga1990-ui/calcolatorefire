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
  "annualContributionGrowthRate": 0
}
```

Valori ammessi per `method`:

- `FINITE`
- `SWR`

`annualSafeWithdrawalRate` è richiesto solo per `SWR` e può essere omesso o `null` per `FINITE`. `terminalCapitalToday` è usato solo da `FINITE`; per `SWR` inviare `0`. La durata FIRE e il rendimento FIRE restano richiesti per la proiezione del decumulo.

### Risposta `200 OK`

La risposta contiene:

- `accumulationMonths` e `fireMonths`;
- `rates`, con i tassi mensili equivalenti;
- `target`, con il solo target del metodo selezionato;
- `accumulation`, con PAC richiesto e proiezione mensile;
- `decumulation`, con prelievi, saldo, eventuale shortfall e proiezione mensile.

Esempio sintetico, con le serie mensili omesse:

```json
{
  "accumulationMonths": 168,
  "fireMonths": 420,
  "target": {
    "firstMonthlyWithdrawal": 2111.1660209006,
    "finiteTarget": 557770.7040214724,
    "selectedTarget": 557770.7040214724,
    "selectedTargetToday": 422720.4860249008
  },
  "accumulation": {
    "initialMonthlyContribution": 1905.5163192213,
    "projectedFinalBalance": 557770.7040214724,
    "projection": ["una voce iniziale e una per ogni mese"]
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
- `UNREACHABLE_WITH_ZERO_MONTHS`

