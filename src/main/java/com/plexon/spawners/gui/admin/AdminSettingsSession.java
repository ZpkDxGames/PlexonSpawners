package com.plexon.spawners.gui.admin;

import java.time.Instant;
import java.util.UUID;
import org.bukkit.entity.EntityType;

public final class AdminSettingsSession {
    private final UUID adminId;
    private AdminSettingsDraft draft;
    private long sourceRevision;
    private long sourceGeneration;
    private boolean dirty;
    private boolean savePending;
    private boolean showAllMobs;
    private EntityType pendingMobReset;
    private Instant touchedAt = Instant.now();

    public AdminSettingsSession(
        final UUID adminId,
        final AdminSettingsDraft draft,
        final long sourceRevision,
        final long sourceGeneration
    ) {
        this.adminId = adminId;
        this.draft = draft;
        this.sourceRevision = sourceRevision;
        this.sourceGeneration = sourceGeneration;
    }

    public void replace(final AdminSettingsDraft replacement, final long revision, final long generation) {
        draft = replacement;
        sourceRevision = revision;
        sourceGeneration = generation;
        dirty = false;
        savePending = false;
        pendingMobReset = null;
        touch();
    }

    public void markDirty() { dirty = true; touch(); }
    public void markClean(final long revision, final long generation) {
        sourceRevision = revision;
        sourceGeneration = generation;
        dirty = false;
        savePending = false;
        touch();
    }
    public boolean beginSave() {
        if (savePending) return false;
        savePending = true;
        touch();
        return true;
    }
    public void endSave() { savePending = false; touch(); }
    public void touch() { touchedAt = Instant.now(); }

    public UUID adminId() { return adminId; }
    public AdminSettingsDraft draft() { return draft; }
    public long sourceRevision() { return sourceRevision; }
    public long sourceGeneration() { return sourceGeneration; }
    public boolean dirty() { return dirty; }
    public boolean savePending() { return savePending; }
    public boolean showAllMobs() { return showAllMobs; }
    public void showAllMobs(final boolean value) { showAllMobs = value; touch(); }
    public EntityType pendingMobReset() { return pendingMobReset; }
    public void pendingMobReset(final EntityType value) { pendingMobReset = value; touch(); }
    public Instant touchedAt() { return touchedAt; }
}
