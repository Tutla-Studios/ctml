---
title: "Driving it from java"
summary: "Markup written in java, a ctml tree inside your own gui, reading back what a script was told, and checking a layout headless."
created: "2026-11-01"
updated: "2026-11-09"
isdoc: true
---

Everything so far has gone through a `.ctml` file and a `CTMLScreen`. Neither is required. This page
is the parts of CraftyTML a host can reach past them.

## Markup written in java

A tree does not have to come off disk. `createUI` takes the markup as a string, under whatever name
you want to find it by:

```java
String markup = """
        <ctml class="w-full h-full flex items-center justify-center bg-slate-900/80">
          <div class="p-2 bg-slate-800 rounded" text="${line}"></div>
        </ctml>
        """;

CTMLAPI.createUI("toast.ctml", markup);
DOMTreeElement tree = UIRegistry.getUIFromName("toast.ctml");
```

This is how you ship ui with your mod rather than asking the user to put a file somewhere. It is also
how you get two trees out of one piece of markup, by registering it twice under two names.

`runAllScripts` is the folder version of the same thing, and `createUI(Path)` reads one file.

## A tree inside your own gui

`CTMLRenderer` is public in two halves, and they are separate on purpose:

```java
public class MyScreen extends Screen {
    private final DOMTreeElement tree;
    private CTMLLayout.Box layout;

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick){
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        layout = CTMLRenderer.layout(tree, graphics.guiWidth(), graphics.guiHeight());
        CTMLRenderer.paint(layout, graphics);
    }
}
```

`layout` gives you the boxes and `paint` draws them, so a ctml tree can go inside a gui you already
have, beside widgets you wrote yourself. `render` does both in one call, and `renderScreen` does both
against a screen's own size.

What you take on by doing this is what `CTMLScreen` was doing for you:

- **Only lay out when something moved.** Compare `DOMTreeNode.revision()` against what you last laid
  out at, and against the size you laid out for. Laying out every frame works and is wasteful.
- **Tell the tree about the mouse and the keyboard.** The `hover:`, `focus:` and `active:` variants
  are answered by the element, and the element is told by whatever is drawing it, through
  `setHovered` and its friends. A tree nothing tells will never show a hover.
- **Fire the callbacks yourself**, with `DOMContext.fire`, which says whether anything was actually
  called so you know to swallow the press.

If you want all of that, use `CTMLScreen`. Use `layout` and `paint` when you want the drawing and
none of the behaviour.

## On a server

The client-only part of this page is drawing inside a gui of your own. The rest carries over to a
plugin:
- **Markup written in java and registered with `CTMLAPI.createUI`** works on a server too. So does
  `runAllScripts`, though CraftyTML already runs its own ui folder as it starts.
- **A tree of one player's own:** a server has many players, and a registered tree is shared by everyone
  shown it. `CTMLAPI.buildUI` builds a fresh tree every time it is called and registers nothing, with
  variables the script can read from its first line:

  ```java
  DOMTreeElement tree = CTMLAPI.buildUI(markup, Map.of("player", player));
  ```

- **Showing a tree** goes through a renderer: `new WindowRenderer(player)` or
  `new HologramRenderer(location)`.
- **Code that takes a `UIRenderer`** rather than one of those runs on both a client
  (`new ScreenRenderer()`) and a server unchanged. That's how the bundled intro and demo are written.
- **Reaching into the tree** works the same, and `CTMLDiagnostics` reports bad classes the same way.

A window is a surface of text displays laid out against the vanilla font, sized by
`CTMLWindow.Frame` and placed by `CTMLWindow.Position`; [Paper and Spigot](/ctml/tutorial/paper) covers
both.

## Reaching into the tree

```java
DOMTreeElement panel = ctx.getElementById("panel");

panel.property("class", "w-full p-2 bg-slate-700 rounded", null);
panel.style(ElementStyle.WIDTH, Length.px(240));
panel.style("background-color", 0xFF1E293B);
```

Setting a property or a style marks the tree as changed on up to the root, so whatever is drawing it
lays it out again next frame. Nothing has to be told twice.

Every element ends up with an id, its own or one handed out in order the first time it is asked for,
and `getElementById` walks the tree each time it is called rather than keeping a map. A tree that
changes shape can therefore never hand back something stale, at the cost of a walk per lookup.

## Reading back what a script was told

A misspelled class does not take the screen down. `CTMLDiagnostics` prints a warning in the same
shape as an error, with the arrows under the place in the file, and carries on. Every note is kept,
so a test or a tool can read them:

```java
for (CTMLDiagnostics.Note note : CTMLDiagnostics.notes()) {
    LOGGER.warn("ctml: {}", note);
}
```

`TailwindParser.parseInto` hands back the classes it could not use, whether unknown altogether or
carrying a value the style cannot take, so a host that wants to report typos itself can.

## Checking a layout without the game

Layout is arithmetic over a dom tree and needs no running minecraft, which is what makes the whole
engine checkable headless. The
[`tools/headless`](https://github.com/Tutla-Studios/ctml/tree/master/tools/headless/README.md)
folder in the repository builds trees, lays them out against a stub font and compares the boxes
against numbers worked out by hand. It can also lay a real script out and print what comes out:

```bash
tools/headless/run.sh demo run/ctml/ui/stats.ctml
```

Nothing in there is part of the mod, so you can do the same in your own project: build a
`DOMTreeElement`, call `CTMLRenderer.layout`, and assert on the boxes.

## Adding an element of your own

CraftyTML, Tums and Tusan are open source, so anything the mod does your mod can do to it. An element
is a subclass of `CTMLElement` that names its tag and overrides `defaultStyle()` where it behaves
differently from a plain div, registered with `ElementRegistry.register`. A property is the same
shape through `ElementProperty` and `PropertyRegistry`.

Both registries are read when the lexer rules are built, so a registration has to happen before the
first script runs.

## Where to go next

The [reference](/ctml/reference) is generated from the source, so it says what your build actually
does: every [element](/ctml/reference/elements), [property](/ctml/reference/properties),
[style](/ctml/reference/styles) and [class](/ctml/reference/classes), and the
[java classes](/ctml/reference/java) with their javadoc.
