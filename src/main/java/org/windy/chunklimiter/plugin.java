package org.windy.chunklimiter;

import org.bukkit.plugin.java.JavaPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static net.neoforged.neoforge.common.NeoForge.EVENT_BUS;

public final class plugin extends JavaPlugin {
    private static final Logger LOGGER = LogManager.getLogger("ChunkLimiter");
    private static plugin instance;
    @Override
    public void onEnable() {
        instance = this;

        EVENT_BUS.register(this);
        EVENT_BUS.register(new ChunkLimiterListener());


    }
    @Override
    public void onDisable() {
        EVENT_BUS.unregister(this);
        EVENT_BUS.unregister(new ChunkLimiterListener());
    }
}
