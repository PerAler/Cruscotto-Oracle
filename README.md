# Cruscotto Oracle - Spring Boot

Cruscotto web per:
- caricare procedure/script Oracle da file `.sql`
- eseguire manualmente con parametri
- pianificare esecuzioni tramite espressioni cron
- visualizzare log di esecuzione in memoria (non storico su DB)

## Requisiti
- JDK 21
- Maven 3.9+
- Accesso a database Oracle

## Configurazione DB
Aggiorna `src/main/resources/application.properties`:
- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`

Esempio URL Oracle:
`jdbc:oracle:thin:@//HOST:1521/SERVICE_NAME`

## SQL delle procedure
Inserisci i file SQL in `src/main/resources/sql`.

Per passare parametri, usa placeholder bind nel file SQL, ad esempio:
`TO_DATE(:data_fattura, 'DD-MON-YYYY')`

Nel cruscotto il parametro verra mostrato automaticamente e inviato come stringa.

## Avvio
```bash
mvn spring-boot:run
```
Apri `http://localhost:8090`.

## Uso scheduler
Nel riquadro di ogni procedura inserisci un cron Spring a 6 campi, ad esempio:
- Ogni 30 minuti: `0 0/30 * * * *`
- Ogni giorno alle 02:15: `0 15 2 * * *`

Per un lancio singolo usa il form "Lancio singolo (data e ora locale)".
La schedulazione one-shot viene rimossa automaticamente dopo l'esecuzione.

## Note tecniche
- I log sono in memoria (buffer FIFO con dimensione configurabile da `app.logs.max-size`).
- Le schedulazioni sono in memoria e vengono azzerate al riavvio.
- Se vuoi uno storico persistente, posso aggiungere tabella Oracle + repository per salvataggio log.


taskkill /F /IM java.exe /T 2>&1 | Out-String
taskkill /F /IM mvn.bat /T 2>&1 | Out-String
Write-Host "Process cleanup complete"