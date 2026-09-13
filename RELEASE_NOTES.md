# PlexonSpawners 4.0.0 — WildStacker Authority + Administrator Policy GUI

PlexonSpawners 4.0.0 keeps WildStacker as the exclusive stack engine while expanding Plexon-owned recovery/reward policy and administration.

The final 4.0 line includes `/pspawners admin`, `/pspawners give <player> <mobtype> <amount>`, draft-based configuration editing, Essence/custom non-Silk reward modes, independent per-logical-unit rolls, per-mob overrides, safe config backup/atomic save behavior and stale-session protection.

Placed-spawner interaction belongs entirely to WildStacker. PlexonSpawners no longer opens or registers its former player withdrawal GUI when a spawner is right-clicked. WildStacker's native manage menu must therefore be enabled in WildStacker itself: in WildStacker 2026.2, at least one of `spawners.manage-menu.amounts-menu` or `spawners.manage-menu.upgrade-menu` must be `true`; `spawners.manage-menu.sneaking-open-menu` controls whether sneaking is required.

`/pspawners give` delegates through the namespaced `wildstacker:stacker give -s ...` command so another plugin cannot capture the generic `stacker` label.

For PlexonSpawners non-Silk rewards to run, WildStacker must allow the break to reach its unstack pipeline. On WildStacker 2026.2 that means `spawners.mine-require-silk: false`; otherwise WildStacker rejects non-Silk survival breaks before `SpawnerUnstackEvent` is emitted. PlexonSpawners does not rewrite WildStacker configuration.

Creative 4.0 defaults now recover the WildStacker-authoritative spawner item and award Essence. Existing schema-11 installations must explicitly set `breaking.creative.recover-spawner: true` and `breaking.creative.award-essence: true` because this runtime correction intentionally does not bump the schema.

Schema remains `11`; this correction does not introduce another migration. Final campaign migration status is `NOT_REQUIRED`.

The exact post-remediation CI-built JAR still requires the focused runtime smoke before stable publication. See `.release/RELEASE_NOTES_4.0.0.md` and `docs/ADMIN_GUI.md`.
