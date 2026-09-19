# PlexonSpawners 4.0 Administrator GUI

## Entry point

/pspawners admin

Permission: plexonspawners.admin.gui.

The GUI configures only Plexon-owned recovery/reward policy. It does not control WildStacker stack
amounts, placement, merge, item representation, limits, tiers/upgrades or normal placed-spawner
interaction.

## Session authority

Each administrator gets an isolated draft bound to:

- config revision;
- runtime generation;
- exact player UUID.

Closing/discarding without Save changes neither disk nor live runtime.

Any newer successful Save/reload makes older sessions stale. Stale drafts cannot overwrite the new
generation.

## Click safety

The top inventory is owned by a typed AdminGuiHolder. Managed interaction is cancelled.

Only LEFT, RIGHT, SHIFT_LEFT and SHIFT_RIGHT are accepted. Number-key swaps, offhand swaps, drops,
middle-click, double-click and other unapproved ClickTypes are ignored.

Semantic actions are deferred one tick and revalidate:

- player online state;
- permission;
- exact holder/session identity;
- config revision;
- runtime generation.

No filesystem operation runs from the click handler.

## Policy screens

- Status / Integration
- Break Policy
- Essence
- Custom Drop
- Mob Overrides
- World Scope
- Messages
- Save / Discard / Reload

There is no player withdrawal GUI and no withdrawal editor.

## Exact item capture

Capture Exact Held Item normalizes template amount to one and stores Paper's exact serialized bytes.

The exact template preserves supported item metadata/components including foreign PDC, enchantments,
attributes, damage, potion state and item model.

PlexonSpawners identity is added later to a clone; foreign PDC is not erased.

## Save transaction

Confirmed Save performs:

Primary thread:
1. permission/session/revision/generation validation;
2. complete draft validation;
3. immutable draft capture;
4. SAVE_PENDING single-flight state.

Bounded I/O:
5. timestamped backup;
6. candidate serialization;
7. same-directory temporary write;
8. atomic replace where supported.

Primary thread:
9. revalidate generation/revision;
10. prepare validated runtime snapshot;
11. commit snapshot;
12. bump revision;
13. refresh GUI/result.

Bounded I/O:
14. prune old normal admin backups.

If runtime commit is rejected after replacement, disk is restored from the pre-save backup and revision
does not advance. The previous runtime generation remains authoritative.

Only one configuration save transaction may be in flight.

## Reload

/pspawners reload loads config/messages on bounded I/O, prepares a complete runtime snapshot and commits
it on the primary thread only after all validation succeeds.

Invalid config/exact-item/MiniMessage data leaves the previous known-good generation active.

## Acceptance

The exact final candidate must prove:

- unauthorized access rejected;
- unsafe ClickTypes cannot move items or trigger actions;
- draft isolation/discard;
- competing-admin stale rejection;
- save backup/write off main thread;
- one generation/revision advance per successful save;
- malformed exact item rollback;
- disk failure rollback;
- close without save unchanged;
- no noticeable save-related tick stall.
