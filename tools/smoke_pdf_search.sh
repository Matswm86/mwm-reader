#!/usr/bin/env bash
# PDF search needs the text layer the system PDF engine gained in Android 15
# (API 35), so this runs on its own API 35 emulator. The main smoke test stays
# on API 30, where the reader correctly hides the search button on PDFs.
set -x

PKG=no.mwmai.reader.debug
COMPONENT="$PKG/no.mwmai.reader.MainActivity"
OUT=smoke-pdf
DEVICE_DIR=/data/data/$PKG/files/demo
mkdir -p "$OUT"

python3 tools/make_demo.py "$OUT/demo" || { echo "::error::could not build the demo files"; exit 1; }
chmod +x ./gradlew
./gradlew installDebug --no-daemon || { echo "::error::install failed"; exit 1; }

adb shell "run-as $PKG mkdir -p files/demo" || { echo "::error::run-as is not available"; exit 1; }
base64 -w0 "$OUT/demo/reader-demo.pdf" | adb shell "run-as $PKG sh -c 'base64 -d > files/demo/reader-demo.pdf'" \
    || { echo "::error::could not stage the PDF"; exit 1; }
adb logcat -c || true

dump() {
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
    adb pull /sdcard/ui.xml "$OUT/$1" >/dev/null 2>&1 || true
}

crashed() {
    adb logcat -d > "$OUT/logcat.txt" 2>&1 || true
    if grep -qE "FATAL EXCEPTION|AndroidRuntime: .*(Exception|Error)" "$OUT/logcat.txt"; then
        echo "::error::App crashed"
        grep -B 2 -A 45 -m 1 -E "FATAL EXCEPTION|AndroidRuntime: .*(Exception|Error)" "$OUT/logcat.txt"
        return 0
    fi
    return 1
}

adb shell am start -n "$COMPONENT" -a android.intent.action.VIEW -d "file://$DEVICE_DIR/reader-demo.pdf"
sleep 15
dump ui_pdf.xml
crashed && exit 1
grep -qF "Page 1 / 1" "$OUT/ui_pdf.xml" || { echo "::error::the PDF did not draw"; head -c 7000 "$OUT/ui_pdf.xml"; exit 1; }

python3 tools/ui_center.py "$OUT/ui_pdf.xml" "desc=Find in file" > "$OUT/tap.txt" \
    || { echo "::error::no search button on a PDF at API 35"; head -c 7000 "$OUT/ui_pdf.xml"; exit 1; }
# shellcheck disable=SC2046
adb shell input tap $(cat "$OUT/tap.txt")
sleep 3
# The demo page says "Android draws it with its own PDF engine," once.
adb shell input text "engine"
sleep 6
dump ui_pdf_search.xml
adb exec-out screencap -p > "$OUT/pdf-search.png" 2>/dev/null || true
crashed && exit 1
if ! grep -qF 'text="1/1"' "$OUT/ui_pdf_search.xml"; then
    echo "::error::PDF search did not report exactly one hit for 'engine'"
    grep -oE 'text="[^"]*"' "$OUT/ui_pdf_search.xml" | head -40
    exit 1
fi

# A word that is not on the page must say none, not hang on "searching".
adb shell input keyevent KEYCODE_MOVE_END
for _ in 1 2 3 4 5 6; do adb shell input keyevent KEYCODE_DEL; done
adb shell input text "zebra"
sleep 6
dump ui_pdf_none.xml
crashed && exit 1
grep -qF 'text="none"' "$OUT/ui_pdf_none.xml" || { echo "::error::a missing word did not report none"; grep -oE 'text="[^"]*"' "$OUT/ui_pdf_none.xml" | head -40; exit 1; }

rm -rf "$OUT/demo"
echo "PDF search on API 35: one hit for a word on the page, none for a word that is not."
