---
title: "Callbacks"
summary: "Name a function in the markup, register it on the context, and hear what the user does to an element."
created: "2026-11-01"
updated: "2026-11-01"
isdoc: true
---

A callback is how the screen tells java something. The markup names a function, you register that
name on the context, and the function is handed a `CTMLCallback` saying what happened and to which
element.

## A button

```html
<button class="px-2 py-1 bg-sky-600 rounded hover:bg-sky-500 active:bg-sky-700 focus:border-slate-100"
        callback="reset" text="start again"></button>
```

```java
ctx.putCallBack("reset", callback -> {
    if (!callback.isPress()) return;
    callback.getCtx().putVariable("blocks", 0);
    callback.getCtx().putVariable("mobs", 0);
});
```

## One name, everything that happens

This is the part worth getting straight. A name is **one** function, and that function hears about
everything that happens to the element, not only the presses. A function that acts on all of it fires
when the cursor merely arrives, which looks like the button pressing itself.

`isPress()` is how you say you only want the real thing: a press on a button, or a left click on
anything else.

What a callback can be told:

| Type | When |
| --- | --- |
| `LEFT_CLICK`, `RIGHT_CLICK`, `MIDDLE_CLICK` | A mouse button went down on the element |
| `INPUT` | A press on a button, or enter or space while it has the focus |
| `HOVER` | The cursor has just come onto the element |
| `DRAG` | A draggable element is being moved |

`getCallbackType()` says which, and `getKeycode()` says what key set an `INPUT` off, or `NO_KEY`
where the mouse did.

And what nothing is told about: a release, a wheel, a key other than enter and space, an element
being scrolled, and the cursor *leaving*. `HOVER` is only told about the way in.

## Hovering as a feature

Since hover reaches the callback, it is a cheap way to explain a control:

```java
ctx.putCallBack("reset", callback -> {
    DOMContext context = callback.getCtx();
    if (!callback.isPress()) {
        context.putVariable("hint", "puts both counters back to zero");
        return;
    }
    context.putVariable("blocks", 0);
    context.putVariable("mobs", 0);
    context.putVariable("hint", "done");
});
```

For a purely visual response, prefer the `hover:` and `active:` variants. They cost nothing and need
no java at all.

## Which element

`getElement()` is the `DOMTreeElement` the callback is about, which is useful when one function serves
several elements:

```java
ctx.putCallBack("pick", callback -> {
    if (!callback.isPress()) return;
    String picked = String.valueOf(callback.getElement().getPropertyValue("value"));
    callback.getCtx().putVariable("chosen", picked);
});
```

`getCtx()` is the context the tree is shown through, which is where you put variables back and where
`getElementById` lives.

## Showing and hiding a box

Reading the `class` property again replaces everything the classes said before it, so adding or
removing `hidden` is an edit to that string:

```java
ctx.putCallBack("togglePanel", callback -> {
    if (!callback.isPress()) return;

    DOMTreeElement panel = callback.getCtx().getElementById("details");
    if (panel == null) return;

    boolean showing = panel.getStyleValue(ElementStyle.DISPLAY) != ElementStyle.Display.NONE;
    panel.property("class", classes(panel, "hidden", showing), null);
    callback.getElement().property("text", showing ? "show details" : "hide details", null);
});
```

with `classes` rewriting the one class and leaving the rest alone:

```java
private static String classes(DOMTreeElement element, String className, boolean add){
    Object written = element.getPropertyValue("class");
    List<String> list = new ArrayList<>();
    if (written != null) {
        for (String each : written.toString().strip().split("\\s+")) {
            if (!each.isEmpty() && !each.equals(className)) list.add(each);
        }
    }
    if (add) list.add(className);
    return String.join(" ", list);
}
```

The property holds the list the element was written with and reads back as it stands, so nothing is
lost by having to write it back whole.

## Dragging

`draggable` on its own makes a box movable inside its parent's content box:

```html
<div id="panel" class="w-[220px] bg-slate-800 border border-slate-600 rounded" draggable></div>
```

It needs no callback to work. Name one and you are told while it moves, with `DRAG`, which is how you
would write the position down somewhere.

A drag ends on the mouse release. There is no way to read the live button state back on this build,
so a release that never arrives is worked out from the cursor instead: a button that is held reports
every movement it makes, so one that moves with nothing reported lets go within a few frames.

## Buttons are plain boxes

A `<button>` is a box that can be pressed. It is inline-block, centres its text, and is in the tab
order without saying so. Nothing disables it, and it has no pressed or hovered look of its own, so
you write one with `hover:` and `active:` like on any other box.

A `callback` works on any element, not only buttons. What a button adds is the `INPUT` type and
keyboard reach.

## No links

There is no `<a>`: a link has nowhere to go. To open a url, put a button on it and call minecraft's
own confirmation screen from the callback:

```java
ctx.putCallBack("openWiki", callback -> {
    if (!callback.isPress()) return;
    ConfirmLinkScreen.confirmLinkNow(parent, URI.create("https://wiki.tutla.net/ctml"));
});
```

## Next

[Huds](/ctml/tutorial/huds) draws the same tree over the game, where none of this page applies.
