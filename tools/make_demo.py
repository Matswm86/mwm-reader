"""Write the demo files the emulator smoke test opens.

They exist so CI reads a real Pine script, a real markdown note, a real CSV and
a real PDF rather than a mock. The PDF is assembled here, byte by byte with
correct cross-reference offsets, so the test needs no PDF library.

Usage: make_demo.py <output-directory>
"""

import sys
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


def build_pdf(title: str, lines: list[str]) -> bytes:
    """A one-page PDF with correct object offsets and cross-reference table."""
    text = [b"BT", b"/F1 22 Tf 64 720 Td (" + title.encode("latin-1") + b") Tj", b"ET"]
    y = 680
    for line in lines:
        text.append(b"BT /F1 13 Tf 64 " + str(y).encode() + b" Td (" + line.encode("latin-1") + b") Tj ET")
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
