#!/usr/bin/env python3
"""
Compile the Playwright screenshots into a landscape PDF presentation.
"""
from pathlib import Path
from reportlab.lib.pagesizes import landscape, A4
from reportlab.lib.units import mm
from reportlab.lib.colors import HexColor, white
from reportlab.pdfgen import canvas
from reportlab.lib.utils import ImageReader

HERE = Path(__file__).parent
# Output is a repo deliverable, not a build artefact. Write it (and read the
# screenshots) under docs/demo/ so this script and the README agree on one
# canonical location. HERE.parents[2] = repo root (services/pbll/playwright/..).
DEMO_DIR = HERE.parents[2] / "docs" / "demo"
SHOTS = DEMO_DIR / "shots"
OUT = DEMO_DIR / "ch-vacd-pattern-a-tracer-bullet.pdf"

PAGE_W, PAGE_H = landscape(A4)            # 842 x 595 pt
MARGIN_X = 18 * mm
MARGIN_Y = 14 * mm
BG = HexColor("#0f1218")
PANEL = HexColor("#161a22")
TEXT = HexColor("#e8ecf3")
MUTED = HexColor("#8b94a8")
ACCENT = HexColor("#4f8cff")
ACCENT2 = HexColor("#3fb98f")


def fill_bg(c):
    c.setFillColor(BG)
    c.rect(0, 0, PAGE_W, PAGE_H, stroke=0, fill=1)


def header(c, title, subtitle=None):
    c.setFillColor(ACCENT2)
    c.setFont("Helvetica-Bold", 9)
    c.drawString(MARGIN_X, PAGE_H - MARGIN_Y + 6, "●  CH VACD")
    c.setFillColor(MUTED)
    c.setFont("Helvetica", 8)
    c.drawRightString(PAGE_W - MARGIN_X, PAGE_H - MARGIN_Y + 6,
                      "Pattern A — Platform Business Logic Layer (tracer bullet)")
    c.setFillColor(TEXT)
    c.setFont("Helvetica-Bold", 22)
    c.drawString(MARGIN_X, PAGE_H - MARGIN_Y - 18, title)
    if subtitle:
        c.setFillColor(MUTED)
        c.setFont("Helvetica", 11)
        c.drawString(MARGIN_X, PAGE_H - MARGIN_Y - 36, subtitle)


def footer(c, page_no, total):
    from datetime import date
    c.setFillColor(MUTED)
    c.setFont("Helvetica", 7.5)
    c.drawString(MARGIN_X, MARGIN_Y - 10,
                 f"Built {date.today().isoformat()} — Swiss GovTech Hackathon 2026 / DigiSanté")
    c.drawRightString(PAGE_W - MARGIN_X, MARGIN_Y - 10, f"{page_no} / {total}")


def fit_image(c, img_path, box):
    """Draw image at img_path inside box (x, y, w, h) preserving aspect."""
    x, y, w, h = box
    img = ImageReader(img_path)
    iw, ih = img.getSize()
    scale = min(w / iw, h / ih)
    dw, dh = iw * scale, ih * scale
    dx = x + (w - dw) / 2
    dy = y + (h - dh) / 2
    c.drawImage(img, dx, dy, dw, dh, mask='auto', preserveAspectRatio=False)


def divider(c, y):
    c.setStrokeColor(HexColor("#2a3142"))
    c.setLineWidth(0.6)
    c.line(MARGIN_X, y, PAGE_W - MARGIN_X, y)


def slide_text(c, lines, x, y, font="Helvetica", size=11, color=TEXT, leading=None):
    if leading is None:
        leading = size * 1.45
    c.setFillColor(color)
    c.setFont(font, size)
    for line in lines:
        c.drawString(x, y, line)
        y -= leading
    return y


def slide_title(c, title, subtitle=None):
    fill_bg(c)
    header(c, title, subtitle)


def add_caption(c, text):
    c.setFillColor(MUTED)
    c.setFont("Helvetica", 9)
    c.drawString(MARGIN_X, MARGIN_Y + 6, text)


PAGES = []  # list of (renderer_fn, page_meta)


def cover_page(c):
    fill_bg(c)
    # Big title
    c.setFillColor(ACCENT2)
    c.setFont("Helvetica-Bold", 12)
    c.drawString(MARGIN_X, PAGE_H - MARGIN_Y - 10, "●  CH VACD — Swiss Vaccination Showcase")
    c.setFillColor(TEXT)
    c.setFont("Helvetica-Bold", 36)
    c.drawString(MARGIN_X, PAGE_H - MARGIN_Y - 60, "Pattern A")
    c.setFillColor(TEXT)
    c.setFont("Helvetica-Bold", 28)
    c.drawString(MARGIN_X, PAGE_H - MARGIN_Y - 92, "Immunization Write Path — Tracer Bullet")
    c.setFillColor(MUTED)
    c.setFont("Helvetica", 13)
    c.drawString(MARGIN_X, PAGE_H - MARGIN_Y - 118,
                 "End-to-end FHIR (CH VACD Immunization) → openEHR Composition → EHRbase, with feeder_audit retention.")
    # Body
    y = PAGE_H - MARGIN_Y - 158
    bullets = [
        "• PBLL (Kotlin + Ktor) — single brain orchestrating the stack",
        "• fhir-server-1 (HAPI 8.8.1) — demographics + directory store (Patient / Practitioner / Organization)",
        "• openFHIR 2.2.1 — stateless FHIR ↔ openEHR mapping engine (FHIRconnect YAMLs)",
        "• EHRbase 2.31.0 — openEHR Composition store + AQL",
        "",
        "Scope: 7 mapped Immunization fields (Tier 1 tracer bullet)",
        "Out of scope: read path, profile validation, terminology server, SMART/EPR auth.",
    ]
    slide_text(c, bullets, MARGIN_X, y, font="Helvetica", size=12, color=TEXT, leading=18)
    # Body footer (sits above the standard footer line)
    c.setFillColor(MUTED)
    c.setFont("Helvetica-Oblique", 10)
    c.drawString(MARGIN_X, MARGIN_Y + 32,
                 "Built as a Pragmatic Programmer 'tracer bullet': minimal slice, real infrastructure end-to-end.")
    c.drawString(MARGIN_X, MARGIN_Y + 16,
                 "Hackathon harness — see services/pbll/ and docs/architecture/patterns.md")


def stack_page(c):
    slide_title(c, "Stack at a Glance",
                "Five services on a shared vacd-net bridge; PBLL is the only participant-built component.")
    y_top = PAGE_H - MARGIN_Y - 60
    rows = [
        ("Service", "Role", "URL inside vacd-net", "Auth"),
        ("PBLL", "Platform brain (Kotlin/Ktor)", "http://pbll:8080", "—"),
        ("fhir-server-1", "HAPI 8.8.1 — CH VACD ref. server, demographics + directory",
         "http://fhir-server-1:9111/ch-vacd-api-reference-server/fhir", "—"),
        ("openfhir", "openFHIR 2.2.1 — FHIR ↔ openEHR mapping (FHIRconnect)",
         "http://openfhir:8083", "—"),
        ("openfhir-mongo", "Mongo 7.0 — openFHIR config store (templates, contexts, models)",
         "(internal)", "admin/admin (internal)"),
        ("ehrbase", "EHRbase 2.31.0 — openEHR Composition store, AQL",
         "http://ehrbase:8080/ehrbase/rest/openehr/v1", "ehrbase-user / SuperSecret…"),
        ("ehrbase-db", "Postgres 16 — EHRbase backing store", "(internal)", "internal"),
    ]
    col_x = [MARGIN_X, MARGIN_X + 100, MARGIN_X + 360, MARGIN_X + 690]
    col_w = [98, 250, 320, 110]
    line_h = 30
    y = y_top
    c.setFont("Helvetica-Bold", 10)
    c.setFillColor(MUTED)
    for x, t in zip(col_x, rows[0]):
        c.drawString(x, y, t)
    y -= 16
    divider(c, y + 8)
    for r in rows[1:]:
        c.setFont("Helvetica-Bold", 10.5)
        c.setFillColor(TEXT)
        c.drawString(col_x[0], y, r[0])
        c.setFont("Helvetica", 9.5)
        c.setFillColor(TEXT)
        for line, x, w in zip(r[1:], col_x[1:], col_w[1:]):
            wrap_text(c, line, x, y, w)
        y -= line_h
    add_caption(c, "Reference: docs/architecture/patterns.md  ·  docker-compose.yml")


def wrap_text(c, text, x, y, max_w, leading=11.5):
    """Very small wrap helper."""
    words = text.split()
    line = ""
    cur_y = y
    for w in words:
        trial = (line + " " + w).strip()
        if c.stringWidth(trial, "Helvetica", 9.5) <= max_w:
            line = trial
        else:
            c.drawString(x, cur_y, line)
            cur_y -= leading
            line = w
    if line:
        c.drawString(x, cur_y, line)


def flow_page(c):
    slide_title(c, "The Write Flow",
                "POST /Immunization → fan out → ensure EHR → openFHIR convert → enrich → store")
    steps = [
        ("1.", "Client", "POSTs a CH VACD Immunization (or document Bundle)", "to PBLL /Immunization"),
        ("2.", "Demographics fan-out", "Patient + Practitioner + Organization → fhir-server-1 (HAPI)",
         "PBLL captures the HAPI-assigned ids"),
        ("3.", "EHR resolution", "Find-or-create openEHR EHR in EHRbase",
         "Linked via EHR_STATUS.subject.external_ref (namespace=ch-vacd, id=<Patient.id>)"),
        ("4.", "Conversion", "openFHIR /openfhir/toopenehr?templateId=…&flat=true",
         "Returns FLAT openEHR Composition shaped by ch-vacd-immunization administration.v1-alpha"),
        ("5.", "Feeder audit", "PBLL injects /_feeder_audit/original_content = verbatim FHIR JSON",
         "Per Konkretisierung §13 — legal provenance"),
        ("6.", "Persist", "POST /ehr/{ehrId}/composition?format=FLAT&templateId=…",
         "Returns 201 + compositionUid; PBLL relays as 201 Created"),
    ]
    y = PAGE_H - MARGIN_Y - 76
    for n, who, what, detail in steps:
        c.setFillColor(ACCENT)
        c.setFont("Helvetica-Bold", 16)
        c.drawString(MARGIN_X, y, n)
        c.setFillColor(TEXT)
        c.setFont("Helvetica-Bold", 12)
        c.drawString(MARGIN_X + 30, y, who)
        c.setFillColor(TEXT)
        c.setFont("Helvetica", 11)
        c.drawString(MARGIN_X + 30, y - 14, what)
        c.setFillColor(MUTED)
        c.setFont("Helvetica-Oblique", 9.5)
        c.drawString(MARGIN_X + 30, y - 26, detail)
        y -= 50
    add_caption(c, "Pseudocode: services/pbll/src/main/kotlin/ch/vacd/pbll/ingestion/ImmunizationIngest.kt")


def fields_page(c):
    slide_title(c, "Seven Mapped Fields (Tier 1)",
                "Deliberately the alpha-safe subset — avoids every known alpha-quality issue in the template.")
    rows = [
        ("#", "FHIR path", "openEHR path", "Type"),
        ("1", "Immunization.status = 'completed'",
         "ism_transition/{careflow_step=at0006, current_state=245}", "manual"),
        ("2", "Immunization.vaccineCode",
         "description[at0017]/items[at0020]", "CODEABLE_CONCEPT"),
        ("3", "Immunization.occurrenceDateTime",
         "time", "DATETIME"),
        ("4", "Immunization.lotNumber",
         "description[at0017]/items[CLUSTER.medication.v2]/items[at0150]", "STRING"),
        ("5", "Immunization.site",
         "description[at0017]/items[at0140]/items[at0141]", "CODEABLE_CONCEPT"),
        ("6", "Immunization.route",
         "description[at0017]/items[at0140]/items[at0147]", "CODEABLE_CONCEPT"),
        ("7", "Immunization.protocolApplied[0].doseNumberPositiveInt",
         "description[at0017]/items[at0025]", "INTEGER"),
    ]
    y = PAGE_H - MARGIN_Y - 70
    # Wider FHIR column to accommodate the long protocolApplied path on row 7.
    col_x = [MARGIN_X, MARGIN_X + 22, MARGIN_X + 310, MARGIN_X + 680]
    c.setFillColor(MUTED)
    c.setFont("Helvetica-Bold", 10)
    for x, h in zip(col_x, rows[0]):
        c.drawString(x, y, h)
    y -= 14
    divider(c, y + 8)
    y -= 6
    for r in rows[1:]:
        c.setFillColor(TEXT)
        c.setFont("Helvetica", 10)
        c.drawString(col_x[0], y, r[0])
        # Shrink font if path is long so it doesn't overflow the next column.
        fhir_font = 8.5 if len(r[1]) > 38 else 9
        c.setFont("Courier", fhir_font)
        c.drawString(col_x[1], y, r[1])
        oe_font = 8.5 if len(r[2]) > 46 else 9
        c.setFont("Courier", oe_font)
        c.setFillColor(HexColor("#cdd6e4"))
        c.drawString(col_x[2], y, r[2])
        c.setFont("Helvetica-Bold", 9)
        c.setFillColor(ACCENT2)
        c.drawString(col_x[3], y, r[3])
        y -= 28
    add_caption(c, "model: services/pbll/src/main/resources/bootstrap/ACTION.medication.v1.yml")


def screenshot_page(title, subtitle, image_path, caption):
    def renderer(c):
        slide_title(c, title, subtitle)
        # image box
        img_top_y = PAGE_H - MARGIN_Y - 62
        img_bottom_y = MARGIN_Y + 22
        img_box = (MARGIN_X, img_bottom_y, PAGE_W - 2 * MARGIN_X, img_top_y - img_bottom_y)
        # subtle background panel
        c.setFillColor(PANEL)
        c.setStrokeColor(HexColor("#2a3142"))
        c.setLineWidth(0.7)
        c.roundRect(img_box[0] - 4, img_box[1] - 4, img_box[2] + 8, img_box[3] + 8,
                    6, stroke=1, fill=1)
        fit_image(c, image_path, img_box)
        add_caption(c, caption)
    return renderer


def summary_page(c):
    slide_title(c, "What's Proven and What's Next",
                "Acceptance criteria validated end-to-end against the live stack.")
    y = PAGE_H - MARGIN_Y - 70
    c.setFillColor(ACCENT2)
    c.setFont("Helvetica-Bold", 12)
    c.drawString(MARGIN_X, y, "Proven (all green)")
    y -= 18
    proven = [
        "✓ Stack comes up end-to-end on docker compose; PBLL fat JAR < 20 MB",
        "✓ Bootstrap is idempotent (OPT + context.yml + model.yml on every restart, no duplicates)",
        "✓ POST /Immunization returns 201 + compositionUid (warm path < 200 ms)",
        "✓ feeder_audit.original_content contains the verbatim FHIR JSON (Konkretisierung §13)",
        "✓ EHRbase composition queryable via AQL by ehr_id",
        "✓ Patient + Practitioner + Organization landed in HAPI from the document Bundle",
        "✓ Strict mode: bare Immunization (non-document Bundle) rejected with 400 + OperationOutcome",
        "✓ Demo UI drives all three canonical CH VACD document Bundles in Chromium",
        "✓ Unit + integration tests pass against the live stack",
    ]
    c.setFillColor(TEXT)
    c.setFont("Helvetica", 10.5)
    for line in proven:
        c.drawString(MARGIN_X + 8, y, line)
        y -= 16

    y -= 16
    c.setFillColor(HexColor("#ffa657"))
    c.setFont("Helvetica-Bold", 12)
    c.drawString(MARGIN_X, y, "Deliberately out of scope (left to participating teams)")
    y -= 18
    next_up = [
        "Read path — GET /Immunization/{uid}, search by patient, Bundle re-hydration from fhir-server-1",
        "Profile validation — HAPI FhirValidator + CH VACD StructureDefinitions package",
        "Terminology lookups — live tx.fhir.ch/r4 calls for code system / value set checks",
        "Pattern B — inbound/outbound connectors and the sync-state table that goes with them",
    ]
    c.setFillColor(TEXT)
    c.setFont("Helvetica", 10.5)
    for line in next_up:
        c.drawString(MARGIN_X + 8, y, line)
        y -= 16
    add_caption(c, "Reference: services/pbll/  ·  docs/architecture/patterns.md  ·  progress/main-progress.txt")


# Build the deck
PAGES = [
    cover_page,
    stack_page,
    flow_page,
    fields_page,
    screenshot_page(
        "Demo UI — initial state",
        "/demo loads with the Boostrix example pre-populated.",
        SHOTS / "00-initial.png",
        "screenshot: 00-initial.png",
    ),
]

EXAMPLE_PAGES = [
    ("01-immunization-administration-boostrix",
     "Boostrix (dTpa-IPV booster) — CH VACD Bundle/document",
     "The verbatim IG seed (fhir.ch/ig/ch-vacd/Bundle-1-1-ImmunizationAdministration); deltoid IM."),
    ("02-immunization-administration-comirnaty",
     "Comirnaty (COVID-19 mRNA) — CH VACD Bundle/document",
     "Derived variant: Swissmedic 68225 + SNOMED 840539006 (COVID-19)."),
    ("03-immunization-administration-priorix",
     "Priorix (MMR) — CH VACD Bundle/document",
     "Derived variant: Swissmedic 615 + SNOMED 14189004/36989005/36653000."),
]

for n, (slug, title, subtitle) in enumerate(EXAMPLE_PAGES, start=1):
    done_n = n * 2  # numbering: 02-done, 04-done, 06-done, …
    img = SHOTS / f"{done_n:02d}-{slug}-done.png"
    if img.exists():
        PAGES.append(screenshot_page(title, subtitle, img, f"screenshot: {img.name}"))

PAGES.append(summary_page)

c = canvas.Canvas(str(OUT), pagesize=landscape(A4))
total = len(PAGES)
for idx, renderer in enumerate(PAGES, start=1):
    renderer(c)
    footer(c, idx, total)
    c.showPage()
c.save()
print(f"wrote {OUT} ({total} pages)")
