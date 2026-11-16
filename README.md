# Highway Builder

![Highway Builder icon](src/main/resources/assets/highwaybuilder/icon.png)

> ## ⚠️ Disclaimer – read before using
>
> **Highway Builder is a client-side automation mod. Many public servers forbid automation, "utility" or "hacked" clients, and their anti-cheat may detect it.**
>
> - **You use this mod entirely at your own risk.**
> - **The author is not responsible for any bans, kicks, mutes, account restrictions, lost items, deaths, or any other consequences of using this mod on public or private servers.**
> - Always read and follow the rules of the server you play on. If a server does not allow automation, don't use this mod there.
> - The mod is provided "as is", without warranty of any kind (see [LICENSE](LICENSE)).
>
> By downloading or using Highway Builder you agree that you alone are responsible for how you use it.

---

A standalone **Fabric** mod for **Minecraft 1.21.11** that builds highways for you: it clears a tunnel, paves the floor, places railings on both sides and walks forward on its own. It works in all **4 straight** directions **and all 4 diagonal** directions.

No other client is needed. All you need is Fabric Loader and Fabric API.

## Features

- Clears a tunnel of any width (1–8) and height (2–6)
- Paves the floor: either **replace** every floor block that isn't your building block, or **only fill holes**
- Railings on both sides, optionally with the space above them cleared too, with or without a support block underneath (without one, railings are air placed)
- **Diagonal highways** (North-East, South-East, South-West, North-West) built as a clean staircase pattern
- Fills lava and water inside the tunnel before digging through it
- Picks the best tool automatically and never uses a tool that is about to break (optional)
- **Mines ender chests for obsidian** when you run out, and keeps a number of ender chests you choose
- Throws trash items (netherrack, cobblestone, …) away when your inventory is full
- Hits end crystals next to you (crystal-trap protection)
- Pauses while you eat or have a menu open
- Stops safely if you fall off the highway or get stuck
- Status line above your hotbar: direction, blocks left, what it is doing
- **Settings menu** (Right Shift or `/highway gui`) with on/off buttons and sliders; everything is saved to `config/highwaybuilder.json`

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft **1.21.11**.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.11 in your `mods` folder.
3. Put `highway-builder-1.0.0.jar` in your `mods` folder.
4. Start the game.

## How to use

1. Stand where the highway should start, at the height the highway should be at (your feet are on top of the floor).
2. Look roughly in the direction you want to build.
3. Press **Right Shift** (or type `/highway gui`) to open the settings menu and click **Start building**, or just type **`/highway`**.

The direction snaps to the nearest straight direction by default. For diagonal highways, set `direction` to `diagonal` (always a diagonal) or `auto` (nearest of all 8 directions) first.

Have a pickaxe and your building blocks (obsidian by default) in your inventory. Click **Stop building** in the menu, or type `/highway` again, to stop.

### Settings menu

Open it with **Right Shift** or **`/highway gui`**. Settings are split into the tabs *General*, *Interaction*, *Inventory* and *Misc*:

- **On/off settings** are buttons: click to switch between ON and OFF.
- **Choices** (like `direction` or `floor`) are buttons: click to go to the next option.
- **Numbers** are sliders.
- **Block/item lists** open a small editor where you type ids separated by commas, e.g. `obsidian, crying_obsidian`.

Hover over any button to see what it does. Changes are saved right away. **Reset tab** puts the current tab back to its defaults.

### Key binds

Both keys can be changed under **Options → Controls → Key Binds → Highway Builder**.

| Key bind | Default |
|---|---|
| Open Settings | Right Shift |
| Start / Stop Building | not bound |

### Commands

Everything in the menu can also be done with commands:

| Command | What it does |
|---|---|
| `/highway` | Turn the builder on / off |
| `/highway gui` | Open the settings menu |
| `/highway on` / `/highway off` | Turn it on or off |
| `/highway settings` | List every setting with its current value |
| `/highway get <setting>` | Show one setting, what it does and which values it accepts |
| `/highway set <setting> <value>` | Change a setting (saved right away) |
| `/highway reset [setting]` | Reset one setting, or all of them |

Example: `/highway set direction diagonal`, `/highway set width 5`

## Settings

### General

| Setting | Default | Description |
|---|---|---|
| `width` | `4` | Width of the walkable lane (railings not included). On diagonal highways the minimum is 2. |
| `height` | `3` | Height of the tunnel above the floor. |
| `floor` | `replace` | `replace`: mine floor blocks that aren't in `blocks-to-place` and pave them. `place-missing`: only fill holes. |
| `railings` | `true` | Place a 1-block railing on both sides. |
| `railing-support` | `true` | Place a block under each railing. When off, nothing is placed under the railings, and railings over air or liquid are **force placed in mid-air** (air place). Air placing is something many anti-cheats detect. |
| `mine-above-railings` | `true` | Also clear the space above the railings. |
| `direction` | `cardinal` | `cardinal` = N/E/S/W, `diagonal` = NE/SE/SW/NW, `auto` = nearest of all 8. Taken from where you look when you start. |
| `auto-walk` | `true` | Walk forward automatically when everything in reach is done. |
| `keep-centered` | `true` | Strafe to stay on the center line. |
| `blocks-to-place` | `obsidian` | Blocks used for the floor and railings (comma separated). |
| `trash-items` | netherrack, cobblestone, … | Items thrown out when the inventory is full (comma separated, or `none`). |

### Interaction

| Setting | Default | Description |
|---|---|---|
| `rotate` | `true` | Look at each block before placing or mining it. |
| `place-range` | `4.5` | Maximum reach for placing. |
| `break-range` | `4.5` | Maximum reach for mining. |
| `place-delay` | `1` | Ticks between placements. |
| `blocks-per-tick` | `1` | Blocks placed per tick. |
| `dont-break-tools` | `true` | Never use a tool at or below `min-durability`. |
| `min-durability` | `15` | Durability at which a tool is no longer used. |
| `pause-while-eating` | `true` | Pause while using an item. |
| `destroy-crystal-traps` | `true` | Hit end crystals within 5 blocks. |

### Inventory

| Setting | Default | Description |
|---|---|---|
| `mine-ender-chests` | `true` | When out of obsidian, place and mine ender chests for more (needs a pickaxe **without** Silk Touch). |
| `save-ender-chests` | `1` | Number of ender chests that are never mined. |
| `refill-target` | `64` | Keep mining ender chests until you have at least this much obsidian. |

### Misc

| Setting | Default | Description |
|---|---|---|
| `status-bar` | `true` | Show the status line above the hotbar. |
| `disable-on-disconnect` | `true` | Turn off when you leave the world. |

## Diagonal highways

A diagonal highway is a staircase of blocks along the 45° line. With `width 4` the lane is 7 diagonal rows wide (about 5 blocks across), with a diagonal railing on each side. The builder walks along the exact diagonal and strafes to stay centered.

## Building from source

Requires **JDK 21**.

```
gradlew build
```

The jar ends up in `build/libs/`.

## Credits

Inspired by the Highway Builder module of [Meteor Client](https://github.com/MeteorDevelopment/meteor-client). This is an independent, standalone implementation and is not affiliated with or endorsed by the Meteor Client developers.

## License

[MIT](LICENSE)
