---
title: "Your first screen"
summary: "Write a .ctml file, read it into a tree and open it as a CTMLScreen."
created: "2026-11-01"
updated: "2026-11-09"
isdoc: true
---

A screen is a `.ctml` file, a context, and one line of java. This page writes the stats panel the
rest of the tutorial builds on.

## The file

Create `stats.ctml` in `ctml/ui/`, or in `plugins/CraftyTML/ui/` on a server:

```html
<ctml class="w-full h-full flex items-center justify-center bg-slate-900/80 text-slate-100 text-shadow">

  <div class="w-full max-w-[220px] flex flex-col gap-1 p-2 bg-slate-800 border border-slate-600 rounded">

    <div class="w-full text-lg font-bold text-center text-amber-300" text="Your run"></div>
    <hr class="w-full border-slate-600">

    <div class="w-full flex flex-row justify-between">
      <span class="text-slate-400" text="blocks walked"></span>
      <span class="text-slate-100" text="0"></span>
    </div>

    <div class="w-full flex flex-row justify-between">
      <span class="text-slate-400" text="mobs met"></span>
      <span class="text-slate-100" text="0"></span>
    </div>

  </div>

</ctml>
```

## What the root does

`<ctml>` is html and body in one. It is `w-full h-full` already, so it fills whatever it is drawn in,
and the styles that come down the tree, such as the text colour, bottom out in it.

The classes on it here do three jobs:

- `flex items-center justify-center` centres the single child in both directions
- `bg-slate-900/80` is a background at eight tenths alpha, so the game shows through
- `text-shadow` is minecraft's own drop shadow behind the text, which is on by default and named here
  to make the point that it is a style like any other

## Text is a property

A box says something with its `text` property rather than with a child node:

```html
<span class="text-slate-400" text="blocks walked"></span>
```

Raw text between the tags works too, and reads as a node of its own among the elements around it:

```html
<p class="text-xs">Every screen is a file.</p>
```

Use `text` when the box has nothing else in it, and raw text when you are mixing words with inline
elements.

## Read it and open it

```java
ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
    if (!(screen instanceof TitleScreen)) return;

    DOMTreeElement tree = UIRegistry.getUIFromName("stats.ctml");
    if (tree == null) return;

    DOMContext ctx = new DOMContext(tree);
    CTMLScreen stats = new CTMLScreen("stats.ctml", tree, screen, ctx);
    client.execute(() -> client.setScreenAndShow(stats));
});
```

`UIRegistry` holds every tree `CTMLAPI.runAllScripts` produced, under its file name. It gives back
`null` for a name nothing registered, which is what a missing or broken file looks like, so it is
worth checking rather than letting a null reach the screen.

The screen is built before it is shown because the constructor takes the screen to go back to. Here
that is the title screen the event handed you.

### On a server

A server has no title screen. Show the panel when a player joins, or from a command of your own. Only
the last line is different:

```java
@EventHandler
public void onJoin(PlayerJoinEvent event){
    DOMTreeElement tree = UIRegistry.getUIFromName("stats.ctml");
    if (tree == null) return;

    DOMContext ctx = new DOMContext(tree);
    new WindowRenderer(event.getPlayer()).show("stats.ctml", tree, ctx);
}
```

Or skip the java and run `/ctml open stats.ctml` in game. The panel floats in front of the player, who
can't walk away from it until they sneak or it's closed with `/ctml close`.

## What happens each frame

`CTMLScreen` lays the tree out only when something has actually moved. A style, a property, the tree
itself, a drag or a scroll bumps the dom's revision, and a resize changes the screen; every other
frame just draws the boxes that are already worked out. You do not have to manage any of that, but it
is why putting a variable in the context is enough to change what the screen says.

A window on a server works the same way, once a tick: a tree that hasn't changed costs nothing, and a
changed one only updates the parts that came out different.

## Next

The panel works and looks plain. [Styling](/ctml/tutorial/styling) is about the class list.
