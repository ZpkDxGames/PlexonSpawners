package com.plexon.spawners.gui.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class AdminSettingsSessionTest {
    @Test
    void sessionTracksGenerationRevisionAndSinglePendingSave() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/com/plexon/spawners/gui/admin/AdminSettingsSession.java"));
        assertTrue(source.contains("long sourceRevision"));
        assertTrue(source.contains("long sourceGeneration"));
        assertTrue(source.contains("boolean savePending"));
        assertTrue(source.contains("boolean beginSave()"));
        assertTrue(source.contains("if (savePending) return false"));
        assertTrue(source.contains("markClean(final long revision, final long generation)"));
    }

    @Test
    void guiRejectsUnsafeClickTypesByAllowlistingOnlyIntentionalClicks() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/com/plexon/spawners/gui/admin/AdminGuiService.java"));
        assertTrue(source.contains("EnumSet.of(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT)"));
        assertTrue(source.contains("if (!ALLOWED_CLICKS.contains(event.getClick())) return;"));
        assertTrue(source.contains("event.setCancelled(true)"));
        assertTrue(source.contains("schedulePrimary(Duration.ofMillis(50)"));
        assertTrue(source.contains("holder.generation() != session.sourceGeneration()"));
        assertTrue(source.contains("holder.revision() != session.sourceRevision()"));
        assertFalse(source.contains("Files."));
    }

    @Test
    void draftHasExplicitDeepCopyBoundary() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/com/plexon/spawners/gui/admin/AdminSettingsDraft.java"));
        assertTrue(source.contains("public AdminSettingsDraft copy()"));
        assertTrue(source.contains("copy.enabledWorlds.addAll(enabledWorlds)"));
        assertTrue(source.contains("copy.mobOverrides.putAll(mobOverrides)"));
    }
}
