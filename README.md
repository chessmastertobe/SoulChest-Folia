<div align="center">

# ⚰ SoulChest

**A feature-rich death chest plugin for Paper 1.21.11+**

[![Paper](https://img.shields.io/badge/Paper-1.21.11+-blue)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

*Never lose your items again. SoulChest saves your inventory in a protected chest when you die.*

</div>

---

## ✨ Features

- **Death Chest System** — Automatically spawns a protected chest when a player dies
- **Full Inventory Preservation** — Saves main inventory, armor, off-hand, and XP
- **Chest GUI** — Browse and manage all your soul chests in one clean interface
- **Per-chest Settings** — Lock/unlock, teleport, fetch, delete, and preview contents
- **Hologram Display** — Floating text above each chest showing owner, death cause, and time remaining
- **XP Storage** — Automatically captures and restores player experience levels
- **Safe Location Finder** — Places chests at the nearest safe spot when dying in void or lava
- **Chest Protection** — Locked chests prevent other players from looting (with admin bypass)
- **PvP Support** — Optional spawning on player-vs-player deaths
- **Toggle System** — Players can enable/disable SoulChest spawning for themselves
- **Stackable Permissions** — Add `soulchest.limit.N` permissions together, or use highest
- **Admin Management** — Full control to view, delete, teleport, and set limits for any player
- **Sound & Particles** — Visual feedback when a chest spawns
- **SQLite Database** — Zero-config, single-file, WAL mode for performance
- **Tab Completion** — All commands suggest chest IDs and player names
- **Fully Configurable** — Every message uses MiniMessage format, fully customizable
- **Custom Symbols** — Choose any emoji or symbol for each chest's hologram

---

## 🧩 Compatibility

| Requirement | Version |
|---|---|
| Server | Paper 1.21.11+ |
| Java | 21 or higher |
| Dependencies | None — SQLite downloaded automatically |

> Works with Java and Bedrock (Geyser) players. The GUI uses single-click so Bedrock players on mobile/console have full access to all features.

---

## 📥 Installation

1. 👉 [Download Latest](https://github.com/trynafindbhumik/SoulChest/releases/latest)
2. Drop it into your server's `plugins/` folder
3. Start the server — SQLite is downloaded automatically
4. Edit `plugins/SoulChest/config.yml` to your liking
5. Run `/sc reload` to apply changes without restarting

---

## 📋 Commands

| Command | Description | Permission |
|---|---|---|
| `/sc chests` | Open your soul chest GUI | `soulchest.gui` |
| `/sc chests <player>` | View another player's chests | `soulchest.admin` |
| `/sc tp <id>` | Teleport to one of your chests | `soulchest.tp` |
| `/sc fetch <id>` | Pull a chest to your location | `soulchest.fetch` |
| `/sc unlock <id>` | Unlock a chest for everyone | `soulchest.unlock` |
| `/sc delete <id>` | Delete one of your chests | `soulchest.use` |
| `/sc toggle` | Toggle SoulChest spawning on/off | `soulchest.toggle` |
| `/sc version` | Show plugin version | `soulchest.version` |
| `/sc reload` | Reload configuration | `soulchest.reload` |
| `/sc admin delete <player> <id>` | Delete a player's chest | `soulchest.admin` |
| `/sc admin tp <player> <id>` | Teleport to a player's chest | `soulchest.admin` |
| `/sc admin view <player> <id>` | Preview chest contents | `soulchest.admin` |
| `/sc admin setlimit <player> <N>` | Set a player's chest limit | `soulchest.admin` |
| `/sc admin resetlimit <player>` | Reset a player's chest limit | `soulchest.admin` |

---

## 🔑 Permissions

```
soulchest.use                     # Basic SoulChest usage (default: true)
soulchest.gui                     # Open the chests GUI (default: true)
soulchest.tp                      # Teleport to your chests (default: true)
soulchest.fetch                   # Fetch chests to your location (default: true)
soulchest.unlock                  # Unlock your chests (default: true)
soulchest.toggle                  # Toggle spawning on/off (default: true)
soulchest.protect                 # Chest protection (default: true)
soulchest.protect.ignore          # Bypass chest protection (default: op)
soulchest.version                 # Check plugin version (default: true)

soulchest.limit.1                 # Allow 1 chest
soulchest.limit.5                 # Allow 5 chests
soulchest.limit.10                # Allow 10 chests
soulchest.limit.15                # Allow 15 chests
soulchest.limit.20                # Allow 20 chests
soulchest.limit.50                # Allow 50 chests
soulchest.limit.unlimited         # Unlimited chests (default: false)

soulchest.admin                   # All admin permissions (default: op)
soulchest.reload                  # Reload configuration (default: op)
soulchest.*                       # All SoulChest permissions
```

### Chest limits explained

```yaml
# In config.yml:
general:
  default-max-chests: 5
```

- The highest matching `soulchest.limit.N` permission is used
- Player has `limit.3` and `limit.5` → allowed **5** chests
- Admin-granted custom limits (`/sc admin setlimit`) override permissions

### LuckPerms example

```bash
# Give VIP rank 10 chests
lp group vip permission set soulchest.limit.10 true

# Give a specific player 20 chest limit
/sc admin setlimit Steve 20
```

---

## ⚙️ Configuration

```yaml
general:
  default-max-chests: 5           # Limit for players without soulchest.limit.N
  chest-duration: 600             # Seconds before chest expires (-1 = never)
  drop-items-on-expire: false     # Drop items when chest expires (vs delete)
  store-xp: true                  # Capture and restore XP on loot
  allow-chest-in-pvp: true        # Spawn chest on PvP deaths
  find-safe-location-in-void: true
  find-safe-location-in-lava: true
  safe-location-search-radius: 10 # Chunks to search for safe location
  spawn-effects: true             # Particles and sound on spawn
  default-symbol: "⚰"            # Default hologram symbol
  show-death-cause: true          # Show death cause in hologram
  prefix: "<dark_gray>[<dark_purple>⚰ <light_purple>SoulChest<dark_gray>]<reset> "

protection:
  auto-unlock-after: -1           # Seconds before auto-unlock (-1 = manual only)
  protect-from-others: true      # Prevent others from opening locked chests
  allow-break-to-loot: false      # Allow breaking chest to loot

hologram:
  enabled: true
  y-offset: 1.5
  lines:
    - "<dark_purple>{symbol} <light_purple>{owner}'s Soul Chest"
    - "<gray>Cause: <white>{cause}"
    - "<gray>Expires in: <yellow>{time_left}"

gui:
  list-title: "<dark_purple>⚰ <light_purple>Your Soul Chests"
  admin-list-title: "<dark_red>⚰ <red>{player}'s Soul Chests"
  manage-title: "<dark_purple>Manage Soul Chest"
```

All chat messages are configurable under `messages:` in `config.yml`. Uses MiniMessage format with placeholders like `{world}`, `{x}`, `{y}`, `{z}`, `{limit}`, `{player}`, `{symbol}`, `{cause}`, `{time_left}`.

---

## 🗄️ Database Schema

```sql
CREATE TABLE soul_chests (
    id              TEXT    PRIMARY KEY,
    owner_uuid      TEXT    NOT NULL,
    owner_name      TEXT    NOT NULL,
    world_name      TEXT    NOT NULL,
    x               INTEGER NOT NULL,
    y               INTEGER NOT NULL,
    z               INTEGER NOT NULL,
    creation_time   INTEGER NOT NULL,
    expiration_time INTEGER NOT NULL,
    locked          INTEGER NOT NULL DEFAULT 1,
    cause           TEXT    NOT NULL DEFAULT 'Unknown',
    symbol          TEXT    NOT NULL DEFAULT '⚰',
    stored_xp       INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE chest_items (
    chest_id   TEXT    NOT NULL,
    slot_type  TEXT    NOT NULL,
    slot_index INTEGER NOT NULL,
    item_data  BLOB,
    PRIMARY KEY (chest_id, slot_type, slot_index),
    FOREIGN KEY (chest_id) REFERENCES soul_chests(id) ON DELETE CASCADE
);

CREATE TABLE player_limits (
    player_uuid TEXT    PRIMARY KEY,
    chest_limit INTEGER NOT NULL
);
```

---

## 🔨 Building from Source

### Requirements
- Java 21+
- Maven 3.8+

```bash
git clone https://github.com/trynafindbhumik/SoulChest.git
cd SoulChest
mvn clean package
# Output: target/SoulChest-1.0.0.jar
```

---

## 📁 Project Structure

```
SoulChest/
├── pom.xml
└── src/main/
    ├── resources/
    │   ├── plugin.yml
    │   └── config.yml
    └── java/com/soulchest/
        ├── SoulChest.java              ← Main plugin class
        ├── api/                         ← API interfaces (future)
        ├── commands/
        │   └── SoulChestCommand.java   ← All /sc commands + tab completion
        ├── gui/
        │   ├── ChestListGUI.java       ← Paginated chest list view
        │   └── ChestManageGUI.java     ← Per-chest management options
        ├── impl/
        │   ├── ChestManager.java       ← Chest spawning, expiry, holograms
        │   └── DataManager.java        ← SQLite database operations
        ├── listeners/
        │   ├── ChestInteractListener.java  ← Chest interaction handling
        │   ├── GUIListener.java            ← GUI click events
        │   └── PlayerDeathListener.java    ← Death event handling
        ├── model/
        │   └── SoulChestData.java      ← Chest data model
        └── util/
            ├── MessageUtils.java        ← MiniMessage parsing
            └── SafeLocationFinder.java  ← Void/lava safe spot finder
```

---

## 📄 License

This project is licensed under the **MIT License** — see [LICENSE](LICENSE) for details.

---

<div align="center">

Made with ☕ by [Bhumik Jain](https://github.com/trynafindbhumik)

</div>
