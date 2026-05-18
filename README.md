# Cruscotto Oracle — Spring Boot

Applicazione web Spring Boot 3 per la gestione, esecuzione e monitoraggio di script SQL/PL-SQL su database Oracle.

---

## Funzionalità

### Dashboard principale (`/dashboard`)
- **Catalogo script SQL** — carica automaticamente tutti i file `.sql` presenti nella cartella configurata (`app.sql.folder`, default `sql`); filtro per nome in tempo reale.
- **Editor SQL rapido** — textarea con:
  - Syntax highlighting PL/SQL via Prism.js
  - Autocompletamento keyword SQL (Tab / frecce)
  - **Formattatore PL/SQL** — uppercase keyword, interruzioni di riga strutturali, indentazione automatica BEGIN/END/IF/LOOP, protezione stringhe e commenti
  - Persistenza bozza in `localStorage`
  - Estrazione automatica parametri bind (`:param`)
- **Esecuzione query** — esegue script SQL con parametri bind; supporta SELECT, WITH, INSERT, UPDATE
- **Salvataggio script** — salva nuovi file `.sql` nel catalogo oppure aggiorna script esistenti
- **Parametri bind** — inserimento nome=valore, uno per riga
- **Robot animato** nell'header con stato dinamico: `ok` / `script-error` / `db-offline`
- **KPI dashboard** — card cliccabili: Log totali, Esecuzioni OK, Errori attivi, Output generati
- **Schedulazioni cron** — pianificazione esecuzioni con espressione Spring cron a 6 campi; lancio singolo one-shot con data/ora locale

### Editor SQL dedicato (`/editor`)
- **Editor full-page** con numerazione righe sincronizzata
- **Formattatore PL/SQL** — identico alla dashboard (pulsante nell'action bar)
- **Robot animato** nell'header con stato dinamico (stesso robot della dashboard)
- Autocompletamento keyword SQL (Tab / frecce / Escape)
- Selezione script dal catalogo (caricamento AJAX, senza reload pagina)
- Ricarica catalogo da disco senza riavvio
- **Esegui** — AJAX con spinner, risultati visualizzati in iframe inline
- **Vista risultati** con toggle densità (compatta / estesa) e toggle larghezza colonne
- **Salva nuovo** / **Aggiorna script selezionato** (POST form)
- Estrazione automatica parametri bind

### Output (`/output/*`)
- Ogni esecuzione con risultati salva **un solo file CSV** nella cartella `output/`
- **HTML** — generato on-demand dal CSV al momento della visualizzazione; paginato (200 righe per pagina, max 1000)
- **XLSX** — generato on-demand dal CSV al momento del download; colonne auto-dimensionate, troncatura celle a 32.767 caratteri
- I link HTML/CSV/XLSX nella dashboard puntano tutti allo stesso bundle base (stesso timestamp); solo il CSV è persistito su disco
- Retention: massimo 100 bundle CSV (`app.output.max-items`); i file più vecchi vengono rimossi automaticamente ad ogni nuova esecuzione

### Log e monitoraggio
- **Log esecuzioni** (`/logs`) — tabella con filtro per stato (OK/KO), link ai file output (HTML/CSV/XLSX)
- **Log errori** (`/errors`) — vista dedicata ai soli KO
- **Persistenza log su Oracle** — tabella `CRUSCOTTO_EXEC_LOG`; ricaricamento all'avvio (`app.logs.persist.enabled=true`)
- Retention log configurabile (`app.logs.max-size`)

### API / Endpoint utili
| Endpoint | Metodo | Descrizione |
|---|---|---|
| `/dashboard` | GET | Pagina principale |
| `/editor` | GET | Editor SQL dedicato |
| `/query/execute` | POST | Esegui script dal cruscotto |
| `/query/save` | POST | Salva nuovo script |
| `/query/update` | POST | Aggiorna script esistente |
| `/editor/execute` | POST | Esegui query (AJAX, ritorna JSON) |
| `/editor/save` | POST | Salva script dall'editor |
| `/editor/update` | POST | Aggiorna script dall'editor |
| `/editor/load-script` | GET | Carica script (AJAX, ritorna JSON) |
| `/catalog/reload` | POST | Ricarica catalogo da disco |
| `/output/{filename}` | GET | Visualizza/scarica file output |
| `/logs` | GET | Pagina log esecuzioni |
| `/errors` | GET | Pagina log errori |

---

## Requisiti
- JDK 21
- Maven 3.9+
- Oracle Database (JDBC thin)

## Configurazione

`src/main/resources/application.properties`:

```properties
server.port=8090
spring.datasource.url=jdbc:oracle:thin:@//HOST:1521/SERVICE_NAME
spring.datasource.username=utente
spring.datasource.password=password

app.output.folder=output
app.output.max-items=100
app.logs.max-size=500
app.logs.persist.enabled=true
```

In avvio da console, l'app chiede anche se vuoi sovrascrivere URL, username e password direttamente prima del bootstrap Spring e poi apre automaticamente il browser sulla dashboard.

Quando parte, il terminale mostra tre richieste in ordine: `URL JDBC`, `Username JDBC`, `Password JDBC`. Inserisci il valore e premi `INVIO`; se vuoi mantenere quello già impostato, premi solo `INVIO`.

## Script SQL
Inserisci i file `.sql` nella cartella configurata con `app.sql.folder` (default `sql`).  
Per i parametri bind usa placeholder `:nomeparametro`, ad esempio:
```sql
SELECT * FROM fatture WHERE anno = :anno AND stato = :stato
```

## Avvio
```bash
cd C:\Temp\Cruscotto_Oracle
mvn clean package -DskipTests
java -jar target\cruscotto-oracle-1.0.4.war
```
Apri `http://localhost:8090`.

## Scheduler cron (6 campi Spring)
| Esempio | Significato |
|---|---|
| `0 0/30 * * * *` | Ogni 30 minuti |
| `0 15 2 * * *` | Ogni giorno alle 02:15 |
| `0 0 8 * * MON-FRI` | Giorni feriali alle 08:00 |

Le schedulazioni sono in memoria e vengono azzerate al riavvio.

## Note tecniche
- Log in memoria (buffer FIFO); con `app.logs.persist.enabled=true` vengono persistiti su Oracle e ricaricati all'avvio.
- Schedulazioni in memoria: azzerate al riavvio.
- Il WAR è deployabile su Tomcat esterno oppure avviabile come fat-JAR.
