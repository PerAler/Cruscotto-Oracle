# Cruscotto Oracle — Spring Boot

Applicazione web Spring Boot 3 per la gestione, esecuzione e monitoraggio di script SQL/PL-SQL su database Oracle.

---

## Changelog

### v1.3.0 (2026-06-16)
- **[CHANGED] UX pannello connessioni nello schema sidebar**
  - Pannello connessioni collassato di default per liberare spazio al browser schema
  - Combo profili connessione sempre visibile con pulsante Apri/Chiudi
  - Auto-collasso del pannello dopo connessione Oracle riuscita
- **[CHANGED] Tabs connessioni spostate nell'area editor**
  - `connectionTabs` ora in `editor-main`, allineate ai comandi script
- **[CHANGED] Esecuzione query dal cursore**
  - L'editor esegue il blocco sotto il cursore
  - Ogni query termina con una riga contenente solo `/`
  - Parametri bind e bozza editor si allineano alla query corrente

### v1.2.0 (2026-06-15)
- **[NEW] Profili connessione persistenti lato server**
  - La combo connessioni recupera i profili anche dopo riavvio applicazione
  - Nessuna password salvata: solo etichetta, target, username, schema
- **[NEW] Libreria query per etichetta connessione**
  - Salvataggio/aggiornamento script in cartella dedicata `sql/<ETICHETTA>`
  - Caricamento script filtrato per etichetta attiva
- **[CHANGED] Cruscotto allineato alla connessione attiva**
  - Evidenza etichetta connessione in pagina
  - Combo script coerente con etichetta/contesto attivo

### v1.1.0 (2026-06-15)
- **[NEW] Multi-connessione Oracle nell'Editor SQL**
  - Apertura/chiusura di più connessioni runtime senza riavvio
  - Attivazione della connessione corrente tramite tab
  - Schema browser e `editor/execute` legati alla connessione attiva
- **[NEW] API connessioni** — `/api/connections`, `/api/connections/open`, `/api/connections/activate`, `/api/connections/close`
- **[CHANGED] Avvio applicazione** — bootstrap disconnesso (nessuna richiesta URL/user/password all'avvio)

---

## Funzionalità

### Utility (`/utility`)
- **Catalogo script SQL** — carica automaticamente tutti i file `.sql` presenti nella cartella configurata (`app.sql.root-dir`, default `sql`); filtro per nome in tempo reale.
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
- **KPI utility** — card cliccabili: Log totali, Esecuzioni OK, Errori attivi, Output generati
- **Schedulazioni cron** — pianificazione esecuzioni con espressione Spring cron a 6 campi; lancio singolo one-shot con data/ora locale

### Editor SQL dedicato (`/editor`)
- **Editor full-page** con numerazione righe sincronizzata
- **Formattatore PL/SQL** — identico alla utility (pulsante nell'action bar)
- **Robot animato** nell'header con stato dinamico (stesso robot della utility)
- Autocompletamento keyword SQL (Tab / frecce / Escape)
- Selezione script dal catalogo (caricamento AJAX, senza reload pagina)
- Ricarica catalogo da disco senza riavvio
- **Esegui** — AJAX con spinner, risultati visualizzati in iframe inline
- **Esegui** — AJAX con spinner, esegue la query sotto il cursore; i blocchi terminano con `/` su una riga singola
- **Vista risultati** con toggle densità (compatta / estesa) e toggle larghezza colonne
- **Salva nuovo** / **Aggiorna script selezionato** (POST form)
- Estrazione automatica parametri bind
- KPI log/errori in testa pagina
- Header aggiornato in tempo reale dopo apertura/attivazione connessione (stato DB e label attiva)

### Output (`/output/*`)
- Ogni esecuzione con risultati salva **un solo file CSV** nella cartella `output/`
- **HTML** — generato on-demand dal CSV al momento della visualizzazione; paginato (200 righe per pagina, max 1000)
- **XLSX** — generato on-demand dal CSV al momento del download; colonne auto-dimensionate, troncatura celle a 32.767 caratteri
- I link HTML/CSV/XLSX nella utility puntano tutti allo stesso bundle base (stesso timestamp); solo il CSV è persistito su disco
- Retention: massimo 100 bundle CSV (`app.output.max-items`); i file più vecchi vengono rimossi automaticamente ad ogni nuova esecuzione

### Log e monitoraggio
- **Log esecuzioni** (`/logs`) — tabella con filtro per stato (OK/KO), link ai file output (HTML/CSV/XLSX)
- **Log errori** (`/errors`) — vista dedicata ai soli KO
- **Import XLSX** (`/xlsx-import`) — upload file XLSX, analisi colonne, lettura struttura tabella Oracle, proposta DDL dinamica solo sulle colonne selezionate, import selettivo e svuotamento opzionale della tabella prima del caricamento
- **Persistenza log su Oracle** — tabella `CRUSCOTTO_EXEC_LOG`; ricaricamento all'avvio (`app.logs.persist.enabled=true`)
- Retention log configurabile (`app.logs.max-size`)

### API / Endpoint utili
| Endpoint | Metodo | Descrizione |
|---|---|---|
| `/dashboard` | GET | Redirect rapido all'editor |
| `/utility` | GET | Pagina utility (catalogo script, scheduler, import) |
| `/editor` | GET | Editor SQL dedicato |
| `/query/execute` | POST | Esegui script dalla utility |
| `/query/save` | POST | Salva nuovo script |
| `/query/update` | POST | Aggiorna script esistente |
| `/editor/execute` | POST | Esegui query (AJAX, ritorna JSON) |
| `/editor/save` | POST | Salva script dall'editor |
| `/editor/update` | POST | Aggiorna script dall'editor |
| `/editor/load-script` | GET | Carica script (AJAX, ritorna JSON) |
| `/api/connections` | GET | Elenco connessioni Oracle aperte |
| `/api/connections/open` | POST | Apre una nuova connessione Oracle |
| `/api/connections/activate` | POST | Imposta la connessione attiva |
| `/api/connections/close` | POST | Chiude una connessione Oracle |
| `/catalog/reload` | POST | Ricarica catalogo da disco |
| `/xlsx-import` | GET | Pagina importazione XLSX |
| `/xlsx-import/analyze` | POST | Analizza file XLSX caricato |
| `/xlsx-import/read-structure` | POST | Legge la struttura di una tabella Oracle |
| `/xlsx-import/create-table` | POST | Crea la tabella Oracle proposta |
| `/xlsx-import/import` | POST | Importa le colonne selezionate nel DB |
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
spring.datasource.url=jdbc:h2:mem:cruscotto;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.username=sa
spring.datasource.password=

app.output.folder=output
app.output.max-items=100
app.logs.max-size=500
app.logs.persist.enabled=false
spring.servlet.multipart.max-file-size=25MB
spring.servlet.multipart.max-request-size=25MB
```

L'app parte senza connessioni Oracle preconfigurate. Le connessioni si aprono dalla pagina `/editor` nel pannello "Connessioni e schema".

Per il DBMS spool, se vuoi usarlo su Oracle 11g crea anche la tabella temporanea:

```sql
CREATE GLOBAL TEMPORARY TABLE app_dbms_output_lines (
  line_num NUMBER PRIMARY KEY,
  line_text CLOB
) ON COMMIT DELETE ROWS;
```

## Script SQL
Inserisci i file `.sql` nella cartella configurata con `app.sql.root-dir` (default `sql`).  
Per i parametri bind usa placeholder `:nomeparametro`, ad esempio:
```sql
SELECT * FROM fatture WHERE anno = :anno AND stato = :stato
```

## Avvio
```bash
cd C:\Temp\Cruscotto_Oracle
mvn clean package -DskipTests
java -jar target\cruscotto-oracle-1.2.0.war
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
- Log in memoria (buffer FIFO); la persistenza Oracle è disabilitata di default (`app.logs.persist.enabled=false`).
- Schedulazioni in memoria: azzerate al riavvio.
- Il WAR è deployabile su Tomcat esterno oppure avviabile come fat-JAR.
