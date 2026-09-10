package com.plexon.spawners.managed;

import java.util.UUID;

public enum SpawnerAccess {
    OWNER_ONLY,
    PUBLIC_USE,
    PUBLIC;

    public boolean canUse(final UUID actorId, final UUID ownerId, final boolean administrator) {
        return administrator || ownerId.equals(actorId) || this != OWNER_ONLY;
    }

    public boolean canManage(final UUID actorId, final UUID ownerId, final boolean administrator) {
        return administrator || ownerId.equals(actorId);
    }

    public boolean canBreak(final UUID actorId, final UUID ownerId, final boolean administrator) {
        return administrator || ownerId.equals(actorId) || this == PUBLIC;
    }

    public SpawnerAccess next() {
        return switch (this) {
            case OWNER_ONLY -> PUBLIC_USE;
            case PUBLIC_USE -> PUBLIC;
            case PUBLIC -> OWNER_ONLY;
        };
    }
}
