# Verbale test di stress — Fase 10

Data: 22 settembre 2026  
Branch: `rendite-aggiuntive`  
Revisione di partenza: `5e5ef034c1a2463a4dd4ad0f8c48df8e018a301e`

## Obiettivo

Verificare che il calcolatore resti stabile con orizzonti lunghi, molte risorse,
importi estremi e tassi vicini al limite inferiore ammesso, senza modificare le
formule o il codice di produzione.

## Verifiche automatiche

Sono state aggiunte le classi:

- `FireCalculatorStressCoverageTest`, con 18 esecuzioni JUnit sul dominio;
- `FireCalculationStressApiTest`, con 3 esecuzioni MockMvc sul contratto API.

Dopo la fase sono stati aggiunti anche 12 test di dominio e 4 test API per i
nuovi limiti superiori di età, importi, tassi e numero di risorse.

La copertura comprende:

- 50 anni di accumulo e 60 anni di FIRE, con età finale pari al massimo
  supportato di 130 anni e 601 e 721 punti mensili
  comprensivi del mese zero, con FINITE e SWR;
- 90 risorse sovrapposte nel dominio e in una singola richiesta
  API, con verifica di quantità, indici di provenienza e serializzazione;
- importi di un centesimo e scenari nell'ordine dei miliardi di euro;
- rendimento FIRE, rendimento di accumulo, crescita PAC e inflazione pari a
  `-99,9999%`, `-99%` e `-90%`, verificati separatamente con FINITE e SWR;
- rifiuto controllato di durate che non possono essere convertite in mesi
  senza overflow, con codice `INVALID_FIRE_DURATION`;
- finitezza e non negatività dei risultati monetari e finitezza dei rendimenti
  mensili, che possono essere negativi.

## Verifica nel browser

Sul frontend e backend locali reali sono state create 30 card tramite i
controlli dell'interfaccia:

- 10 investimenti o PAC esistenti, con capitali da 1.000 a 10.000 euro e
  versamenti da 10 a 100 euro al mese;
- 10 rendite periodiche, da 10 a 100 euro al mese;
- 10 capitali futuri, da 1.000 a 10.000 euro.

Il calcolo completo è stato eseguito con entrambi i metodi. Sono stati
osservati:

| Metodo | Tempo osservato nel browser | Esito |
| --- | ---: | --- |
| Durata finita | 547 ms | superato |
| SWR | 294 ms | superato |

In entrambi i casi:

- tutte le 30 card e le 40 opzioni di utilizzo sono rimaste presenti e attive;
- il riepilogo ha incluso investimenti, rendite e capitali futuri;
- i due grafici sono stati renderizzati;
- risultati e riepiloghi non contenevano `NaN`, `Infinity` o `undefined`;
- la console non ha prodotto errori o warning.

I tempi sono misure indicative sul computer locale e non costituiscono una
soglia prestazionale automatica.

## Esito complessivo

- 280 test Maven: 0 fallimenti, 0 errori, 0 ignorati;
- 21 scenari golden riconciliati dal calcolatore indipendente entro 0,01 euro;
- limiti applicati in modo coerente nel dominio, nel contratto API e nel frontend.
