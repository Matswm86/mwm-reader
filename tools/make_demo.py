"""Write the demo files the emulator smoke test opens.

They exist so CI reads a real Pine script, a real markdown note, a real CSV and
a real PDF rather than a mock. The PDF is assembled here, byte by byte with
correct cross-reference offsets, so the test needs no PDF library.

Usage: make_demo.py <output-directory>
"""

import io
import struct
import sys
import zipfile
import zlib
from pathlib import Path

PINE = """//@version=6
// MWM Sweep Demo - a liquidity sweep marker, written for the reader's screenshot.
indicator("MWM Sweep Demo", overlay = true, max_labels_count = 200)

lookback   = input.int(20, "Swing lookback", minval = 2, maxval = 200)
bodyFactor = input.float(1.5, "Displacement body factor", step = 0.1)
showLabels = input.bool(true, "Label the sweeps")

swingHigh = ta.highest(high, lookback)[1]
swingLow  = ta.lowest(low, lookback)[1]
body      = math.abs(close - open)
avgBody   = ta.sma(body, 14)

sweepUp   = high > swingHigh and close < swingHigh and body > avgBody * bodyFactor
sweepDown = low  < swingLow  and close > swingLow  and body > avgBody * bodyFactor

if sweepUp and showLabels
    label.new(bar_index, high, "sell-side taken", color = color.new(color.red, 20),
              style = label.style_label_down, textcolor = color.white)

if sweepDown and showLabels
    label.new(bar_index, low, "buy-side taken", color = color.new(color.teal, 20),
              style = label.style_label_up, textcolor = color.white)

plotshape(sweepUp,   title = "Sweep up",   style = shape.triangledown, location = location.abovebar)
plotshape(sweepDown, title = "Sweep down", style = shape.triangleup,   location = location.belowbar)

/* A block comment, so the reader has one to colour across lines.
   The numbers below are there for the number colour. */
atr = ta.atr(14)
plot(atr * 0.0, display = display.none, title = "keep atr in scope")
"""

MARKDOWN = """---
title: MWM Reader demo note
tags: [reader, demo]
---

# MWM Reader demo note

A markdown file opened by the emulator during the build, so the screenshot in
the README is the real app rather than a mock-up.

## What it shows

- **Bold** text, *thin* text, `inline code` and ~~struck out~~ words
- [ ] an unticked task
- [x] a ticked one
- A [link](https://example.invalid) that is underlined, never followed

> A block quote, set in the accent colour with a rule down its left side.

## A table

| Instrument | Session | Contracts |
|------------|---------|-----------|
| MNQ        | London  | 2         |
| MGC        | New York| 1         |

## A code fence

```python
def sweep(bars, lookback=20):
    \"\"\"Return the bars that took out the prior swing.\"\"\"
    highs = [b.high for b in bars[-lookback:]]
    return [b for b in bars if b.high > max(highs)]
```

---

That rule above is a horizontal rule, not a heading underline.
"""

NOTES = """MWM Reader

A plain text file. No extension magic, no markup, just words wrapped by the
reader at whatever width the phone happens to be.

The reader keeps the place you stopped on every file you open, so coming back
to a long text lands where you left it rather than at the top.
"""

CSV = """date,session,instrument,side,contracts,r_multiple,note
2026-09-01,London,MNQ,long,2,1.8,swept Asia low then reclaimed
2026-09-02,New York,MNQ,short,2,-1.0,stopped at the open drive
2026-09-03,London,MGC,long,1,2.4,"held the level, no retest"
2026-09-04,New York,MNQ,long,2,0.0,scratched before the number
2026-09-05,London,MNQ,short,2,1.2,equal highs taken
"""


def build_zip(entries: dict[str, str | bytes]) -> bytes:
    """A deflate zip in memory, which is what EPUB, docx and xlsx all are."""
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, body in entries.items():
            archive.writestr(name, body)
    return buffer.getvalue()


def build_epub() -> bytes:
    """Two spine documents, an NCX for the chapter names, and the metadata."""
    chapter = (
        '<?xml version="1.0" encoding="utf-8"?>'
        '<html xmlns="http://www.w3.org/1999/xhtml"><head><title>{title}</title></head>'
        "<body><h1>{title}</h1><p>{body}</p></body></html>"
    )
    return build_zip(
        {
            "mimetype": "application/epub+zip",
            "META-INF/container.xml": (
                '<?xml version="1.0"?><container version="1.0" '
                'xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles>'
                '<rootfile full-path="OEBPS/book.opf" '
                'media-type="application/oebps-package+xml"/></rootfiles></container>'
            ),
            "OEBPS/book.opf": (
                '<?xml version="1.0"?><package version="2.0" '
                'xmlns="http://www.idpf.org/2007/opf" unique-identifier="id">'
                '<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">'
                "<dc:title>The Sweep Demo</dc:title>"
                "<dc:creator>MWM AI</dc:creator></metadata><manifest>"
                '<item id="c1" href="ch1.xhtml" media-type="application/xhtml+xml"/>'
                '<item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>'
                '<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>'
                '</manifest><spine toc="ncx"><itemref idref="c1"/>'
                '<itemref idref="c2"/></spine></package>'
            ),
            "OEBPS/toc.ncx": (
                '<?xml version="1.0"?><ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" '
                'version="2005-1"><navMap>'
                '<navPoint id="n1" playOrder="1"><navLabel><text>At the open</text></navLabel>'
                '<content src="ch1.xhtml"/></navPoint>'
                '<navPoint id="n2" playOrder="2"><navLabel><text>The reclaim</text></navLabel>'
                '<content src="text/ch2.xhtml"/></navPoint></navMap></ncx>'
            ),
            "OEBPS/ch1.xhtml": chapter.format(
                title="At the open",
                body="Price ran the Asia high in the first ten minutes and left a wick behind it.",
            ),
            "OEBPS/text/ch2.xhtml": chapter.format(
                title="The reclaim",
                body="The close back inside the range was the signal, not the wick itself.",
            ),
        }
    )


def build_docx() -> bytes:
    """A heading, two paragraphs, a numbered item and a two-column table."""
    document = (
        '<?xml version="1.0" encoding="UTF-8"?>'
        '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
        "<w:body>"
        '<w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr>'
        "<w:r><w:t>Desk notes for the week</w:t></w:r></w:p>"
        "<w:p><w:r><w:t>Two sessions traded, </w:t></w:r>"
        "<w:r><w:t>one of them skipped on news.</w:t></w:r></w:p>"
        '<w:p><w:pPr><w:numPr><w:ilvl w:val="0"/></w:numPr></w:pPr>'
        "<w:r><w:t>Stop always in before the fill confirms</w:t></w:r></w:p>"
        "<w:tbl>"
        "<w:tr><w:tc><w:p><w:r><w:t>Session</w:t></w:r></w:p></w:tc>"
        "<w:tc><w:p><w:r><w:t>Result</w:t></w:r></w:p></w:tc></w:tr>"
        "<w:tr><w:tc><w:p><w:r><w:t>London</w:t></w:r></w:p></w:tc>"
        "<w:tc><w:p><w:r><w:t>plus 1.8R</w:t></w:r></w:p></w:tc></w:tr>"
        "</w:tbl></w:body></w:document>"
    )
    return build_zip(
        {
            "[Content_Types].xml": (
                '<?xml version="1.0"?><Types '
                'xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
                '<Default Extension="xml" ContentType="application/xml"/></Types>'
            ),
            "word/document.xml": document,
        }
    )


def build_xlsx() -> bytes:
    """One named sheet whose text cells come from the shared-string table."""
    return build_zip(
        {
            "xl/workbook.xml": (
                '<?xml version="1.0"?><workbook><sheets>'
                '<sheet name="Sessions" sheetId="1" r:id="rId1"/></sheets></workbook>'
            ),
            "xl/sharedStrings.xml": (
                '<?xml version="1.0"?><sst count="4" uniqueCount="4">'
                "<si><t>session</t></si><si><t>r_multiple</t></si>"
                "<si><t>London</t></si><si><t>New York</t></si></sst>"
            ),
            "xl/worksheets/sheet1.xml": (
                '<?xml version="1.0"?><worksheet><sheetData>'
                '<row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>'
                '<row r="2"><c r="A2" t="s"><v>2</v></c><c r="B2"><v>1.8</v></c></row>'
                '<row r="3"><c r="A3" t="s"><v>3</v></c><c r="B3"><v>-1</v></c></row>'
                "</sheetData></worksheet>"
            ),
        }
    )


def build_png(width: int = 480, height: int = 240) -> bytes:
    """Three horizontal bands, so a failed decode is obvious in the screenshot."""

    def chunk(kind: bytes, payload: bytes) -> bytes:
        return (
            struct.pack(">I", len(payload))
            + kind
            + payload
            + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)
        )

    bands = [(0x2F, 0x6F, 0x62), (0xF4, 0xEC, 0xD8), (0xC9, 0x8A, 0x3E)]
    raw = bytearray()
    for y in range(height):
        raw.append(0)  # no per-row filter
        red, green, blue = bands[min(len(bands) - 1, y * len(bands) // height)]
        raw += bytes((red, green, blue)) * width

    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(bytes(raw), 6))
        + chunk(b"IEND", b"")
    )


def build_pdf(title: str, lines: list[str]) -> bytes:
    """A one-page PDF with correct object offsets and cross-reference table."""
    text = [b"BT", b"/F1 22 Tf 64 720 Td (" + title.encode("latin-1") + b") Tj", b"ET"]
    y = 680
    for line in lines:
        text.append(
            b"BT /F1 13 Tf 64 " + str(y).encode() + b" Td (" + line.encode("latin-1") + b") Tj ET"
        )
        y -= 22
    stream = b"\n".join(text)

    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
        b"/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        b"<< /Length " + str(len(stream)).encode() + b" >>\nstream\n" + stream + b"\nendstream",
    ]

    out = bytearray(b"%PDF-1.4\n")
    offsets = []
    for number, body in enumerate(objects, start=1):
        offsets.append(len(out))
        out += str(number).encode() + b" 0 obj\n" + body + b"\nendobj\n"

    xref_at = len(out)
    out += b"xref\n0 " + str(len(objects) + 1).encode() + b"\n"
    out += b"0000000000 65535 f \n"
    for offset in offsets:
        out += f"{offset:010d} 00000 n \n".encode()
    out += b"trailer\n<< /Size " + str(len(objects) + 1).encode() + b" /Root 1 0 R >>\n"
    out += b"startxref\n" + str(xref_at).encode() + b"\n%%EOF\n"
    return bytes(out)


def main() -> int:
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    (out / "MWM_Sweep_Demo.pine").write_text(PINE, encoding="utf-8")
    (out / "reader-demo.md").write_text(MARKDOWN, encoding="utf-8")
    (out / "notes.txt").write_text(NOTES, encoding="utf-8")
    (out / "trades.csv").write_text(CSV, encoding="utf-8")
    (out / "sweep-demo.epub").write_bytes(build_epub())
    (out / "desk-notes.docx").write_bytes(build_docx())
    (out / "sessions.xlsx").write_bytes(build_xlsx())
    (out / "bands.png").write_bytes(build_png())
    (out / "reader-demo.pdf").write_bytes(
        build_pdf(
            "MWM Reader",
            [
                "This page was generated by the build.",
                "Android draws it with its own PDF engine,",
                "so the app ships no PDF library at all.",
            ],
        ),
    )
    for path in sorted(out.iterdir()):
        print(f"{path.name}: {path.stat().st_size} bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
