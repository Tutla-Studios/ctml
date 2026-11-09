---
title: "Paper and Spigot"
summary: "Run the same .ctml files on a Paper, Spigot or Bukkit server, shown to one player at a time as a window floating in front of them."
created: "2026-11-09"
updated: "2026-11-09"
isdoc: true
---

CraftyTML runs on servers as well as clients. The same jar is a Fabric mod and a Paper, Spigot and Bukkit
plugin. On a server it reads the same `.ctml` files and lays them out the same way, but it can't open a
screen on a player's client. Instead it builds a window out of display entities that floats in front of
one player, and they use it by looking at it and clicking.

Nothing needs to be installed on the client. A vanilla client sees the window.

## Installing it

Put the CraftyTML jar and the Tums jar in the server's `plugins/` folder. CraftyTML depends on Tums to
run its scripts, and won't load without it. Both work on Paper, Spigot and Bukkit for minecraft 26.3.

Start the server once and it creates `plugins/CraftyTML/ui/`. That's where your `.ctml` files go.

## A script runs for one player

A client has one player, so it runs its whole ui folder at startup. A server has many players, so
nothing runs at startup. A script runs when someone asks for it, for one player at a time, and the tree
it builds is that player's alone. Two players running the same script get two separate trees: one
player's clicks, hovers and variables never reach the other's window.

The script sees who it's running for as `player`, with `player.name` and `player.uuid` to hand.

## Commands

| command | what it does |
| --- | --- |
| `/ctml run <script> [player]` | runs a script for a player and shows what it builds, with any callbacks registered for it |
| `/ctml demo [player]` | shows the intro, and the full demo from its button, if those files are in the ui folder |
| `/ctml close [player]` | puts a player's window away |
| `/ctml list` | names the scripts in the ui folder |

Leave the player out and it's whoever ran the command. The commands need the `craftytml.use`
permission, which ops have.

## Using a window

While a window is open, the player can't walk away from it:

- **Looking** moves a dot across the window. That dot is the cursor, and whatever it's over is hovered,
  so `hover:` classes and hover callbacks work.
- **Left click** is a left click, and presses a button. **Right click** is a right click.
- **The hotbar** is the scroll wheel: a step right is a notch down. The selected slot doesn't change.
- **Dragging** takes two clicks. A server hears a click but never the release after it, so one click
  picks a `draggable` box up, it follows the cursor, and the next click puts it down.
- **Sneaking** closes the window, and so does teleporting away, dying or leaving.

The player can turn as far as the window's edge and a little past it, and can't walk, jump or break
anything until it closes. If the server crashes with a window open, the player's walk speed is put back
the next time they join.

## Callbacks

Callbacks are answered by java, the same as on a client (see [Callbacks](/ctml/tutorial/callbacks)). On a
server you register them for a script by name. They're put on the context every time that script is
opened, and the player it's open for is passed in alongside:

```java
CraftytmlPaper.callbacks("shop.ctml", (player, ctx) -> {
    ctx.putVariable("coins", 10);
    ctx.putCallBack("buy", callback -> {
        if (!callback.isPress()) return;
        player.sendMessage("bought");
    });
});
```

Now `/ctml run shop.ctml` and `CraftytmlPaper.open(player, "shop.ctml")` both open the shop with
`buy` answered.

To build the context yourself, run the script and open the tree with it:

```java
DOMTreeElement tree = CraftytmlPaper.run(player, "shop.ctml");
DOMContext ctx = new DOMContext(tree);
ctx.putCallBack("buy", callback -> player.sendMessage("bought"));
CraftytmlPaper.windows().open(player, tree, ctx);
```

## How big a window is

A window lays its tree out at 320 by 180 gui pixels, about the size of a small screen, which makes it
3.2 by 1.8 blocks wide, 2.5 blocks from the player's eyes. Pass a `CTMLWindow.Frame` to `open` for
another size:

```java
CraftytmlPaper.windows().open(player, tree, ctx, new CTMLWindow.Frame(480, 270, 0.008, 3.0));
```

The breakpoint variants (`md:`, `lg:` and the rest) are read against those pixels, the same as against a
client's window.

## What a window can't draw

A window is made of flat display entities, and some of what a client screen draws has no equivalent:

- **Drawn:** background colours, borders, text in its colour, weight and slant, scrollbars, opacity,
  and gradients. A background gradient is drawn as bands of colour, and a text gradient colours each
  glyph.
- **Not drawn:** rounded corners, shadows, images, focus rings, letter-spacing, and the insides of form
  controls. A text line that a scrolling box would cut off is left out whole, since a display can't be
  cut.
- **No keyboard:** nothing can be typed into, tab doesn't walk the focus, and enter and space don't press
  anything. Only the mouse is available.
- **No huds.** A window is always in front of one player, never drawn over everyone's game.

Text is measured with the vanilla font's glyph widths, which is what the client draws a display in. A
resource pack that changes the font moves the text on screen, but not where the server laid it out.
