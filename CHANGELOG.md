# Changelog

Tutte le modifiche rilevanti a questo progetto sono documentate in questo file.

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
