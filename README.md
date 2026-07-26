# RuneVeil Crates

Server-side Forge 1.20.1 crate keys, holograms, weighted rarity rolls, pity protection, a chest-based loot editor, vote rewards, and Discord Nitro crates. Clients do not need a resource pack or the mod installed.

## Install

1. Build with `gradlew.bat build` or download the release JAR.
2. Put the JAR in the Forge server `mods` directory.
3. Start the server. Configuration is created in `config/runeveilcrates/`.
4. Run `/crate validate`, convert a supported block with `/crate convert vote`, then issue a key with `/crate givekey vote <player>`.

## Main commands

- `/crate convert <type>`, `/crate unconvert`, `/crate info`, `/crate edit`
- `/crate givekey <type> [player] [amount]`
- `/crate givekey <type> <amount> all` gives that key and amount to everyone online
- `/crate givekeyoffline <type> <uuid> <amount>`
- `/crate preview <type>`, `/crate validate`, `/crate reload`
- `/crate duplicate <source> <newId>`, `/crate rename <oldId> <newId>`
- `/crate delete <type> confirm`, `/crate export <type>`, `/crate import <file>`
- `/crate override <type> <setting> <value>`
- `/crate settings rollanimation ...`, `/crate cleanupholograms`

Roll animation uses `maximumSteps` as the exact maximum number of reward previews before showing the winner. Set it with `/crate settings rollanimation maximumsteps <1-1000>`.

Administrative permission level is controlled by `adminPermissionLevel`; empty-hand editor access uses `editorPermissionLevel`.

## Configuration

`settings.json` controls global behavior. Keys and crates use one commented JSON file per type. `/crate reload` is transactional: invalid changes are rejected and the last working configuration remains active. JSON saves use backup and atomic replacement files.

Per-crate overrides support `consumeKey`, `broadcastRare`, `animation`, `pity`, `inventory`, and `cooldown`. Use `inherit` to return to the global value. Inventory-full policies are `drop`, `deny`, and `discard`.

The editor displays effective reward probability. Destructive reward and crate actions require confirmation. Its map button supports duplicate/export; shift-clicking it uses the custom name of the held item as the rename ID or import filename. The same actions are available as commands.

Imports belong in `config/runeveilcrates/imports/`; exports are written to `config/runeveilcrates/exports/`.

## Build and test

```text
gradlew.bat clean build
```

The project targets Java 17 bytecode and Forge 47.4.10 for Minecraft 1.20.1.

## License

MIT
