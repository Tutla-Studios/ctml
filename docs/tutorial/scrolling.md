---
title: "Scrolling"
summary: "Make a box a scrollport, style its bars, and bring a child into view from java."
created: "2026-11-01"
updated: "2026-11-09"
isdoc: true
---

A box whose content does not fit can scroll. One class makes it a scrollport, and from there the
wheel, the keys and a scrollbar all work without any java.

## A scrollport

```html
<div class="w-full h-[80px] overflow-y-auto flex flex-col gap-1 pr-1">
  <div class="w-full" text="an entry"></div>
  <div class="w-full" text="another entry"></div>
  <!-- and as many more as you like -->
</div>
```

`overflow-y-auto` gives it a bar only when there is something to scroll. `overflow-auto` does both
axes, and `overflow-y-scroll` keeps the bar whether it is needed or not.

A scrollport needs a size on the axis it scrolls. `h-[80px]` here, or `h-full` inside a parent that
has one. Without a definite height there is nothing for the content to overflow.

The wheel scrolls the box under the cursor, and whatever it cannot use is handed on to the box around
it. `overscroll-y-contain` stops that being handed on.

## Styling the bar

The bars are ctml's own classes, loosely after css's `scrollbar-width` and `scrollbar-color`:

```html
<div class="overflow-y-auto scrollbar-w-2 scrollbar-rounded-1
            scrollbar-thumb-slate-500 scrollbar-track-slate-800/50"></div>
```

`scrollbar-hidden` and `scrollbar-none` take the bars away and leave the box scrolling.

How the bars look comes down the tree the way css inherits `scrollbar-width`, so setting them once on
a container styles every scrollport inside it.

A bar takes its room out of the content, so a box that turns out to need one is laid out a second
time with the room kept. Only once: if keeping the room makes the other axis overflow, that axis's
bar floats over the content rather than asking for a third pass. `scrollbar-gutter-stable` keeps the
room whenever the axis could scroll, so nothing jumps sideways when a bar turns up.

## Smooth scrolling

```html
<div class="overflow-y-auto scroll-smooth"></div>
```

A scroll then slides there instead of landing at once. Css only promises this for the scrolls a
script asks for; ctml slides the wheel and the keys too.

## Scrolling from java

Three properties drive a scrollport. They are written, not read: whatever lays the element out takes
the value off and acts on it.

```java
DOMTreeElement list = ctx.getElementById("entries");

list.property("scrolly", 0, null);            // back to the top
list.property("scrollinto", "last-entry", null);  // bring that child into view
```

`scrollx` and `scrolly` are how far the content is scrolled, in pixels. `scrollinto` is the id of a
descendant to bring into view, which is css's `scrollIntoView`.

A box is brought in from the nearest edge, and that is the only alignment there is: there is no block
or inline alignment to ask for, and no scroll margin to hold the box clear of the edge.

## Keeping the place through a resize

`overflow-anchor-auto` holds the content on show still when something above it changes size, which is
on by default. A box's own scroll picks the anchor afresh, so a resize landing on the same frame as a
scroll is not corrected; it picks a new anchor and waits for the frame after.

Where the scroll sits is also kept as a share of the room it had, so the same place comes back after
the window changes size.

## On a server

Everything on this page works the same in a window or a hologram on a server. Only the input is different:
- **The hotbar is the wheel.** Scrolling the hotbar scrolls the box the player is looking at, a step to
  the right being a notch down, and the selected slot doesn't move.
- **No keys.** The arrow keys, page up and down, home and end never reach a server.
- **No grabbing the bar.** A bar can't be grabbed; it's still drawn and still moves with the scroll.
- **On a hologram**, the hotbar scrolls the box under the player's cursor in the same way. The held slot
  only stays put when something scrolled, so a player can still change items while looking at one.

## In a hud

All of this works in a hud, except taking hold of a bar. A hud gets no cursor and no wheel, so a
scrollport there is something only `scrollx`, `scrolly` and `scrollinto` move. The bar is still drawn
and still animated.

## Next

[Driving it from java](/ctml/tutorial/java) is the last page: markup written in java, a tree inside a
gui of your own, and checking a layout without launching the game.
