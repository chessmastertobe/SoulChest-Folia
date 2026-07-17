<div align="center">

# SoulChest-Folia

**Strict Folia-only** death chest plugin for Folia 1.21+

This is a maintained fork focused on proper, strict Folia support using region schedulers (no reflection).

> Original project: [trynafindbhumik/SoulChest](https://github.com/trynafindbhumik/SoulChest)

</div>

---

## ✨ Features

- Automatically spawns a protected chest when a player dies
- Preserves full inventory, armor, off-hand items, and XP
- Clean and easy-to-use GUI for managing all your chests
- Per-chest actions: Teleport, Fetch to player, Lock/Unlock, Delete, and Preview contents
- Holograms above chests showing owner name, cause of death, and time remaining
- Chest protection system (other players cannot loot locked chests)
- Safe location finder (avoids lava and void deaths)
- Optional PvP support
- Player toggle system (`/sc toggle`)
- Permission-based chest limits with stacking support
- Full set of admin tools
- SQLite database with WAL mode for performance
- Fully configurable using MiniMessage formatting
- Custom symbols support for holograms

---

## 🧩 Compatibility

| Requirement     | Version           |
|-----------------|-------------------|
| **Server**      | **Folia 1.21.11+** |
| **Java**        | 21 or higher      |

> This plugin is built as a **strict Folia** plugin. It uses region schedulers directly and is not designed for older Paper versions without threaded regions support.

---

## 📥 Installation

1. Download the latest `.jar` file.
2. Place it in your server's `plugins/` folder.
3. Start your Folia server.
4. Edit `plugins/SoulChest/config.yml` to your liking.
5. Use `/sc reload` to apply changes.

---

## 📋 Commands

| Command                          | Description                              | Permission            |
|----------------------------------|------------------------------------------|-----------------------|
| `/sc chests`                     | Open your soul chest GUI                 | `soulchest.gui`       |
| `/sc chests <player>`            | View another player's chests (Admin)     | `soulchest.admin`     |
| `/sc tp <id>`                    | Teleport to one of your chests           | `soulchest.tp`        |
| `/sc fetch <id>`                 | Pull a chest to your current location    | `soulchest.fetch`     |
| `/sc unlock <id>`                | Unlock a chest so anyone can open it     | `soulchest.unlock`    |
| `/sc delete <id>`                | Delete one of your chests                | `soulchest.use`       |
| `/sc toggle`                     | Toggle SoulChest spawning on/off         | `soulchest.toggle`    |
| `/sc version`                    | Show the plugin version                  | `soulchest.version`   |
| `/sc reload`                     | Reload the plugin configuration          | `soulchest.reload`    |
| `/sc admin delete <player> <id>` | Delete a player's chest                  | `soulchest.admin`     |
| `/sc admin tp <player> <id>`     | Teleport to another player's chest       | `soulchest.admin`     |
| `/sc admin view <player> <id>`   | Preview another player's chest contents  | `soulchest.admin`     |
| `/sc admin setlimit <player> <N>`| Set a custom chest limit for a player    | `soulchest.admin`     |
| `/sc admin resetlimit <player>`  | Reset a player's chest limit to default  | `soulchest.admin`     |

---

## 🔑 Permissions

    # Basic permissions (default: true unless noted)
    soulchest.use
    soulchest.gui
    soulchest.tp
    soulchest.fetch
    soulchest.unlock
    soulchest.toggle
    soulchest.protect
    soulchest.version

    # Admin / special permissions
    soulchest.protect.ignore          # Bypass protection (default: op)
    soulchest.reload                  # Reload configuration (default: op)
    soulchest.admin                   # Full admin access (default: op)

    # Chest limit permissions (use the highest matching one)
    soulchest.limit.1
    soulchest.limit.5
    soulchest.limit.10
    soulchest.limit.15
    soulchest.limit.20
    soulchest.limit.50
    soulchest.limit.unlimited

---

## ⚙️ Configuration

The plugin is highly configurable via `config.yml`. All messages support **MiniMessage** formatting.

Main sections:
- `general` — Core settings (duration, XP, safe location search, effects, etc.)
- `protection` — Chest protection rules
- `hologram` — Hologram appearance and text
- `gui` — GUI titles and filler materials
- `messages` — Customizable in-game messages

---

## 🔨 Building from Source

    git clone https://github.com/chessmastertobe/SoulChest-Folia.git
    cd SoulChest-Folia
    mvn clean package

The final jar will be located in the `target/` folder.

---

## 📜 License

This project is licensed under the **MIT License**.  
See the [LICENSE](LICENSE) file for the full license text.

---

<div align="center">

**Made with ❤️ for the Folia community**

</div>