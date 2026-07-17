# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.2.0] - 2026-07-17

### Added
- Full strict Folia support using region schedulers
- Proper use of `Bukkit.getRegionScheduler()` and `Bukkit.getGlobalRegionScheduler()`
- Entity-safe hologram removal using `Entity.getScheduler()`
- `cleanupOrphanedHolograms()` now runs safely on startup
- Improved error handling when loading and saving chests

### Changed
- Removed all reflection-based code
- Deleted `FoliaUtil.java` (no longer needed for strict Folia)
- Refactored `ChestManager` for better Folia thread safety
- Updated `removeHologram()`, `spawnChest()`, `removeChest()`, and `relocateChest()` to properly use schedulers
- Improved `PlayerDeathListener` to schedule chest creation on the correct region thread
- Updated `ChestManageGUI` to use `GlobalRegionScheduler` for delayed actions
- Modernized several classes for better long-term maintainability on Folia

### Fixed
- Fixed major Folia threading violations when removing holograms during chest expiry
- Prevented "Accessing entity state off owning region's thread" errors
- Improved reliability of chest spawning, fetching, teleporting, and expiring under Folia

### Removed
- Removed hybrid Paper/Folia detection logic
- Removed unused `FoliaUtil` class

---

**Note:** Version 1.2.0 is the first official release of the strict Folia version of this plugin.