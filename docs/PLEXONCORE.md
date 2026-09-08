# PlexonCore Integration

PlexonSpawners 2.2.0 supports PlexonCore API `>=1.0 <2.0`.

## Module identity

```text
id: spawners
display: PlexonSpawners
mode with compatible Core: CORE
mode without usable Core: STANDALONE
```

Published capabilities include the spawner engine, managed spawner items, physical Essence, public service API, public recovery/Essence/placement events, WildStacker compatibility, and stateless runtime.

## Lifecycle

Startup resolves Core once, registers `STARTING`, initializes config/services/API/GUI/compat/listeners, then marks the module `READY`. Critical startup failures mark `FAILED` before the plugin fails safely. A runtime WildStacker API linkage failure marks the Core module `DEGRADED` while preserving the non-destructive compatibility fallback.

Shutdown unregisters Bukkit services and then unregisters module `spawners` if this plugin owns that registration.

## Runtime isolation

PlexonCore is compile-only/provided. `CoreBridgeFactory` isolates optional API linkage behind reflective loading so Core absence cannot create a class-linkage failure. CI verifies the final JAR contains no `com/zpkdxgames/plexoncore/` runtime classes.

No Core lookup, version parsing, integration refresh, reflection discovery, filesystem I/O, or config reload occurs per spawner break.

## Diagnostics

Use:

```text
/plexon modules
/plexon integrations
/plexon diagnostics
/pspawners diagnostics
```

Expected production state with Core 1.0.0 is `PlexonSpawners — READY`, mode `CORE`.
