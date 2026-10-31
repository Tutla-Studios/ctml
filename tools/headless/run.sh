#!/bin/sh
# Lays the engine out without a running game: compiles the mod's own sources against the jars
# gradle last resolved, then every suite next to this script. No game, no window, no gpu.
#
#   tools/headless/run.sh            every suite
#   tools/headless/run.sh FlexTest   just one
#   tools/headless/run.sh demo <file.ctm>   lay a real script out and print the boxes
set -e

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
out=${CTML_HEADLESS_OUT:-/tmp/ctml-headless}

args="$root/build/loom-cache/argFiles/runClient"
if [ ! -f "$args" ]; then
    echo "no classpath to compile against: $args is missing." >&2
    echo "run the client (or any gradle build) once so loom writes it, then try again." >&2
    exit 1
fi
# the mod's own classes are left off: they are whatever gradle built last, and what is wanted
# here is what the sources say now
cp=$(sed -n 2p "$args" | tr ':' '\n' | grep -v "^$root/build/" | paste -sd:)

rm -rf "$out" && mkdir -p "$out"
# the two fabric entry points want the loader, which nothing here needs
find "$root/src" -name "*.java" | grep -vE "Craftytml(Client|DataGenerator)?\.java" > "$out/srcs.txt"
javac -nowarn -encoding UTF-8 --release 25 -cp "$cp" -d "$out" @"$out/srcs.txt"
javac -nowarn -encoding UTF-8 --release 25 -cp "$out:$cp" -d "$out" "$here"/*.java

run(){ java -Dfile.encoding=UTF-8 -cp "$out:$cp" "$@"; }

if [ "$1" = "demo" ]; then
    shift
    run DemoDump "$@"
    exit $?
fi

suites=${*:-"FlexTest FlowTest ScrollTest GridTest TableTest TableElementTest FloatTest GradientTest DiagnosticsTest BackgroundTest PaintTest FocusTest ImgTest MarkdownTest ButtonTest VariableTest VariantTest"}
failed=0
total=0
for suite in $suites; do
    passed=$(run "$suite" 2>/dev/null | grep -c "^ok" || true)
    last=$(run "$suite" 2>/dev/null | tail -1)
    total=$((total + passed))
    printf "%-18s %4s  %s\n" "$suite" "$passed" "$last"
    case "$last" in *FAILED*) failed=1;; esac
done
echo "$total checks"
exit $failed
