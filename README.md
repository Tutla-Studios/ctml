# CraftyTML
A Minecraft Mod that lets you build Minecraft UI with HTML
---

CraftyTML is a Fabric Minecraft Mod that lets you write screens and huds as markup/html instead of java. Elements are written like html, styled with tailwind-like classes, and powered by [Tusan](https://wiki.tutla.net/tusan) interpreter that powers [Tums](https://wiki.tutla.net/tums). Made for developers to avoid mixins, messy gui code and other gui issues.

📖 **Full documentation @ [Tutla Wiki](https://wiki.tutla.net/ctml)**

## How to install (for Players)

1. Download the latest version from [Modrinth](https://modrinth.com/mod/ctml)
2. Drag and drop the downloaded `.jar` file into your `mods` folder
3. Start Minecraft

## For developers

### How to install

To get started, follow these steps:

1. Download the mod and put it in `[PROJECT FOLDER]/libs/`
```yaml
├── build
├── build.gradle
├── gradle
├── gradle.properties
├── gradlew
├── gradlew.bat
├── libs
│   └── ctml-0.1.0.jar <-- here!
├── LICENSE
├── README.md
├── settings.gradle
└── src
    ├── client
    └── main
```
2.. Add the mod as a dependancy in `build.gradle` under the `dependencies` section.
```cpp
dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"

    implementation "net.fabricmc:fabric-loader:${project.loader_version}"

    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"
    implementation files("libs/[MOD FILENAME].jar") // <---- HERE!
}
```
3. Import `CTMLAPI` and start developing!

Read the full docs in the [CTML API documentation](https://wiki.tutla.net/ctml/).


### How to write a UI?

Create a new file with a `.ctml` extension in `[YOUR MINECRAFT FOLDER]/ctml/ui/`, or you can write it within your mod and then register it directly using the `CTMLAPI`.

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
| 🧱 **Elements** | `ctml`, `div`, `span`, `img`, `button`, the table tags (`table`, `thead`, `tbody`, `tfoot`, `caption`, `tr`, `th`, `td`) and the markdown set (`h1`–`h6`, `p`, `ul`, `ol`, `li`, `blockquote`, `pre`, `code`, `hr`, `br`, `b`, `strong`, `i`, `em`, `s`, `del`, `u`, `ins`) |
| 🏷️ **Properties** | `class`, `text`, `id`, `callback`, `draggable`, `tabindex`, `src`, `colspan`, `rowspan`, `start`, `reversed`, `value`, `scrollx`, `scrolly`, `scrollinto` |
| 🎨 **Layout** | block flow, inline text, flexbox, grid, floats, multi-column, tables, sticky and fixed, z-index |
| 🖌️ **Paint** | colours and `/50` alphas, borders and radii, gradients, background images, opacity, text sizes, weights, alignment and shadow |
| 📜 **Overflow** | scrollports, scrollbars you can style, the wheel, the keys, smooth scrolling, scroll anchoring |
| ✨ **Variants** | `hover:`, `focus:`, `focus-within:`, `active:`, `dark:`, `first:`, `last:`, `odd:`, `even:`, `only:` and the `sm:` to `2xl:` breakpoints |

### Screens, Huds & Callbacks

The same file is both. Nothing in the markup says which it is, whichever class you build it with decides:

- **`CTMLScreen`** opens it as a screen. The mouse and the keyboard reach it, so callbacks fire, boxes drag, scrollbars can be grabbed and `hover:` and other variants work.
- **`CTMLHud`** draws it over the game as a hud. Everything a script drives still works like variables, class lists, scrolling, but a hud hears nothing from the user, so callbacks never fire.


#### Callbacks

These are event listeners that let developers recieve events from elements in the DOM.

```java
ctx.putCallBack("sayHi", callback -> {
    if (!callback.isPress()) return;
    callback.getCtx().getElementById("greeting").property("text", "hi!", null);
});
```

### Demo

```java
// a tree out of the ui folder or from a written script, and the context its variables and callbacks live in
CTMLAPI.createUI("myui.ctml", uiPath);
DOMTreeElement tree = UIRegistry.getUIFromName("myui.ctml");
DOMContext ctx = new DOMContext(tree);

// or run a file of your own from anywhere: CTMLAPI.runScript(path, tusan, environment)

// a variable: every ${cookies} in the markup says this, from the next frame on
ctx.putVariable("cookies", 12);

// a callback, by the name the markup wrote in its callback property
ctx.putCallBack("sayHi", callback -> {
    if (!callback.isPress()) return;
    callback.getCtx().getElementById("greeting").property("text", "hi!", null);
});

// and then either of the two ways to show it
minecraft.setScreenAndShow(new CTMLScreen("myui", tree, parent, ctx));
HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("mymod", "myui"), new CTMLHud(tree, ctx));
```

## For Tusan developers

CraftyTML, Tums & Tusan are fully open source: anything that can be done here, your mod can do to it too.

`CTMLAPI` runs and registers scripts, and `CTMLRenderer.layout` and `CTMLRenderer.paint` are public as well, so you can lay a tree out and draw it inside a gui of your own.

The layout engine is pure arithmetic over a dom tree, so all of it can be checked without a running game; refer [`tools/headless`](https://github.com/Tutla-Studios/ctml/tree/master/tools/headless/README.md).

Read the full developer docs in the [CraftyTML API documentation](https://wiki.tutla.net/ctml/developer/).

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
