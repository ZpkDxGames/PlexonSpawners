# PlexonSpawners 4.0 Administrator GUI

## Entry point

```text
/pspawners admin
```

Permission: `plexonspawners.admin.gui` (default `op`). The GUI requires a player; console may still use `/pspawners status` and `/pspawners reload`.

## Authority boundary

The administrator GUI configures **PlexonSpawners-owned policy only**. It does not own normal placed-spawner interaction, placed stack quantities, merging, WildStacker limits/radius, linked entities, tiers/upgrades, spawner item representation or WildStacker persistence.

WildStacker remains the source of truth for all stack state and for its native spawner interaction/tier-upgrade GUI.

PlexonSpawners no longer registers a player-facing right-click withdrawal GUI. Existing schema-11 withdrawal keys are compatibility residue only and must not be treated as a supported player interaction surface.

## Draft sessions

Each administrator receives an isolated draft loaded from the live configuration revision. GUI clicks mutate only that draft. Closing/discarding without Save does not change disk or runtime policy.

Every successful GUI Save or `/pspawners reload` advances an in-memory revision. If an older admin session tries to save after the revision advanced, the save is rejected as stale and cannot silently overwrite the newer configuration.

## Supported policy areas

- **Status / Integration** — WildStacker version, authority, config schema/revision, dirty state and validation.
- **Break Policy** — policy enabled, Silk level, bypass permission, non-Silk mode, Creative recovery/Essence/custom reward flags.
- **Essence** — enabled, amount, chance, delivery, preview, sanitized held-item template/reset.
- **Custom Drop** — enabled, amount, chance, delivery, preview, sanitized held-item template/reset.
- **Mob Overrides** — paginated spawnable-mob selector, inherited/overridden state, per-mob mode and amounts/chances, confirmed reset to inheritance.
- **Worlds** — all-worlds vs allowlist, loaded worlds and retained offline configured names.
- **Messages** — supported break/reward feedback toggles.
- **Save / Discard / Reload** — explicit confirmation flows.

Any legacy withdrawal draft fields retained for schema-11 compatibility are non-authoritative and have no player placed-spawner runtime entrypoint.

## Save pipeline

A confirmed Save:

1. validates the complete draft;
2. rejects stale sessions;
3. creates `plugins/PlexonSpawners/config-backups/config-<timestamp>.yml`;
4. serializes the intended PlexonSpawners configuration;
5. writes a temporary file in the plugin directory;
6. atomically replaces `config.yml` where the filesystem supports it, with safe replace fallback;
7. reloads runtime settings/services;
8. advances the configuration revision;
9. retains the newest configured number of normal admin backups.

The special pre-4.0 migration backup is outside this retention directory and is not removed by normal admin-backup pruning.

## Reward modes

```text
ESSENCE
CUSTOM_ITEM
ESSENCE_AND_CUSTOM_ITEM
NONE
```

A qualifying Silk break remains a separate recovery path and uses WildStacker's authoritative spawner item. Non-Silk reward modes never redefine a recovered spawner item.

For each reward scheme, chance rolls once per exact logical spawner removed by WildStacker. Combined mode performs independent Essence and custom-item rolls. Successful rewards are aggregated and split only when required by legal item stack sizes.

## Final focused runtime acceptance

Before stable release, verify the exact final CI-built JAR:

- right-clicking a WildStacker-managed spawner does not open a Plexon withdrawal GUI or consume the interaction;
- WildStacker's native expected spawner tier/upgrade interaction works;
- authorized player opens `/pspawners admin`; unauthorized player cannot;
- `/pspawners give <online-player> zombie 2` (or equivalent) still delivers WildStacker-compatible spawners;
- one representative break/reward case still uses WildStacker logical quantity correctly;
- no Plexon native stack state returns.

Do not infer runtime PASS from unit tests or GitHub Actions.
