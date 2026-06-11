# Dokumentation

## Architektur

Die vollständige Spezifikation der Systemarchitektur, inklusive Datenflüssen und Authentifizierung:
[architecture/architecture.md](architecture/architecture.md)

## API

Dokumentation der REST-API-Endpoints (Producer- und Consumer-BFF) mit Beispielen:
[api/readme.md](api/readme.md)

Die dazugehörigen OpenAPI-Spezifikationen liegen unter [api/](api/).

## Mermaid-Diagramme rendern

Die Architekturdiagramme liegen als `.mmd`-Dateien unter `docs/architecture/diagrams/`.
Um daraus SVG-Dateien zu erzeugen, führe folgendes Kommando aus:

```bash
node scripts/render-mermaid.js docs
```

Voraussetzung: `@mermaid-js/mermaid-cli` (mmdc) muss installiert sein:

```bash
npm install -g @mermaid-js/mermaid-cli
```

Das Skript findet rekursiv alle `.mmd`-Dateien unter `docs/` und erzeugt für jede eine `.svg`-Datei im selben Ordner.

In der CI (GitHub Actions) übernimmt der Workflow `render-mermaid.yml` diesen Schritt automatisch bei jedem Push auf `main`.
