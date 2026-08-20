[![Join the Discord](https://img.shields.io/discord/1487928162636533871?label=Join%20the%20Discord&logo=discord&color=5865F2&logoColor=white)](https://discord.gg/R5NdTVRDpb)

# DungeonRunTracker [DRT]

A Hypixel SkyBlock mod that tracks dungeon runs and loot.

## Screenshots

![DRT Loot Screen](assets/DRT_Loot_Showcase.png)

## Features

- **Run tracker HUD** - displays run count, current floor, and cumulative profit directly on your screen
- **Loot logging** - automatically records every dungeon chest opening, including cost, items, and profit
- **Loot history screen** - browse all past runs filtered by floor, sort by item value, and search loot entries
- **Live pricing** - item prices fetched from the auction house in real time, with fallback to BIN data
- **Skull textures** - item icons resolved from the NEU-repo for accurate in-game appearance
- **Moveable and resizable HUD** - use `/drt move` to drag the tracker, resize it with the scroll wheel, or use `+`, `-`, and `0`
- **HUD visibility toggle** - use `/drt toggle` to hide or show the tracker UI while loot tracking keeps running
- **Clickable HUD mode selector** - click `DRT` on the HUD to cycle visibility modes:
  - `Default` - show in Dungeon Hub and during runs
  - `Global` - show everywhere
  - `DHub` - show only in Dungeon Hub
- **Cleaner reset control** - reset text only appears while your inventory is open
- **Compatibility fixes** - supports Odin Extra Stats end-of-run messages

## Commands

| Command | Description |
|---|---|
| `/drt move` | Open the position screen to drag and resize the HUD |
| `/drt loot` | Open the loot history screen |
| `/drt toggle` | Hide or show the tracker UI without disabling loot tracking |

You can also click the HUD directly:

- Click `DRT` to cycle visibility modes.
- Click `[floor]` to cycle the selected floor.
- Click the profit line to open the loot history screen.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.21.11 or 26.1.2
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Drop the matching `DungeonRunTracker` jar into your `mods/` folder

## Requirements

- Minecraft 1.21.11 or 26.1.2
- Fabric Loader 0.19.2+ for 1.21.11, or 0.19.3+ for 26.1.2
- Fabric API
- Java 21+ for 1.21.11, or Java 25+ for 26.1.2
