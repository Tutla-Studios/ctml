---
title: "Getting started"
summary: "Add CraftyTML to a Fabric mod or a Paper plugin, write your first .ctml file and show it."
created: "2026-11-01"
updated: "2026-11-09"
isdoc: true
---

CraftyTML reads html-like markup and draws it as minecraft ui. You write a `.ctml` file, style it
with tailwind-like classes, and show it with one line of java. The same jar is a Fabric client mod and
a Paper, Spigot and Bukkit plugin: on a client a tree opens as a screen or a hud, and on a server as a
window in front of one player or a hologram anyone can see.

This page gets the dependency in place and a screen on the display. The
[tutorial](/ctml/tutorial) then builds something real with it.

## What you need

- A Fabric mod project for minecraft 26.3
- Java 25
- The CraftyTML jar, from [Modrinth](https://modrinth.com/mod/ctml)

The first half of this page sets up a Fabric client mod. Writing a plugin instead? Skip to
[On a server](#on-a-server): everything except the setup and the last line of showing a tree is the same.

## Add it to your project

Put the jar in your project's `libs/` folder:

```text
your-mod/
├── build.gradle
├── gradle.properties
├── libs
│   └── ctml-0.1.0.jar
└── src
```

Then depend on it in `build.gradle`:

```groovy
dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${project.loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"

    implementation files("libs/ctml-0.1.0.jar")
}
```

CraftyTML is a client mod, so everything you write against it belongs in your client source set.

## Write a .ctml file

Markup lives in `ctml/ui/` inside the minecraft folder: `.minecraft/ctml/ui/` for a normal install,
or `run/ctml/ui/` while you are developing. Create `hello.ctml` there:

```html
<ctml class="w-full h-full flex flex-col items-center justify-center gap-2 bg-slate-900 text-slate-100">

    <div id="greeting" class="text-2xl font-bold text-amber-400" text="Hello, World!"></div>

    <button class="px-2 py-1 bg-sky-600 rounded hover:bg-sky-500 active:bg-sky-700"
            callback="sayHi" text="press me"></button>

</ctml>
```

Three things are worth noticing already:

- `<ctml>` is the root. It fills whatever it is drawn in, and everything the tree inherits bottoms
  out in it.
- The classes are tailwind's, with one spacing unit standing for four gui pixels. `px-2` is eight
  pixels of padding left and right.
- Text is a property, not a child. `text="Hello, World!"` is how a box says something.

## Run the scripts

Point CraftyTML at the folder once, on your client initialiser:

```java
public class ExampleModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient(){
        Path ui = FabricLoader.getInstance().getGameDir().resolve("ctml/ui");
        CTMLAPI.runAllScripts(ui);
    }
}
```

Every file in the folder is read and the tree it produces is registered under its file name. Nothing
is shown yet: a tree is just a tree until you build a screen or a hud around it.

## Show it

A screen is opened over another one, so the easiest place to try it is the title screen:

```java
ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
    if (!(screen instanceof TitleScreen)) return;

    DOMTreeElement tree = UIRegistry.getUIFromName("hello.ctml");
    if (tree == null) return;                    // the file is not in ctml/ui

    DOMContext ctx = new DOMContext(tree);
    ctx.putCallBack("sayHi", callback -> {
        if (!callback.isPress()) return;         // the cursor merely arriving is not a press
        callback.getCtx().getElementById("greeting").property("text", "hi!", null);
    });

    CTMLScreen hello = new CTMLScreen("hello.ctml", tree, screen, ctx);
    client.execute(() -> client.setScreenAndShow(hello));
});
```

That is the whole shape of it: a tree, a context for the variables and callbacks that drive it, and a
`CTMLScreen` to open it. The third argument is the screen to go back to when this one closes.

A callback hears about everything that happens to its element, not only the presses, which is why the
first line asks. [Callbacks](/ctml/tutorial/callbacks) goes through the rest of what it is told.

## Where to go next

| I want to... | Go to |
| --- | --- |
| Build a screen step by step | [Tutorial](/ctml/tutorial) |
| Draw over the game instead of over a screen | [Huds](/ctml/tutorial/huds) |
| Look up an element, a property or a class | [Reference](/ctml/reference) |
| Call CraftyTML from java | [Java API](/ctml/reference/java) |

There is no hot reload yet, so a changed `.ctml` file is only read when the game starts. An edit
needs a restart.

## On a server

For a Paper, Spigot or Bukkit plugin, you need:
- a plugin project for minecraft 26.3 on Java 25
- the CraftyTML and Tums jars in the server's `plugins/` folder

Compile against the same jar:

```groovy
repositories {
    maven { url = 'https://repo.papermc.io/repository/maven-public/' }
}

dependencies {
    compileOnly "io.papermc.paper:paper-api:26.3.build.245-stable"
    compileOnly files("libs/craftytml-0.1.0.jar")
}
```

and load after it, in your `plugin.yml`:

```yaml
depend: [CraftyTML]
```

There is nothing to point at a folder. CraftyTML runs every script in `plugins/CraftyTML/ui/` into the
`UIRegistry` as it starts, so put `hello.ctml` there. The markup is exactly the same.

Showing it is the same code as on a client, up to the last line. Where a client builds a `CTMLScreen`,
a server hands the tree to a renderer:

```java
@EventHandler
public void onJoin(PlayerJoinEvent event){
    Player player = event.getPlayer();

    DOMTreeElement tree = UIRegistry.getUIFromName("hello.ctml");
    if (tree == null) return;                    // the file is not in plugins/CraftyTML/ui

    DOMContext ctx = new DOMContext(tree);
    ctx.putCallBack("sayHi", callback -> {
        if (!callback.isPress()) return;
        callback.getCtx().getElementById("greeting").property("text", "hi " + player.getName() + "!", null);
    });

    new WindowRenderer(player).show("hello.ctml", tree, ctx);
}
```

`WindowRenderer` floats the tree in front of that one player, who looks at it to move a cursor and
clicks to press. `new HologramRenderer(location)` stands it in the world for everyone instead. You can
also do all of this without java, using `/ctml open hello.ctml` and the rest of the
[commands](/ctml/reference/commands).

A tree from the registry is one tree, so two players shown it share it. For a tree of their own, build
one per player with `CTMLAPI.buildUI`, which [Paper and Spigot](/ctml/tutorial/paper) covers along with
everything else that differs on a server.
