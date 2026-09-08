# Migration to PlexonSpawners 2.2.0

## Supported source

2.2.0 is built from the maintained 2.1.0 `main` source. The production server may upgrade directly from live 2.0.2 because the conservative 2.1.0 configuration/presentation migration remains present.

## Before deployment

Back up:

```text
PlexonSpawners-2.0.2.jar
plugins/PlexonSpawners/
```

Do not delete or regenerate the plugin data directory. Existing custom config/messages, exact configured Essence item, and existing managed spawner/Essence PDC items are intended to remain compatible.

## Deployment

```text
STOP SERVER
replace PlexonSpawners-2.0.2.jar with PlexonSpawners-2.2.0.jar
KEEP plugins/PlexonSpawners/
START SERVER
```

Then run:

```text
/plexon modules
/plexon diagnostics
/pspawners diagnostics
```

With compatible PlexonCore 1.0.0 expect module `spawners` / PlexonSpawners to report READY and mode CORE.

## Controlled validation

1. Break one spawner with valid Silk Touch and verify exactly one typed managed spawner, one durability action, configured XP behavior, and one recovery event.
2. With a deterministic temporary 100% Essence chance, test an unqualified break and verify the configured total amount plus exactly one Essence event. Restore production chance immediately afterward.
3. Verify OP without Silk does not recover a spawner when bypass is disabled.
4. Enable explicit bypass and permission temporarily and verify that path separately.
5. If WildStacker is installed, break one stacked spawner and verify stack amount decreases by exactly one.
6. Place a recovered managed spawner and verify the creature type plus exactly one placement event.
7. Test `/pspawners reload`, `/plexon reload`, and at least two full restarts for duplicate API/listener/module state.
8. Remove PlexonCore in staging and repeat representative gameplay to validate STANDALONE mode.

## Rollback

If rollback is required:

```text
stop server
restore PlexonSpawners-2.0.2.jar
restore the plugin config backup only if needed
start server
```

No database rollback exists because PlexonSpawners remains stateless.
