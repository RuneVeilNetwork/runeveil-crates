package com.runeveil.crates;

import com.runeveil.crates.command.CrateCommand;
import com.runeveil.crates.config.CrateConfigManager;
import com.runeveil.crates.event.CrateEvents;
import com.runeveil.crates.integration.VotifierIntegration;
import com.runeveil.crates.visual.CrateHologramManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(RuneveilCrates.MOD_ID)
public class RuneveilCratesMod {
    public static final Logger LOGGER = LogManager.getLogger(RuneveilCrates.MOD_ID);

    private CrateConfigManager configManager;

    public RuneveilCratesMod() {
        IEventBus forgeBus = MinecraftForge.EVENT_BUS;
        forgeBus.register(this);
        forgeBus.register(CrateEvents.class);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        configManager = new CrateConfigManager(event.getServer());
        configManager.load();
        CrateEvents.setConfigManager(configManager);
        LOGGER.info("RuneVeil Crates loaded {} crate type(s) and {} key type(s).",
                configManager.getCrates().size(), configManager.getKeys().size());
        LOGGER.info("FTB Chunks crate whitelist tags are bundled. For OpenPAC setup see config/runeveilcrates/claim-compat.txt");
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        if (configManager != null) {
            event.getServer().execute(() -> {
                CrateHologramManager.refreshAllDimensions(configManager);
                LOGGER.info("Refreshed crate holograms after world load.");
            });
            VotifierIntegration.initialize(event.getServer(), configManager);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (configManager != null
                && configManager.getSettings().hologram != null
                && configManager.getSettings().hologram.removeOnShutdown) {
            int removed = CrateHologramManager.cleanupAll(event.getServer());
            LOGGER.info("Removed {} crate hologram(s) before shutdown (hologram.removeOnShutdown = true).", removed);
        }
        if (configManager != null) {
            configManager.saveLocations();
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CrateCommand.register(event.getDispatcher(), () -> configManager);
    }
}
