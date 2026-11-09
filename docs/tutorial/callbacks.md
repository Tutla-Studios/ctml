---
title: "Callbacks"
summary: "Name a function in the markup, register it on the context, and hear what the user does to an element."
created: "2026-11-01"
updated: "2026-11-09"
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
| `INPUT` | A press on a button, or enter or space while it has the focus; or an input, textarea or select changing |
| `HOVER` | The cursor has just come onto the element |
| `DRAG` | A draggable element is being moved |

`getCallbackType()` says which, and `getKeycode()` says what key set an `INPUT` off, or `NO_KEY`
where the mouse did.

And what nothing is told about: a release, a wheel, a key other than enter and space, an element
being scrolled, and the cursor *leaving*. `HOVER` is only told about the way in.

### On a server

Callbacks are registered and written exactly the same way. They are just reached differently, because
a server has no mouse or keyboard:
- **The cursor** is wherever the player is looking on the window or hologram. Arriving over an element
  is a `HOVER`.
- **A left click** (attack) is `LEFT_CLICK`, or `INPUT` on a button. **A right click** (use) is
  `RIGHT_CLICK`. There is no middle click.
- **Keys** never arrive, so `INPUT` from enter or space never happens, and `getKeycode()` is always
  `NO_KEY`.

Your context is made for one player, so a callback can use that player directly. On a hologram,
which everyone shares, `CraftytmlPaper.holograms().get(id).presser()` is whoever clicked it.

Without java, a callback can run a server command instead:
- `/ctml callback put <name> <callback> <command>`
- or the json in front of `/ctml execute` and `/ctml hologram create`

`{player}` in the command is whoever pressed it.

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

On a server, a drag takes two clicks, because the server never hears a release: one click picks the box
up, it follows where the player looks, and the next click puts it down. `DRAG` fires all the while it
moves. Holograms can't be dragged.

## Buttons are plain boxes

A `<button>` is a box that can be pressed. It is inline-block, centres its text, and is in the tab
order without saying so. Nothing disables it, and it has no pressed or hovered look of its own, so
you write one with `hover:` and `active:` like on any other box.

A `callback` works on any element, not only buttons. What a button adds is the `INPUT` type and
keyboard reach.

## Inputs

`<input>`, `<textarea>` and `<select>` are the things a user fills in. They send an `INPUT` to the
callback they name each time they change: every key typed into a text field, a tick, a chosen option,
a step of a number, a drag of a slider. The callback does not get the new value handed to it; it reads
it off the element, because the element keeps its state in its own properties:

```java
ctx.putCallBack("nameChanged", callback -> {
    if (callback.getCallbackType() != CTMLCallback.CallbackType.INPUT) return; // not the cursor arriving

    DOMTreeElement field = callback.getElement();
    String typed = (String) field.getPropertyValue("value");   // text, number, slider or chosen option
    Object ticked = field.getPropertyValue("checked");         // checkbox and radio: true, or null if not
});
```

A textarea can say far more about itself. Cast its element and ask:

```java
if (field.getElement() instanceof TextArea area) {
    TextArea.Details d = area.details(); // text, length, lines, words, caret, row, column,
                                         // longestLine, remaining, empty, valid, disabled, readOnly
    area.lines();  area.lineText(2);  area.wordCount();  area.row();  area.column();
}
```

Rows and columns in `Details` count from one, and the single-value methods count from zero. `caret-sky-300` and
`placeholder-slate-500` colour its caret and its hint, and ctrl with the arrows, backspace and delete works a word
at a time.

Writing the property from java does the same in the other direction: `field.property("value", "hi", null)`
puts the text in the box. A press on one of these is taken by the control and is not also told to the
callback as a click, so a change is heard once. A `<input type="button">` is the exception, being a
button: pressing it sends the `INPUT` and its `value` is its label.

```html
<input type="text" placeholder="name" maxlength=16 callback="nameChanged">
<input type="checkbox" checked callback="optionChanged">
<input type="range" min=0 max=100 step=5 value=40 callback="volumeChanged">
<select callback="fruitChanged">
  <option value="a" text="Apple"></option>
  <option value="b" selected text="Bee"></option>
</select>
```

What is there: `text`, `password`, `email`, `search`, `url`, `tel`, `number`, `checkbox`, `radio`,
`range`, `hidden`, `button`, `submit`, `reset`, `date` and `color` for `type` (the last two are plain buttons), plus `textarea`, `select` and `option`. A type it does not
know is read as `text`, with a warning. Radio buttons share a group by `name`. A number takes digits,
a minus and a point, and up and down step it. `required`, `minlength`, `maxlength`, `pattern`, `min` and `max` are checked by `isValid()` on the element, which a host can ask; nothing is refused on account of them. `autofocus`, `size`, `rows`, `cols`, and a `label` and `disabled` on an `<option>` all work. Every field starts as a small dark box you dress with
classes.

Text fields select and edit the way an editor does: shift with the arrows, home and end (and ctrl for
words), or a drag, or shift and a press, or ctrl+A. Two quick presses select a word and three a line.
Ctrl+C, X and V copy, cut and paste (a password field copies nothing), ctrl+Z undoes and ctrl+Y or
ctrl+shift+Z redoes, and typing or pasting replaces the selection. `selection-sky-500/50` colours the
highlight, and a textarea's `details()` carries `selectionStart`, `selectionEnd` and `selectedText`.

A `date` holds `yyyy-mm-dd` and shows it in a box. A press opens a calendar under it: arrows for the
month and the year, a day to pick, and `today` and `clear` along the bottom. With the keyboard, enter opens
it, the arrows walk days and weeks, page up and down walk months and enter picks; closed, up and down step
the date and delete clears it. A `color` holds `#rrggbb` and shows a swatch. A press opens a picker with four
ways to choose, switched by the tabs along its top: `wheel` (a hue ring with a saturation and brightness
square inside), `box` (a saturation and brightness box over a hue bar), `bars` (sliders for red, green, blue,
hue, saturation and brightness) and `swatch` (a grid of ready made colours). Everything but the swatches
can be dragged, each change calls the callback, and hex typed while it is open is applied with enter. Left
and right (or page up and down) switch tabs. Add `alpha` to a colour and its value becomes `#rrggbbaa`: the picker gets an opacity bar under whichever
tab is showing, the swatch is drawn over a chequerboard, and `alpha()` and `argb()` on `Input` give the opacity
and the colour with it. Without `alpha` it stays `#rrggbb` and opaque. Neither takes `min`, `max` or `step`, and both are read off the
element as `value`, with `date()` and `rgb()` on `Input` for java.

On a server, nothing can be typed straight into a field, because the server never sees the keys:
- **Checkboxes, radios and sliders** answer a click on their own.
- **Text fields, textareas, numbers, dates, colours and selects** open the whole form as one of
  minecraft's own dialogs when clicked. Done writes every value back and fires each changed control's
  `INPUT`, the same as typing on a client.
- **Spigot and Bukkit** have no dialogs, so only the click-only controls work there.

What is not: wrapping in a textarea (a long line scrolls sideways),
`<label>`, forms and `submit`, and the types that need a picker of their own (`time`, `month`, `week`, `datetime-local`, `file`).
`min`, `max` and `step` are whole numbers. The rest of html's input attributes (`autocomplete`, `inputmode`, `accept`, `capture`, `alt`, `list`, `form`, `formaction`, `formenctype`, `formmethod`, `formtarget`, `formnovalidate`, `dirname`, `wrap`, `title`, `multiple`, `spellcheck`) are accepted and can be read back, but nothing acts on them. A `<select>` opens its list under itself and draws it over
everything else; the list is not styled by classes.

## No links

There is no `<a>`: a link has nowhere to go. To open a url, put a button on it and call minecraft's
own confirmation screen from the callback:

```java
ctx.putCallBack("openWiki", callback -> {
    if (!callback.isPress()) return;
    ConfirmLinkScreen.confirmLinkNow(parent, URI.create("https://wiki.tutla.net/ctml"));
});
```

A server can't open anything on the client, so send the link to the player's chat to click instead, as
the renderers' `openLink` does:

```java
TextComponent link = new TextComponent("https://wiki.tutla.net/ctml");
link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://wiki.tutla.net/ctml"));
player.spigot().sendMessage(link);
```

## Next

[Huds](/ctml/tutorial/huds) draws the same tree over the game, where none of this page applies.
