# PlexonSpawners 3.1.1

`3.1.1` is a stable configuration-visibility hotfix for Paper 26.2 / Java 25 / PlexonCore 2.0.4.

## Fixed

Existing PlexonSpawners installations could keep a sparse physical `config.yml` even though the bundled JAR defaults contained the full managed-spawner settings. Bukkit resolves missing keys through the bundled defaults, so checks using the normal `contains(path)` API could report a key as present even when it was absent from the file on disk.

This was especially confusing for the 3.1 logical stack-cap feature because the runtime could use the defaults while administrators could not see or edit the expected section in their existing `config.yml`.

3.1.1 now detects missing **explicit** configuration values with Bukkit's ignore-default lookup and materializes the bundled defaults into the physical config before the Essence runtime finishes loading. Existing administrator values remain authoritative and are not overwritten.

After the first startup on 3.1.1, the generated file exposes the full configuration surface, including:

```yaml
managed:
  nearby-stack-cap:
    enabled: true
    radius: 8.0
    maximum-amount: 99
    same-type-only: true
```

## Compatibility

- Configuration schema remains `6`.
- Managed persistence schema remains `1`.
- Managed item schema remains `2`.
- No database migration is required.
- Existing custom values, including serialized Essence items, are preserved.
- Stable rollback is `v3.1.0` at `8aaf1b7078edf5e9076af02035d204e123b3958a`.

## Verification boundary

GitHub source/build certification covers compilation, tests, JAR contents, Java 25/class major 69, Paper 26.2 metadata, config schema 6, PlexonCore isolation, checksum generation, exact-main publication and public-asset verification.

Live PlexonCraft runtime certification remains separate and is not inferred from CI.

**Runtime certification: NOT EXECUTED**
