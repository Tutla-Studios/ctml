---
title: "Styling"
summary: "The class list: the spacing scale, colours, flex and grid, arbitrary values and variants."
created: "2026-11-01"
updated: "2026-11-01"
isdoc: true
---

Styling is one property. The `class` list is read into a set of styles, and those styles are what
layout and paint work from. If you know tailwind you already know most of this page; what differs is
the scale and the list of what exists.

## The spacing scale

A bare number is spacing units, and one unit is four gui pixels:

| Written | Pixels |
| --- | --- |
| `p-1` | 4 |
| `p-2` | 8 |
| `gap-3` | 12 |
| `w-10` | 40 |

Tailwind uses a quarter of a rem per unit, which is also four css pixels at the default font size,
so the scale reads the same. It is just measured in the pixels minecraft draws in.

Anything that is not on the scale goes in square brackets and is read as written:

```html
<div class="w-[220px] max-w-[60%] px-[3px] gap-[2px]"></div>
```

## Colours

A colour is a family and a shade from the [palette](/ctml/reference/colors), with an optional alpha
after a slash:

```html
<div class="bg-slate-800 text-slate-100 border border-slate-600"></div>
<div class="bg-slate-900/70"></div>
<div class="bg-[#ff00aa] text-[#0f172a]/50"></div>
```

`border` on its own is a one pixel frame. The width, the colour and the style are separate classes,
so `border border-slate-600` is a frame one pixel wide in that colour.

## Laying boxes out

Everything css can do here, ctml does with the same names: block flow, inline text, flexbox, grid,
floats, multi-column and tables.

```html
<div class="flex flex-col gap-1">            <!-- a column -->
<div class="flex flex-row justify-between">  <!-- a row, pushed apart -->
<div class="grid grid-cols-3 gap-1">         <!-- three equal columns -->
<div class="grid grid-cols-[1fr_auto]">      <!-- a track list of your own -->
```

A flex child that should take the room left over says `flex-1`, and one that should keep its own size
says nothing.

Give the stats panel a second column and the rows line up without any widths:

```html
<div class="w-full grid grid-cols-[1fr_auto] gap-x-2">
  <span class="text-slate-400" text="blocks walked"></span>
  <span class="text-right" text="128"></span>
  <span class="text-slate-400" text="mobs met"></span>
  <span class="text-right" text="12"></span>
</div>
```

## Variants

A condition in front of a class says when the class lands:

```html
<button class="bg-sky-600 hover:bg-sky-500 active:bg-sky-700 focus:border-slate-100"></button>
```

They stack, and all of them have to hold:

```html
<div class="md:hover:underline"></div>
```

The breakpoints are gui pixels rather than css pixels, since that is what ctml measures in: `sm` is
320, `md` 480, `lg` 640, `xl` 854 and `2xl` 1024. A minecraft window at the usual gui scale lands
around `md` and `lg`, so those are the two worth designing against. Every variant is listed in the
[variants reference](/ctml/reference/variants).

## Shadows

`shadow-sm`, `shadow`, `shadow-md`, `shadow-lg`, `shadow-xl` and `shadow-2xl` lay a soft shadow
behind a box, spreading 2, 3, 5, 8, 12 and 16 pixels past its edges. It sits a little lower than the
box and follows its rounded corners. `shadow-black/50` sets its colour and `shadow-[10]` its size in
pixels; `shadow-none` takes it away. The shadow is drawn under the whole box, so a box with a
see-through background lets it show through.

## What a class list cannot do

There is no `outline`, `cursor`, `font-family`, `transform`, `transition` or
`animation`. With no font family there is nothing monospaced to put `<code>` and `<pre>` in, so they
keep the spacing they were written with and leave the look to the classes on them.

There is also no `group-` or `peer-` variant, since nothing watches another element's state for a
sibling, and no arbitrary selectors, since there is no selector engine behind them.

A class the parser cannot use is handed back rather than quietly dropped, so a typo is something your
host can report instead of something that silently does nothing.

## Setting a style from java

Classes are not the only way in. A style can be set on an element directly, and it sits over whatever
the classes said:

```java
tree.getElementById("panel").style(ElementStyle.WIDTH, Length.px(240));
tree.getElementById("panel").style("background-color", 0xFF1E293B);
```

Reading the `class` property again replaces everything the classes said before it, which is what lets
a callback show a box it had hidden. The property holds the list the element was written with and
reads back as it stands, so adding or taking away one class is an edit to that string.

## Next

[Variables](/ctml/tutorial/variables) makes the numbers in the panel real.
