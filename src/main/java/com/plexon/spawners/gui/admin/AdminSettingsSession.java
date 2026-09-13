package com.plexon.spawners.gui.admin;

import java.time.Instant;
import java.util.UUID;
import org.bukkit.entity.EntityType;

public final class AdminSettingsSession {
    private final UUID adminId;
    private AdminSettingsDraft draft;
    private long sourceRevision;
    private boolean dirty;
    private boolean showAllMobs;
    private EntityType pendingMobReset;
    private Instant touchedAt = Instant.now();

    public AdminSettingsSession(final UUID adminId, final AdminSettingsDraft draft, final long sourceRevision) {
        this.adminId = adminId;
        this.draft = draft;
        this.sourceRevision = sourceRevision;
    }

    public void replace(final AdminSettingsDraft replacement, final long revision) {
        draft = replacement;
        sourceRevision = revision;
        dirty = false;
        pendingMobReset = null;
        touch();
    }

    public void markDirty() {
        dirty = true;
        touch();
    }

    public void markClean(final long revision) {
        sourceRevision = revision;
        dirty = false;
        touch();
    }

    public void touch() {
        touchedAt = Instant.now();
    }

    public UUID adminId() { return adminId; }
    public AdminSettingsDraft draft() { return draft; }
    public long sourceRevision() { return sourceRevision; }
    public boolean dirty() { return dirty; }
    public boolean showAllMobs() { return showAllMobs; }
    public void showAllMobs(final boolean value) { showAllMobs = value; touch(); }
    public EntityType pendingMobReset() { return pendingMobReset; }
    public void pendingMobReset(final EntityType value) { pendingMobReset = value; touch(); }
    public Instant touchedAt() { return touchedAt; }
}
