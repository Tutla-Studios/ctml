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

## Only the renderer is different

Everything up to showing a tree is the same code on a client and on a server. Scripts run through
`CTMLAPI` into the `UIRegistry`, and you build a `DOMContext` with your variables and callbacks. The
server runs its ui folder at startup, the same way the client runs `ctml/ui`. The only difference is the
last step, where the tree and its context are handed to a `UIRenderer`:

| platform | renderer | shows the tree as |
| --- | --- | --- |
| Fabric | `ScreenRenderer` | a `CTMLScreen` |
| Paper, Spigot, Bukkit | `WindowRenderer(player)` | a window in front of that player |

## Commands

| command | what it does |
| --- | --- |
| `/ctml open <name> [player]` | shows a tree from the `UIRegistry` |
| `/ctml run <script> [player]` | runs a script in the ui folder again and shows the new tree, which belongs to that player alone |
| `/ctml demo [player]` | shows the intro, and the full demo from its button, if those files are in the ui folder |
| `/ctml close [player]` | puts a player's window away |
| `/ctml list [registered\|running]` | names the trees in the `UIRegistry`, the windows open now and who has them, or both |
| `/ctml help [command]` | lists every command, or explains one in detail |
| `/ctml execute [targets] [name] [json] <ctml>` | shows ctml written into the command itself |
| `/ctml variable list\|put\|remove [name] [variable] [value]` | lists, sets or clears the variables of an executed ui |
| `/ctml callback list\|put\|remove [name] [callback] [command]` | lists, sets or clears the callbacks of an executed ui |

Leave the player out and it's whoever ran the command. The commands need the `craftytml.use`
permission, which ops have. A window opened from a command has an empty context, so its callbacks call
nothing.

## Uis made from a command

`/ctml execute` shows markup typed straight into the command, without needing a file or any java:

```text
/ctml execute @a shop {"buy":"give {player} diamond 1"} <ctml class="flex items-center justify-center"><button callback="buy" text="buy a diamond ${price}"></button></ctml>
```

- **Targets** are a player's name or a selector such as `@a`. Leave them out and the ui goes to whoever
  ran the command.
- **The name** (`shop` here) is how the variable and callback commands find this ui. It's optional:
  without one, the ui goes under the name `default`.
- **The json** is optional. It maps each callback name to a command, which runs from the console when the
  callback is pressed, with `{player}` replaced by the name of whoever pressed it. Hovering doesn't run
  anything.
- **The ctml** is everything after the json. It's built separately for each target and never registered,
  so each player's window is theirs alone, and the tree is freed when the window closes.

Variables and callbacks can also be kept for a name, and are added to every window open under that name
straight away, and to every one executed under it later:

```text
/ctml variable put shop price 5 coins
/ctml callback put shop sell give {player} emerald 1
/ctml variable remove shop price
/ctml variable list shop
/ctml callback list
```

`list` with a name shows what that ui has, and without one shows every ui that has anything.

Typed in chat, a command can only be 256 characters long. Longer markup has to come from a command block,
a function or the console.

## Using a window

While a window is open, the player can't walk away from it:

- **Looking** moves a dot across the window. That dot is the cursor, and whatever it's over is hovered,
  so `hover:` classes and hover callbacks work.
- **Left click** is a left click, and presses a button. **Right click** is a right click.
- **The hotbar** is the scroll wheel: a step right is a notch down. The selected slot doesn't change.
- **Dragging** takes two clicks. A server hears a click but never the release after it, so one click
  picks a `draggable` box up, it follows the cursor, and the next click puts it down.
- **Sneaking** closes the window, and so does teleporting away, dying or leaving.

## Forms

A server can't hear a keyboard, so what a player types goes through one of minecraft's own dialogs.

- **Click-only controls:** a checkbox ticks, a radio button is chosen, and a slider jumps to where the
  click landed, straight from the click.
- **Typing controls:** clicking a text field, a textarea, a number, a date, a colour or a select opens
  the window's form as a dialog. Every control in the window is in it, in the order they're written:
  text boxes, toggles for checkboxes, sliders for ranges, and a list of choices for each select and
  each group of radio buttons. **Done** writes every value back and fires the `INPUT` callback of each
  control that changed, the same as typing into it on a client. **Cancel** changes nothing.

An input is labelled in the dialog with its `name`, or its `placeholder` where it has no name.

Dialogs are part of Paper. On Spigot and Bukkit the click-only controls still work, but nothing can be
typed.

The player can turn as far as the window's edge and a little past it, and can't walk, jump or break
anything until it closes. If the server crashes with a window open, the player's walk speed is put back
the next time they join.

## Showing a tree from java

The code is the same as on a client, down to the last line:

```java
CTMLAPI.createUI("shop.ctml", markup);
DOMTreeElement tree = UIRegistry.getUIFromName("shop.ctml");

DOMContext ctx = new DOMContext(tree);
ctx.putVariable("coins", 10);
ctx.putCallBack("buy", callback -> {
    if (!callback.isPress()) return;
    callback.getCtx().putVariable("coins", 9);
});

renderer.show("shop.ctml", tree, ctx);
```

`renderer` is `new ScreenRenderer()` on a client and `new WindowRenderer(player)` on a server. Code that
takes a `UIRenderer` and doesn't care which one it gets runs on both platforms unchanged. The intro and
the demo work this way: `DemoIntro.open(renderer)` is the same call on both.

### One tree for each player

A tree in the `UIRegistry` is a single tree. If you show it to two players, they're looking at the same
elements: a callback that changes it changes it for both, and so does either player's cursor, because
`hover:` is kept on the element. To give each player their own tree, build it with `buildUI`, which runs
the script every time it's called and doesn't register the result:

```java
DOMTreeElement tree = CTMLAPI.buildUI(markup, Map.of("player", player));
```

It takes markup in a string or a `Path` to a file. The map holds variables the script can read from its
first line, which is how it knows who it's for. `/ctml run` uses `buildUI` this way.

## How big a window is

A window lays its tree out at 320 by 180 gui pixels, about the size of a small screen, which makes it
3.2 by 1.8 blocks wide, 2.5 blocks from the player's eyes. Pass a `CTMLWindow.Frame` to `open` for
another size:

```java
CraftytmlPaper.windows().open(player, "map", tree, ctx, new CTMLWindow.Frame(480, 270, 0.008, 3.0));
```

### Where a window goes

A window opens straight ahead of the player. To put it somewhere else, pass a `CTMLWindow.Position` to
`show`. It's measured in blocks to the right of and above the spot straight ahead of their eyes, and
negative numbers go left or down. Wherever the window goes, it turns to face the player, and they can
look as far as its edges:

```java
new WindowRenderer(player).show("map", tree, ctx, new CTMLWindow.Position(-1.5, 0)); // to their left
new WindowRenderer(player).show("map", tree, ctx, new CTMLWindow.Position(0, 1));     // above their eyeline
```

The breakpoint variants (`md:`, `lg:` and the rest) are read against those pixels, the same as against a
client's window.

## What a window can't draw

A window is made of flat display entities, and some of what a client screen draws has no equivalent:

- **Drawn:** background colours, borders, text in its colour, weight and slant, scrollbars, opacity,
  gradients, and form controls with their ticks, sliders and swatches. A background gradient is drawn
  as bands of colour, and a text gradient colours each glyph.
- **Not drawn:** rounded corners (a radio's dot is square), shadows, images, focus rings and
  letter-spacing. A text line that a scrolling box would cut off is left out whole, since a display
  can't be cut.
- **No keyboard:** typing goes through the form's dialog. Tab doesn't move the focus, and enter and
  space don't press anything.
- **No huds.** A window is always in front of one player, never drawn over everyone's game.

Text is measured with the vanilla font's glyph widths, which is what the client draws a display in. A
resource pack that changes the font moves the text on screen, but not where the server laid it out.
