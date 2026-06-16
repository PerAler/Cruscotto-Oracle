# Changelog

Tutte le modifiche rilevanti a questo progetto sono documentate in questo file.

## [1.2.0] - 2026-06-15
### Added
- Persistenza profili connessione Oracle su file locale (`config/cruscotto-oracle-connections.json`) senza password.
- API connessioni estesa con `savedProfiles` per popolare la combo anche dopo riavvio.
- Catalogo SQL per etichetta connessione con cartelle dedicate sotto la root configurabile (`app.sql.root-dir/<ETICHETTA>`).

### Changed
- Editor: caricamento/salvataggio/aggiornamento script allineati alla connessione attiva.
- Dashboard: visualizzazione etichetta connessione attiva e selezione script filtrata per etichetta.
- Navigazione aggiornata: `/dashboard` come ingresso editor e nuova pagina utility su `/utility`.
- Editor allineato alla utility con KPI log/errori in testa e header dinamico aggiornato dopo apertura connessione.

## [1.3.0] - 2026-06-16
### Changed
- Editor SQL: esecuzione del blocco sotto il cursore invece del selettore query.
- Terminatore query standardizzato su una riga contenente solo `/`.
- Bozza editor allineata al cursore corrente.

## [1.1.0] - 2026-06-15
### Added
- Gestione multi-connessione Oracle nell'Editor SQL (apertura, attivazione e chiusura connessioni runtime).
- API dedicate: `/api/connections`, `/api/connections/open`, `/api/connections/activate`, `/api/connections/close`.
- Supporto esecuzione query e caricamento schema per connessione selezionata (`connectionId`).

### Changed
- Avvio applicazione in stato disconnesso (nessun prompt JDBC in console).
- Sidebar editor aggiornata con gestione connessioni e schema per sessione.
- `OracleSchemaService` esteso per lavorare su `USER_*` oppure `ALL_*` in base allo schema configurato.

## [1.0.5] - 2026-05-22
## [1.0.6] - 2026-05-22
### Added
- Sidebar schema lazy-load: tutti i gruppi Oracle visibili all'avvio in stato collassato, con caricamento elenco solo su apertura gruppo.
- Endpoint REST `/api/schema/group` per caricare on-demand gli oggetti di un gruppo specifico.
- Supporto esteso ai gruppi Oracle oltre TABLES/VIEWS (procedure, function, package, trigger, sequence, synonym, type, materialized view, altri oggetti).

### Changed
- Layout sidebar schema: gruppi non-flex, `schema-items` come sotto-pannello scrollabile verticale.
- Regole altezza `schema-items`: minimo 0, massimo 20 righe.
- Feedback caricamento gruppo: apertura immediata con riga singola `Reading...` durante il fetch.

## [1.0.5] - 2026-05-22
### Added
- **Schema Browser Sidebar nell'Editor SQL** — Pannello laterale collassabile che visualizza tabelle e viste dello schema Oracle corrente
- **Filtro di ricerca real-time** — Ricerca case-insensitive con contatore dinamico aggiornato in base ai risultati
- **Scrollbar interna per gruppo** — Ogni gruppo (TABLES/VIEWS) ha scroll indipendente con max-height 200px per visualizzare tutti gli oggetti
- **Inserimento nome tabella/vista** — Click su un oggetto inserisce il nome nel punto di cursore dell'editor SQL
- **Endpoint REST `/api/schema`** — Recupera tabelle da `user_tables` e viste da `user_views` con grouping automatico
- **OracleSchemaService** — Nuovo servizio per interrogazione dello schema Oracle con metodi di grouping per tipo

### Changed
- Editor SQL: layout aggiornato a flex con sidebar (responsive, collassa su mobile)
- Scrollbar custom e styling improved per migliore UX

## [1.0.4] - 2026-05-18
### Fixed
- Credenziali Oracle spostate su configurazione parametrica e prompt interattivo all'avvio.
- Test resi indipendenti da Oracle reale.
- Cancellazione errori allineata tra memoria e persistenza Oracle.
- Cattura DBMS_OUTPUT corretta sulla stessa connessione JDBC della procedura.

### Changed
- Cartella SQL resa configurabile e caricamento esteso alle sottocartelle.
- Apertura automatica del browser dopo il bootstrap della web app.

## [1.0.2] - 2026-05-07
### Added
- Dialog di scelta per i file XLSX: opzione di apertura nel browser o download diretto.
- Event listener JavaScript sui link XLSX in logs.html con modal `confirm()`.

### Changed
- Link XLSX in logs.html aggiornati con attributi `data-xlsx-file` e `data-xlsx-view`.

## [1.0.1] - 2026-05-07
### Added
- Link cliccabile a output HTML nel mini editor SQL rapido della dashboard dopo esecuzione query.
- Recupero automatico del nome file HTML generato dall'ultima entry di log per visualizzazione link.

### Changed
- Metodo `runEditorQuery` del DashboardController esteso per tracciare e passare il nome file output.
- Versione incrementata da 1.0.0 a 1.0.1.

## [1.0.0] - 2026-05-05
### Added
- Export risultati in formato CSV e XLSX oltre all'HTML.
- Endpoint download output con link dedicati in dashboard.
- Modifica script SQL salvati direttamente dalla UI.
- KPI dashboard cliccabili e pagine dedicate per log/errori.
- Shortcut editor SQL: Tab, Shift+Tab, Invio con auto-indent, Ctrl+/ per commento riga.

### Changed
- Editor SQL reso stabile con textarea singola (rimozione overlay), eliminando il disallineamento durante lo scroll su query lunghe.
- Versione Maven fissata da 0.0.1-SNAPSHOT a 1.0.0.

### Fixed
- Risolto il drift di allineamento nell'editing multilinea con scroll.
- Migliorata leggibilita editor (caret, focus, selezione).
