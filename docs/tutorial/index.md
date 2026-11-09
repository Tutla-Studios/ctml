---
title: "Tutorial"
summary: "Build a CraftyTML screen and a hud from nothing, one idea at a time."
created: "2026-11-01"
updated: "2026-11-01"
isdoc: true
---

This tutorial builds one thing all the way through: a small stats panel, opened as a screen and then
drawn again as a hud. Each page adds one idea and leaves you something that runs.

It assumes you have the dependency in place and a `.ctml` file being read, which
[Getting started](/ctml/getting-started) covers.

## The pages

| Page | What it adds |
| --- | --- |
| [Your first screen](/ctml/tutorial/first-screen) | The tree, the root element, and opening it |
| [Styling](/ctml/tutorial/styling) | Classes, the spacing scale, flex and grid, variants |
| [Variables](/ctml/tutorial/variables) | `${name}`, and changing what a screen says from java |
| [Callbacks](/ctml/tutorial/callbacks) | Buttons, presses, hovers and drags |
| [Huds](/ctml/tutorial/huds) | The same tree drawn over the game |
| [Scrolling](/ctml/tutorial/scrolling) | Scrollports, styled bars and bringing a box into view |
| [Driving it from java](/ctml/tutorial/java) | Markup written in java, your own gui, headless checks |
| [Paper and Spigot](/ctml/tutorial/paper) | The same files on a server, as a window in front of one player |

## How the pieces fit

Three things do the work, and keeping them apart is most of understanding CraftyTML:

- **The tree.** A `.ctml` file is read once and becomes a `DOMTreeElement` with everything under it.
  Nothing in the file says whether it is a screen or a hud.
- **The context.** A `DOMContext` holds the variables the tree reads and the callbacks it names. It
  is also how you reach into the tree by id.
- **The view.** A `CTMLScreen` opens the tree as a screen; a `CTMLHud` draws the same tree over the
  game. The view is the only thing that decides which it is.

```text
hello.ctml  ──CTMLAPI──▶  DOMTreeElement  ──┬──▶  CTMLScreen   (mouse, keyboard, callbacks)
                                 ▲          └──▶  CTMLHud      (drawn only, hears nothing)
                                 │
                            DOMContext      (variables, callbacks, getElementById)
```

A tree can only be in one view at a time, and a hud hears nothing from the user, so its callbacks
never fire. Everything else is the same.

## Two things to know up front

A `.ctml` file is read at startup only, so an edit needs the game restarted. And every element is
written with a closing tag, except `<hr>`, `<br>` and `<img>`, which hold nothing and may be written
either way.
