#!/usr/bin/env bash
# Installs the APK on the CI emulator, pushes a real Pine script, markdown note,
# CSV and PDF onto the "phone", opens each one, and reads the result back out of
# the view hierarchy. A green compile proves nothing about whether a file draws.
#
# A file rather than an inline workflow script: the emulator action feeds inline
# scripts to `sh -c` one line at a time, which breaks every multi-line `if`.
set -x

PKG=no.mwmai.reader.debug
ACTIVITY=no.mwmai.reader.MainActivity
COMPONENT="$PKG/$ACTIVITY"
OUT=smoke
DEVICE_DIR=/sdcard/Download
mkdir -p "$OUT"

python3 tools/make_demo.py "$OUT/demo" || { echo "::error::could not build the demo files"; exit 1; }

adb shell mkdir -p "$DEVICE_DIR"
for F in "$OUT"/demo/*; do
    adb push "$F" "$DEVICE_DIR/" >/dev/null || { echo "::error::push failed for $F"; exit 1; }
done

chmod +x ./gradlew
./gradlew installDebug --no-daemon || { echo "::error::install failed"; exit 1; }
adb shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE || true

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

# expect <dump-file> <text> [<text> ...]
expect() {
    FILE="$OUT/$1"; shift
    BAD=0
    for TEXT in "$@"; do
        if ! grep -qF "$TEXT" "$FILE"; then
            echo "::error::$FILE is missing the text: $TEXT"
            BAD=1
        fi
    done
    if [ "$BAD" -ne 0 ]; then
        head -c 7000 "$FILE"
        exit 1
    fi
}

# open <file-name> <dump-name> <seconds>
# No -t: the component is explicit, so no intent filter is consulted, and the
# reader picks the format from the file name the way it does on a real phone.
open_file() {
    adb shell am start -n "$COMPONENT" -a android.intent.action.VIEW \
        -d "file://$DEVICE_DIR/$1" || echo "am start returned $?"
    sleep "$3"
    dump "$2"
    crashed && exit 1
}

# ---------------------------------------------------------------- home screen
adb shell am start -n "$COMPONENT" || echo "am start returned $?"
sleep 18
dump ui_home.xml
adb exec-out screencap -p > "$OUT/home.png" 2>/dev/null || true
crashed && exit 1
expect ui_home.xml "MWM Reader" "Open a file" "Add a folder" "RECENT"

# ------------------------------------------------------------ the Pine script
open_file "MWM_Sweep_Demo.pine" ui_pine.xml 10
expect ui_pine.xml "MWM_Sweep_Demo.pine" "PINE" "MWM Sweep Demo" "ta.highest"
adb exec-out screencap -p > "$OUT/pine.png" 2>/dev/null || true

# --------------------------------------------------------------- the markdown
open_file "reader-demo.md" ui_md.xml 9
expect ui_md.xml "reader-demo.md" "Markdown" "MWM Reader demo note" "What it shows"
adb exec-out screencap -p > "$OUT/markdown.png" 2>/dev/null || true

# -------------------------------------------------------------------- the PDF
open_file "reader-demo.pdf" ui_pdf.xml 12
expect ui_pdf.xml "reader-demo.pdf" "Page 1 / 1"
adb exec-out screencap -p > "$OUT/pdf.png" 2>/dev/null || true

# -------------------------------------------------------------------- the CSV
open_file "trades.csv" ui_csv.xml 9
expect ui_csv.xml "trades.csv" "r_multiple" "swept Asia low then reclaimed"

# ------------------------------------------------------------- the plain text
open_file "notes.txt" ui_txt.xml 8
expect ui_txt.xml "notes.txt" "wrapped by the"

# ------------------------------------------------- find in file, on the script
open_file "MWM_Sweep_Demo.pine" ui_pine2.xml 9
python3 tools/ui_center.py "$OUT/ui_pine2.xml" "desc=Find in file" > "$OUT/tap.txt" \
    || { echo "::error::no search button on the toolbar"; head -c 7000 "$OUT/ui_pine2.xml"; exit 1; }
# shellcheck disable=SC2046
adb shell input tap $(cat "$OUT/tap.txt")
sleep 3
adb shell input text "displacement"
sleep 4
dump ui_search.xml
crashed && exit 1
if ! grep -qE 'text="[0-9]+/[0-9]+"' "$OUT/ui_search.xml"; then
    echo "::error::search ran but no hit counter appeared"
    head -c 7000 "$OUT/ui_search.xml"
    exit 1
fi

rm -rf "$OUT/demo"
echo "Home listed, Pine coloured, markdown rendered, PDF drawn, CSV tabulated, text wrapped, search found hits."
