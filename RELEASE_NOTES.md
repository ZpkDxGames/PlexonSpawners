# PlexonSpawners 4.0.0 — WildStacker Authority + Administrator Policy GUI

PlexonSpawners 4.0.0 keeps WildStacker as the exclusive stack engine while expanding Plexon-owned recovery/reward policy and administration.

The final 4.0 line includes `/pspawners admin`, `/pspawners give <player> <mobtype> <amount>`, draft-based configuration editing, Essence/custom non-Silk reward modes, independent per-logical-unit rolls, per-mob overrides, safe config backup/atomic save behavior and stale-session protection.

Placed-spawner interaction now belongs entirely to WildStacker. PlexonSpawners no longer opens or registers its former player withdrawal GUI when a spawner is right-clicked, allowing WildStacker's native tier/upgrade interaction to run normally. The PlexonSpawners administrator GUI remains available through `/pspawners admin` for Plexon-owned break and reward policy.

Schema remains `11`; this cleanup does not introduce another migration. Final campaign migration status is `NOT_REQUIRED`.

The broader 4.0 candidate has already passed PlexonCraft runtime testing, but the exact final CI-built JAR still requires the focused click-handoff smoke before stable publication. See `.release/RELEASE_NOTES_4.0.0.md` and `docs/ADMIN_GUI.md`.
