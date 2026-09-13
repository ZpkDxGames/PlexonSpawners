# PlexonSpawners 4.0 Administrator GUI

## Entry point

```text
/pspawners admin
```

Permission: `plexonspawners.admin.gui` (default `op`). The GUI requires a player; console may still use `/pspawners status` and `/pspawners reload`.

## Authority boundary

The administrator GUI configures **PlexonSpawners policy only**. It does not set placed stack quantities, merge stacks, alter WildStacker limits/radius, manipulate linked entities, edit WildStacker upgrades, or write WildStacker persistence.

WildStacker remains the source of truth for all stack state and authoritative spawner items.

## Draft sessions

Each administrator receives an isolated draft loaded from the live configuration revision. GUI clicks mutate only that draft. Closing/discarding without Save does not change disk or runtime policy.

Every successful GUI Save or `/pspawners reload` advances an in-memory revision. If an older admin session tries to save after the revision advanced, the save is rejected as stale and cannot silently overwrite the newer configuration.

## Main screens

- **Status / Integration** — WildStacker version, authority, config schema/revision, dirty state and validation.
- **Break Policy** — policy enabled, Silk level, bypass permission, non-Silk mode, Creative recovery/Essence/custom reward flags.
- **Essence** — enabled, amount, chance, delivery, preview, sanitized held-item template/reset.
- **Custom Drop** — enabled, amount, chance, delivery, preview, sanitized held-item template/reset.
- **Mob Overrides** — paginated spawnable-mob selector, configured-only/all toggle, inherited/overridden state, per-mob mode and amounts/chances, confirmed reset to inheritance.
- **Withdrawal** — enabled, right-click behavior, configurable presets and WildStacker-authority explanation.
- **Worlds** — all-worlds vs allowlist, loaded worlds and retained offline configured names.
- **Messages** — Silk, Essence, custom reward, withdrawal success/failure toggles.
- **Save / Discard / Reload** — explicit confirmation flows.

## Click controls

Bounded numeric controls use left/right clicks and shift modifiers. Reward item templates may be captured from the administrator's main-hand item; only supported visual fields are reconstructed. Arbitrary foreign PDC/ownership/container metadata is not persisted.

## Save pipeline

A confirmed Save:

1. validates the complete draft;
2. rejects stale sessions;
3. creates `plugins/PlexonSpawners/config-backups/config-<timestamp>.yml`;
4. serializes the complete intended PlexonSpawners config;
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

## Validation

Hard failures block Save, including invalid amount/chance ranges, AIR/invalid reward templates, duplicate/invalid withdrawal presets, blank titles and malformed MiniMessage fields. Non-fatal states such as an enabled scheme with 0% chance are surfaced as warnings.

## Runtime acceptance matrix

Before stable release, verify:

- authorized player opens `/pspawners admin`; unauthorized player cannot; console gets player-only feedback;
- changes closed without Save leave runtime and `config.yml` unchanged;
- confirmed Save creates a backup, writes valid YAML, updates runtime immediately and survives restart;
- two-admin stale-session protection works;
- Essence at 100% gives exact logical-unit reward totals;
- custom item at 100% preserves configured visual metadata/Plexon identity and no spawner recovery item leaks;
- combined mode at 100% yields both schemes independently;
- qualifying Silk returns WildStacker's authoritative item and suppresses non-Silk rewards;
- Creative matrix matches configuration exactly;
- one mob override works while another mob inherits defaults;
- withdrawal presets/all preserve the final unit, revalidate live state, never grant break rewards and resist rapid-click duplication;
- ordinary player right-click opens only the withdrawal GUI and does not open admin UI;
- WildStacker overlapping interaction settings remain compatible with the production profile.

Do not infer runtime PASS from unit tests or GitHub Actions.
