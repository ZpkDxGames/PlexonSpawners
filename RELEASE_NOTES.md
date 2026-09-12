# PlexonSpawners 3.1.1

`3.1.1` is a stable configuration-visibility hotfix for Paper 26.2 / Java 25 / PlexonCore 2.0.4.

## Fixed

Existing installations could retain a sparse physical `config.yml` even though the bundled defaults contained the complete configuration. Bukkit resolves missing values through those defaults, so normal `contains(path)` checks could report a key as available while it was still absent from the file on disk.

This made the 3.1 stack-cap settings effectively hidden on upgraded servers. 3.1.1 detects missing explicit values and materializes bundled defaults into the physical configuration without replacing administrator overrides.

After first startup, the file exposes the full managed-spawner configuration including:

```yaml
managed:
  nearby-stack-cap:
    enabled: true
    radius: 8.0
    maximum-amount: 99
    same-type-only: true
```

The behavior remains unchanged: WildStacker logical amounts are counted, spawning pauses at the configured threshold, and automatically becomes eligible again when the nearby logical population falls below it.

## Compatibility

- Configuration schema: unchanged at 6
- Managed persistence schema: unchanged at 1
- Managed item schema: unchanged at 2
- Existing custom configuration values are preserved
- No database migration is required
- Stable rollback: `v3.1.0` at `8aaf1b7078edf5e9076af02035d204e123b3958a`
- Rollback JAR SHA-256: `00281428501747d3ae16304a5e376006bde01a81f69181d4c327a65c71506d65`

GitHub source/build certification is distinct from live PlexonCraft runtime certification.

**Runtime certification: NOT EXECUTED**
