---
title: "Variables"
summary: "Put a value in the dom context and every ${name} in the markup says it, from the next frame on."
created: "2026-11-01"
updated: "2026-11-01"
isdoc: true
---

The stats panel shows two zeroes. Variables are how it shows real numbers without you touching the
tree.

## Name a variable in the markup

Any string in the markup can name one with `${name}`:

```html
<div class="w-full grid grid-cols-[1fr_auto] gap-x-2">
  <span class="text-slate-400" text="blocks walked"></span>
  <span class="text-right" text="${blocks}"></span>
  <span class="text-slate-400" text="mobs met"></span>
  <span class="text-right ${mobMood}" text="${mobs}"></span>
</div>
```

Two things are being done there. `${blocks}` is a variable inside the text, and `${mobMood}` is a
variable inside a *class list*, so the whole look of that box is something java decides.

## Put the values in

```java
DOMContext ctx = new DOMContext(tree);

ctx.putVariable("blocks", 0);
ctx.putVariable("mobs", 0);
ctx.putVariable("mobMood", "text-slate-100");
```

And change them whenever you like:

```java
ClientTickEvents.END_CLIENT_TICK.register(client -> {
    ctx.putVariable("blocks", walked);
    ctx.putVariable("mobs", met);
    ctx.putVariable("mobMood", met > 20 ? "text-rose-300" : "text-slate-100");
});
```

That is the whole of it. `putVariable` tells the screen as well as storing the value, so every string
naming it is read again and whatever it was showing changes with it. Nothing else has to be called.

Only the elements that carry a `${` are touched, so a variable nothing mentions costs a walk of the
tree and no layout at all.

## What a value turns into

Whatever `toString` says. An `Integer` reads as its digits, a `String` as itself. A variable nothing
has put there yet reads as nothing at all, rather than leaving the braces on show, so a screen opened
before its values arrive is blank in that spot instead of wrong.

`removeVariable` takes one away and leaves every string that named it reading as empty.

To write a literal `${`, double the dollar: `$${` reads as `${` and means it.

## Reading the tree instead

For something you set once, reach into the tree by id and set the property directly:

```java
ctx.getElementById("blocks-value").property("text", "128", null);
```

Either way works. A variable suits a value that changes often and is read in more than one place; a
property suits a one-off change to one element.


## Keeping what the user did

A screen opening or closing keeps nothing unless you ask it to. `rememberState` writes down how far
each box was dragged and how far each scroll container was scrolled, under a key of your choosing,
and `restoreState` puts it back:

```java
public class StatsScreen extends CTMLScreen {
    private final DOMContext ctx;

    public StatsScreen(DOMTreeElement tree, Screen parent, DOMContext ctx){
        super("stats.ctml", tree, parent, ctx);
        this.ctx = ctx;
        ctx.restoreState("stats");
    }

    @Override
    public void removed(){
        ctx.rememberState("stats");
        super.removed();
    }
}
```

`CTMLScreen` is an ordinary `Screen`, so subclassing it is how you get at `removed()`. If you would
rather not subclass, call the two from wherever you open and close the screen yourself.

Both say how many elements they reached. Elements are recognised by their ids, so one your markup
named is found again whatever happens to the tree, while an id handed out automatically lasts only as
long as that tree does. It is kept in memory and written nowhere, so a restart starts the screens
over.

## Next

[Callbacks](/ctml/tutorial/callbacks) is the other direction: the screen telling java something.
