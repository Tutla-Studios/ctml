---
title: "Huds"
summary: "Build a CTMLHud around the same tree to draw it over the game, and what a hud cannot do."
created: "2026-11-01"
updated: "2026-11-01"
isdoc: true
---

A hud is the same file, the same elements, the same classes and the same interpreter. The only
difference is which class you build around the tree: `CTMLScreen` opens it as a screen, `CTMLHud`
draws it over the game.

Nothing in the markup says which it is. You can hand the same tree to either.

## A counter in the corner

Create `fpscounter.ctml`:

```html
<ctml class="w-full h-full relative text-shadow">

  <div class="absolute top-1 left-1 w-fit bg-slate-900/70 border border-slate-600 rounded px-1 py-[1px] flex flex-row gap-1 items-center">
    <span class="text-xs text-slate-400" text="fps"></span>
    <span class="text-xs font-bold ${fpsMood}" text="${fps}"></span>
  </div>

</ctml>
```

The root fills the window, as it always does, so a hud is laid out against the whole display. Put
`relative` on it and the panel can sit wherever you like with `absolute` and an inset. `w-fit` keeps
the box only as wide as what is in it.

A hud draws over the game, so a background at an alpha is usually what you want: `bg-slate-900/70`
reads over grass and over a cave alike.

## Register it

A hud goes in through Fabric's own hud registry, so it takes its turn with the vanilla elements:

```java
public static void addFpsCounter(){
    DOMTreeElement tree = UIRegistry.getUIFromName("fpscounter.ctml");
    if (tree == null) return;

    DOMContext ctx = new DOMContext(tree);
    HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("yourmod", "fps-counter"),
            new CTMLHud(tree, ctx));

    ClientTickEvents.END_CLIENT_TICK.register(client -> {
        int fps = client.getFps();
        ctx.putVariable("fps", fps);
        ctx.putVariable("fpsMood", fps < 30 ? "text-rose-400" : fps < 60 ? "text-amber-300" : "text-emerald-300");
    });
}
```

`CTMLHud` is a `HudElement`, so anything the registry can do with one it can do with this: `addLast`
puts it over everything, and `attachElementBefore` or `attachElementAfter` with a
`VanillaHudElements` identifier puts it next to a particular vanilla one.

Register it once, on your client initialiser, after the scripts have run.

## What a hud cannot do

A hud gets no press, no wheel, no key and no cursor. That means:

- **Callbacks never fire.** A `callback` property on a hud element says nothing.
- **`hover:`, `focus:`, `focus-visible:`, `focus-within:` and `active:` never hold**, there being
  nothing to tell the tree about the mouse or the keyboard.
- **A draggable box is never dragged**, and a scrollbar is drawn but cannot be taken hold of.

Everything a script drives still works: the variables a `${name}` reads, a class list written from
java, and `scrollx`, `scrolly` and `scrollinto`, which a hud animates the way a screen does.

The `dark:` variant and the breakpoints still hold, since those are questions about the element and
the window rather than about the user.

## The same tree, both ways

Because the file says nothing about which it is, a panel can be both: a hud while you play, and a
screen you can actually press when you open it. Build two views over two trees:

```java
DOMTreeElement hudTree = UIRegistry.getUIFromName("stats.ctml");
DOMContext hudCtx = new DOMContext(hudTree);
HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("yourmod", "stats"), new CTMLHud(hudTree, hudCtx));
```

Give each view its own tree and its own context. One tree in two views at once would have both
laying the same boxes out, which is one more thing than either of them expects.

For a second tree of the same markup, register the file under two names:

```java
CTMLAPI.createUI("stats-hud.ctml", markup);
CTMLAPI.createUI("stats-screen.ctml", markup);
```

## Next

[Scrolling](/ctml/tutorial/scrolling) is about content that does not fit, which works in both views.
