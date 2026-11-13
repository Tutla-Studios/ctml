# CraftyTML

CraftyTML is a Minecraft mod for Fabric and a plugin for Paper, Spigot and Bukkit that lets you write screens and huds as markup/html instead of java.

![intro screen](https://cdn.modrinth.com/data/y5uBAABM/images/904230d2ff6219d10e03adc17ba50cb2e8a73c7d.png)

It work on both client and server. Elements are written like html, styled with tailwind-like classes, and powered by [Tusan](https://wiki.tutla.net/tusan) interpreter that powers [Tums](https://modrinth.com/mod/tums). Made for developers to avoid mixins, messy gui code and other gui issues.

#### UI Types
- **Screen (Client):** A Custom Minecraft Screen, for Minecraft Clients (Fabric)
- **HUD:** A Minecraft HUD, for Minecraft Clients
- **Window (Server):** A 3d window embedded into the world using DisplayEntities on Spigot/Paper.
- **Holograms:** A 3d UI view accessible to all players and interactable. Meant for server developers.

📖 **Full documentation @ [Tutla Wiki](https://wiki.tutla.net/ctml)**

## How to install (for Players)

1. Download the latest version from [Modrinth](https://modrinth.com/mod/ctml), and [Tums](https://modrinth.com/mod/tums)
2. Drag and drop the downloaded `.jar` file into your `mods` folder
3. Start Minecraft

## How to install (for Servers)

1. Download the latest version from [Modrinth](https://modrinth.com/mod/ctml), and [Tums](https://modrinth.com/mod/tums)
2. Drop both `.jar` files into your server's `plugins` folder (Paper, Spigot or Bukkit)
3. Start the server, and put your `.ctml` files in `plugins/CraftyTML/ui/`

To check it works, run `/ctml demo` in game for a window, or `/ctml hologram demo` for a hologram.

## For Servers

Every command needs the `craftytml.use` permission, which ops have. Where a player is optional, it is whoever runs the command. Every one is explained in full in the [commands reference](https://wiki.tutla.net/ctml/reference/commands), and in game by `/ctml help <command>`.

#### Holograms
![Holograms](https://cdn.modrinth.com/data/y5uBAABM/images/9824f7e83670a111375a62a0ab42fde4ec832495_350.webp)

Holograms are UIs that float in your world. Dedicated for Spigot/Paper using DisplayEntities.


#### Command List

| Command | What it does |
| --- | --- |
| `/ctml open <name> [player]` | Shows a ui from the ui folder |
| `/ctml run <script> [player]` | Runs a script from the ui folder again, as a ui of that player's own |
| `/ctml execute [targets] [name] [json] <ctml>` | Shows ctml written into the command itself. `targets` is a player or a selector like `@a`, the name defaults to `default`, and the json maps callbacks to commands: `{"buy":"give {player} diamond 1"}` |
| `/ctml variable list\|put\|remove [name] [variable] [value]` | Lists, sets or clears the variables of an executed ui or a hologram, on every window of it straight away |
| `/ctml callback list\|put\|remove [name] [callback] [command]` | Lists, sets or clears the callback commands of an executed ui or a hologram, on every window of it straight away |
| `/ctml hologram create <id> [x y z] [yaw] [--options] [json] <ctml>` | Stands ctml in the world for everyone to see and click, at a place (`~` works) facing a yaw. `--pitch=` and `--roll=` turn it, `--face=camera` or `--face=vertical` turn it to face each viewer, and `--per-player` gives every player a ui of their own. `open <id> <ui>` does the same with a registered ui, `demo` puts up the intro, and `move`, `remove` and `list` manage them. Players looking at one get a cursor and can click, drag and scroll it |
| `/ctml demo [player]` | Shows the intro and the demo |
| `/ctml close [player]` | Closes a player's window |
| `/ctml list [registered\|running]` | Names the registered uis, the windows open right now and who has them open, or both |
| `/ctml help [command]` | Lists every command, or explains one |

A player works a window or a hologram by looking at it and clicking. The look moves a cursor, the hotbar scrolls, a draggable box or a scrollbar is picked up with one click and put down with the next, a click focuses what it lands on, and typing into a form opens one of minecraft's own dialogs on Paper. Sneaking closes a window.

## For Developers

### How to install (Fabric)

To get started, follow these steps:

1. Download the jar and put it in `[PROJECT FOLDER]/libs/`
```yaml
├── build
├── build.gradle
├── gradle
├── gradle.properties
├── gradlew
├── gradlew.bat
├── libs
│   └── craftytml-0.1.0.jar <-- here!
├── LICENSE
├── README.md
├── settings.gradle
└── src
├── client
└── main
```
2. Add the mod as a dependancy in `build.gradle` under the `dependencies` section.
```cpp
dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"

    implementation "net.fabricmc:fabric-loader:${project.loader_version}"

    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"
    implementation files("libs/[MOD FILENAME].jar") // <---- HERE!
}
```
3. Import `CTMLAPI` and start developing!

### How to install (Paper / Spigot / Bukkit)

The same jar is the plugin. Compile against it and the server's api, and load after it:

`build.gradle`
```groovy
dependencies {
    compileOnly "io.papermc.paper:paper-api:26.3.build.245-stable"
    compileOnly files("libs/craftytml-0.1.0.jar")
}
```

`plugin.yml`
```yaml
# plugin.yml
depend: [CraftyTML]
```

There is no folder to point at: CraftyTML runs every script in `plugins/CraftyTML/ui/` into the `UIRegistry` as it starts, you can write your own markup using the API.

Read the full docs in the [CTML documentation](https://wiki.tutla.net/ctml/), starting with [Getting started](https://wiki.tutla.net/ctml/getting-started).


## How to write a UI?

Create a new file with a `.ctml` extension in `[YOUR MINECRAFT FOLDER]/ctml/ui/` on a client or `plugins/CraftyTML/ui/` on a server, *or write it within your mod or plugin and register it directly using the `CTMLAPI`.*

```html
<ctml class="w-full h-full flex flex-col items-center justify-center gap-2 bg-slate-900 text-slate-100">

    <div class="text-2xl font-bold text-amber-400" text="Hello, World!"></div>

    <button class="px-2 py-1 bg-sky-600 rounded hover:bg-sky-500 active:bg-sky-700"
            callback="sayHi" text="press me"></button>

</ctml>
```

A string anywhere in the markup can name a variable with `${name}`, and it is read again every frame it changes.

```html
<div class="text-xs ${fpsMood}" text="fps ${fps}"></div>
```

### What you can write

| | |
| --- | --- |
| 🧱 **Elements** | `ctml`, `div`, `span`, `img`, `button`, the inputs (`input`, `textarea`, `select`, `option`), the table tags (`table`, `thead`, `tbody`, `tfoot`, `caption`, `tr`, `th`, `td`) and the markdown set (`h1`–`h6`, `p`, `ul`, `ol`, `li`, `blockquote`, `pre`, `code`, `hr`, `br`, `b`, `strong`, `i`, `em`, `s`, `del`, `u`, `ins`) |
| 🏷️ **Properties** | `class`, `text`, `id`, `callback`, `draggable`, `tabindex`, `src`, `colspan`, `rowspan`, `start`, `reversed`, `value`, `scrollx`, `scrolly`, `scrollinto` |
| 🎨 **Layout** | block flow, inline text, flexbox, grid, floats, multi-column, tables, sticky and fixed, z-index |
| 🖌️ **Paint** | colours and `/50` alphas, borders and radii, gradients, background images, opacity, text sizes, weights, alignment, text shadow and box shadows |
| 📜 **Overflow** | scrollports, scrollbars you can style, the wheel, the keys, smooth scrolling, scroll anchoring |
| ✨ **Variants** | `hover:`, `focus:`, `focus-within:`, `active:`, `dark:`, `first:`, `last:`, `odd:`, `even:`, `only:` and the `sm:` to `2xl:` breakpoints |

### Screens, Huds & Callbacks

The same file is both. Nothing in the markup says which it is, whichever class you build it with decides:

- **`CTMLScreen`** opens it as a screen. The mouse and the keyboard reach it, so callbacks fire, boxes drag, scrollbars can be grabbed and `hover:` and other variants work.
- **`CTMLHud`** draws it over the game as a hud. Everything a script drives still works like variables, class lists, scrolling, but a hud hears nothing from the user, so callbacks never fire.
- **`WindowRenderer`** shows it on a server, as a window in front of one player, who is held still until it closes. Callbacks, variables, drags, scrolling and `hover:` work the same as in a screen, and forms open a dialog on Paper.
- **`HologramRenderer`** stands it in the world on a server, at any place, for everyone to see, fixed at a yaw, pitch and roll or turning to face each viewer. Everyone looking at it gets a cursor of their own and can click, drag and scroll it, and `hover:` follows the nearest of them. A per-player hologram gives every player near it a ui of their own instead. Holograms are gone after a restart.

A window (server) or a hologram is drawn out of display entities, so rounded corners, shadows, images and letter-spacing are left out there, and gradients are drawn in bands.

Everything before showing a tree, building it with `CTMLAPI`, its context, its variables and callbacks, is the same code on a client and on a server. Only the renderer at the end is different.


#### Callbacks

These are event listeners that let developers recieve events from elements in the DOM.

```java
ctx.putCallBack("sayHi", callback -> {
        if (!callback.isPress()) return;
        callback.getCtx().getElementById("greeting").property("text", "hi!", null);
});
```

### Demo

1. Create the UI
```java
// a tree out of the ui folder or from a written script, and the context its variables and callbacks live in
CTMLAPI.createUI(uiPath);               // a file of your own, from anywhere
CTMLAPI.createUI("myui.ctml", markup);  // or markup you wrote in java, under a name you pick
DOMTreeElement tree = UIRegistry.getUIFromName("myui.ctml");
```
2. Create Context
```java
DOMContext ctx = new DOMContext(tree);
```
3. Add variables/callback (optional)
```java
// a variable: every ${cookies} in the markup says this, from the next frame on
ctx.putVariable("cookies", 12);

// a callback, by the name the markup wrote in its callback property
ctx.putCallBack("sayHi", callback -> {
        if (!callback.isPress()) return;
        callback.getCtx().getElementById("greeting").property("text", "hi!", null);
});
```
4. Render it

As a Screen (Fabric, Client-Side)
```java
// and then show it: on a client, as a screen
minecraft.setScreenAndShow(new CTMLScreen("myui", tree, parent, ctx));
```

As a HUD (Fabric, Client-Side)
```java
HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("mymod", "myui"), new CTMLHud(tree, ctx));
```

On server
```java
// on a server, a tree of one player's own: built afresh, never registered, with the player in its scope
        DOMTreeElement theirs = CTMLAPI.buildUI(markup, Map.of("player", player));

// or on a server, in front of one player: straight ahead, or anywhere around them
        new WindowRenderer(player).show("myui", tree, ctx);
        new WindowRenderer(player).show("myui", tree, ctx, new CTMLWindow.Position(-1.5, 0)); // off to their left
```

On server, as hologram
```java
// or in the world for everyone, its middle at a location and its front facing the way the location looks
        new HologramRenderer(location).show("myui", tree, ctx);
```

## For Tusan developers

CraftyTML, Tums & Tusan are fully open source: anything that can be done here, your mod can do to it too.

`CTMLAPI` runs and registers scripts, and `CTMLRenderer.layout` and `CTMLRenderer.paint` are public as well, so you can lay a tree out and draw it inside a gui of your own. Anything that shows trees, the demos included, can take a `UIRenderer` and run unchanged on both platforms: `ScreenRenderer` on a client, `WindowRenderer` or `HologramRenderer` on a server.

The layout engine is pure arithmetic over a dom tree, so all of it can be checked without a running game; refer [`tools/headless`](https://github.com/Tutla-Studios/ctml/tree/master/tools/headless/README.md).

### Building it yourself

The project is an Architectury build in three modules under `src/`:

| Module | What is in it |
| --- | --- |
| `common` | Everything that is the same everywhere: the ctml language, the dom, layout, `CTMLAPI`, `UIRegistry`, `UIRenderer` and the demos |
| `fabric` | The client mod: screens, huds and painting with minecraft's gui |
| `paper` | The server plugin: windows, holograms, dialogs and the `/ctml` command |

- `./gradlew build` builds one universal jar, `build/libs/craftytml-<version>.jar`, which loads as a Fabric mod and as a Paper, Spigot or Bukkit plugin.
- `./gradlew :fabric:runClient` starts a dev client with `run/ctml/ui` as its ui folder.
- `./gradlew :paper:runServer` starts a Paper test server with Tums beside it, `run/ctml/ui` is loaded on each start.
- `./docs/generate.sh` writes the reference pages, the docs' front page from this README included, from the source. `--check` fails where they have fallen behind.

Read the full developer docs in the [CraftyTML reference](https://wiki.tutla.net/ctml/reference/), including the [java API](https://wiki.tutla.net/ctml/reference/java) and the [commands](https://wiki.tutla.net/ctml/reference/commands).

---

## 💬 Support, Tracking & More

- 💬 Join our [**Discord**](https://discord.tutla.net) for sneak peeks & community support
- ⭐ Star us on [**GitHub**](https://github.com/Tutla-Studios/ctml)
- 🌐 Visit our [**Website**](https://tutla.net) for updates
- 💙 Contribute by [**donating**](https://donatr.ee/tutlamc?utm_source=copy&utm_medium=share)

Think we’re missing something? **Contribute on [GitHub](https://github.com/Tutla-Studios/ctml)**

---

## 🧪 Other Tutla Studio Projects

- 🍬 [**Tums**](https://modrinth.com/mod/tums) – A Minecraft Mod that makes Minecraft Modding simpler
- ⚔️ [**PvP+**](https://modrinth.com/plugin/pvp+plugin) – A Comprehensive PvP Plugin for Paper servers
- 🛡️ [**LeaveBind!**](https://modrinth.com/mod/leavebind) – Leave the server with a click of a button
- 🧾 [**Leave This, GoBack!**](https://modrinth.com/mod/leave-this-go-back) – Pressing on a slot twice returns you back to the previous slot. Useful for attribute swapping / quick tool swapping / mace pvp
