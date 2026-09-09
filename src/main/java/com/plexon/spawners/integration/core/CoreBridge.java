package com.plexon.spawners.integration.core;

import java.util.Map;
import net.kyori.adventure.text.Component;

public interface CoreBridge {
    String SUPPORTED_API_RANGE = ">=1.0 <2.0";
    String MODULE_ID = "spawners";

    boolean installed();

    boolean available();

    boolean compatible();

    String pluginVersion();

    String apiVersion();

    String mode();

    String registrationState();

    String detail();

    Component renderMiniMessage(String trustedTemplate);

    Component renderTemplate(String trustedTemplate, Map<String, ?> runtimeValues);

    void registerStarting();

    void markReady(String detail);

    void markDegraded(String detail);

    void markFailed(String detail);

    void unregister();
}
