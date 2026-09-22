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

- `FireCalculatorStressCoverageTest`, con 16 esecuzioni JUnit sul dominio;
- `FireCalculationStressApiTest`, con 3 esecuzioni MockMvc sul contratto API.

La copertura comprende:

- 60 anni di accumulo e 80 anni di FIRE, pari a 721 e 961 punti mensili
  comprensivi del mese zero, con FINITE e SWR;
- 150 risorse sovrapposte nel dominio e 90 risorse in una singola richiesta
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

- 262 test Maven: 0 fallimenti, 0 errori, 0 ignorati;
- 21 scenari golden riconciliati dal calcolatore indipendente entro 0,01 euro;
- nessuna modifica al motore, al contratto API o al frontend necessaria.

