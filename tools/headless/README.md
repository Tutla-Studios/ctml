# headless layout tests

The layout engine is pure arithmetic over a dom tree, so all of it can be checked without a
running game: the suites here build a tree, lay it out against a stub font of six pixel
characters and nine pixel lines, and compare what comes out against numbers worked out by hand.

    tools/headless/run.sh                         every suite
    tools/headless/run.sh ScrollTest GridTest     just those
    tools/headless/run.sh demo run/ctml/ui/groot.ctm   lay a real script out and print the boxes

They compile against the jars gradle last resolved, which it writes to
`build/loom-cache/argFiles/runClient`, so the client (or any gradle build) has to have run once.
Nothing here is part of the mod: no gradle wiring, no dependency on it from `src`.

| suite | what it covers |
| --- | --- |
| `FlexTest` | direction, wrapping, grow and shrink, basis, order, the alignments, baselines |
| `FlowTest` | block flow, inline text, line breaking, margins, intrinsic widths, sticky, fixed |
| `ScrollTest` | overflow, scrollports, bars, the wheel, gutters, keys, sliding, anchoring |
| `GridTest` | track lists, fr shares, auto tracks, spans, placement, the flow, alignments |
| `TableTest` | column sizing, row heights, collapsed borders |
| `TableElementTest` | the table tags and their properties, end to end from a `.ctm` file |
| `FloatTest` | floats, clearing, text flowing past them |
| `GradientTest` | gradient classes, stop positions, the ramp, where a point falls in a box |
| `DiagnosticsTest` | what a script is told about a class nothing can use, and lost mouse releases |
| `VariantTest` | the conditions in front of a class: hover, focus, active, dark, breakpoints, places |

`Bench.java` times layout instead of checking it, for the caching work.
