# Copilot instructions for Cruscotto-Oracle

## Project root and environment
- Canonical local path: `C:\Users\mamagnon\source\repos\Cruscotto-Oracle`.
- Before running commands, `cd` into that folder explicitly (sessions often start from `C:\Users\mamagnon`).
- Use Maven commands from project root (not from parent folders).

## Build, run, and test commands
- Run app in development: `mvn spring-boot:run`
- Full build package: `mvn clean package`
- Run all tests: `mvn test`
- Run a single test class: `mvn -Dtest=OracleSchemaServiceTests test`

## High-level architecture
- Spring Boot WAR app with Thymeleaf UI and Oracle JDBC.
- Main controller orchestration is in `DashboardController`:
  - page routes (`/dashboard`, `/editor`, `/logs`, `/errors`, `/xlsx-import`)
  - REST endpoints used by the editor and utility actions.
- SQL editor schema sidebar is split into two API layers:
  1. `/api/schema` returns schema group summaries (group + count) for initial render.
  2. `/api/schema/group` returns objects for one group on demand (lazy loading).
- Schema grouping logic is centralized in `OracleSchemaService` (canonical group ordering and mapping).
- Logs, output rendering (HTML/CSV/XLSX), scheduler, and XLSX import are handled by dedicated services wired by the controller.

## Project-specific conventions
- **Frontend schema UX contract (editor):**
  - All schema groups are visible and collapsed by default.
  - Group items are loaded only when the group is opened.
  - On open, show immediate placeholder `Reading...`, then replace with loaded items.
  - `schema-items` is the only scrollable sub-panel; keep min/max row constraints aligned with current CSS behavior.
- **Do not close tasks on backend-only assumptions** for editor/sidebar work: verify end-to-end behavior against the requested UI interaction model.
- **When changing schema sidebar behavior**, update both:
  - backend (`OracleSchemaService`, controller endpoints),
  - frontend (`templates/editor.html` CSS + JS interactions).
- Keep robot branding consistent across pages (header robot and favicon `/icon.svg` where applicable).

## Release/versioning conventions
- Version changes must be synchronized in all relevant places in the same change set:
  - `pom.xml` version
  - `DashboardController.APP_VERSION`
  - release notes/docs (`README.md`, `CHANGELOG.md` when present)

## Validation expectations for this repo
- For feature work, run at least `mvn test`.
- For release/build requests, run `mvn clean package`.
- Prefer reporting completion only after the requested runtime behavior is actually visible (especially for `/editor` schema interactions).
