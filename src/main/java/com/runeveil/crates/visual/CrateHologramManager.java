package com.runeveil.crates.visual;

import com.runeveil.crates.RuneveilCrates;
import com.runeveil.crates.config.CrateConfigManager;
import com.runeveil.crates.config.CrateDefinition;
import com.runeveil.crates.config.SettingsConfig;
import com.runeveil.crates.storage.CrateLocationKey;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CrateHologramManager {
    public static final String HOLOGRAM_TAG = "runeveilcrates_hologram";
    private static final String LOCATION_NBT = "RuneveilCratesLocation";
    private static final Map<String, UUID> HOLOGRAM_IDS = new HashMap<>();

    private CrateHologramManager() {
    }

    public static void refreshAllDimensions(CrateConfigManager manager) {
        SettingsConfig settings = manager.getSettings();
        if (settings.hologram != null && settings.hologram.removeOrphansOnStartup) {
            cleanupOrphans(manager);
        }

        manager.getLocations().locations.forEach((locationKey, crateId) -> {
            BlockPos pos = CrateLocationKey.decodePos(locationKey);
            ResourceKey<Level> dimension = parseDimensionKey(locationKey);
            if (pos == null || dimension == null) {
                return;
            }
            CrateDefinition crate = manager.getCrate(crateId);
            if (crate == null) {
                return;
            }
            ServerLevel level = manager.getServerLevel(dimension);
            if (level != null) {
                showCrateLabel(level, pos, crate, settings);
            }
        });
    }

    public static void refreshChunk(CrateConfigManager manager, ServerLevel level, ChunkPos chunkPos) {
        SettingsConfig settings = manager.getSettings();
        manager.getLocations().locations.forEach((locationKey, crateId) -> {
            BlockPos pos = CrateLocationKey.decodePos(locationKey);
            ResourceKey<Level> dimension = parseDimensionKey(locationKey);
            if (pos == null || dimension == null || !dimension.equals(level.dimension()) || !isInChunk(pos, chunkPos)) {
                return;
            }
            CrateDefinition crate = manager.getCrate(crateId);
            if (crate != null) {
                showCrateLabel(level, pos, crate, settings);
            }
        });
    }

    static boolean isInChunk(BlockPos pos, ChunkPos chunkPos) {
        return (pos.getX() >> 4) == chunkPos.x && (pos.getZ() >> 4) == chunkPos.z;
    }

    public static int cleanupAll(MinecraftServer server) {
        return cleanupAll(server, null);
    }

    public static int cleanupAll(MinecraftServer server, CrateConfigManager manager) {
        int removed = 0;
        HOLOGRAM_IDS.clear();

        if (manager != null) {
            SettingsConfig settings = manager.getSettings();
            for (String locationKey : manager.getLocations().locations.keySet()) {
                BlockPos pos = CrateLocationKey.decodePos(locationKey);
                ResourceKey<Level> dimension = parseDimensionKey(locationKey);
                if (pos == null || dimension == null) {
                    continue;
                }
                ServerLevel level = manager.getServerLevel(dimension);
                if (level != null) {
                    removed += purgeHologramsAt(level, pos, settings);
                }
            }
        }

        for (ServerLevel level : server.getAllLevels()) {
            removed += removeTaggedHolograms(level);
        }
        return removed;
    }

    public static int cleanupOrphans(CrateConfigManager manager) {
        Set<String> activeLocations = new HashSet<>(manager.getLocations().locations.keySet());
        int removed = 0;

        for (ServerLevel level : manager.getServer().getAllLevels()) {
            List<ArmorStand> holograms = new ArrayList<>(level.getEntitiesOfClass(ArmorStand.class, worldBounds(level)));
            for (ArmorStand stand : holograms) {
                if (!isManagedHologram(stand)) {
                    continue;
                }
                String locationKey = stand.getPersistentData().getString(LOCATION_NBT);
                if (locationKey.isBlank() || !activeLocations.contains(locationKey)) {
                    stand.discard();
                    removed++;
                }
            }
        }

        HOLOGRAM_IDS.entrySet().removeIf(entry -> !activeLocations.contains(entry.getKey()));
        return removed;
    }

    public static void showCrateLabel(ServerLevel level, BlockPos pos, CrateDefinition crate, SettingsConfig settings) {
        ArmorStand stand = getOrCreate(level, pos, settings);
        if (stand != null) {
            stand.setCustomName(CrateVisuals.crateLabel(crate));
        }
    }

    public static void showText(ServerLevel level, BlockPos pos, Component text, SettingsConfig settings) {
        ArmorStand stand = getOrCreate(level, pos, settings);
        if (stand != null) {
            stand.setCustomName(text);
        }
    }

    public static void remove(ServerLevel level, BlockPos pos) {
        String key = CrateLocationKey.encode(level.dimension(), pos);
        HOLOGRAM_IDS.remove(key);
        purgeHologramsAt(level, pos, null);
    }

    private static ArmorStand getOrCreate(ServerLevel level, BlockPos pos, SettingsConfig settings) {
        if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return null;
        String key = CrateLocationKey.encode(level.dimension(), pos);

        UUID cachedId = HOLOGRAM_IDS.get(key);
        if (cachedId != null) {
            Entity entity = level.getEntity(cachedId);
            if (entity instanceof ArmorStand armorStand && armorStand.isAlive()) {
                dedupeHolograms(level, pos, settings, armorStand);
                tagHologram(armorStand, key);
                HOLOGRAM_IDS.put(key, armorStand.getUUID());
                return armorStand;
            }
            HOLOGRAM_IDS.remove(key);
        }

        List<ArmorStand> existing = findHologramsAt(level, pos, settings);
        if (!existing.isEmpty()) {
            ArmorStand primary = existing.get(0);
            dedupeHolograms(level, pos, settings, primary);
            tagHologram(primary, key);
            HOLOGRAM_IDS.put(key, primary.getUUID());
            return primary;
        }

        ArmorStand stand = EntityType.ARMOR_STAND.create(level);
        if (stand == null) {
            return null;
        }

        Vec3 anchor = anchor(pos, settings);
        stand.moveTo(anchor.x, anchor.y, anchor.z, 0.0F, 0.0F);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.getEntityData().set(ArmorStand.DATA_CLIENT_FLAGS, (byte) (
                ArmorStand.CLIENT_FLAG_MARKER | ArmorStand.CLIENT_FLAG_SMALL | ArmorStand.CLIENT_FLAG_NO_BASEPLATE
        ));
        stand.setCustomNameVisible(true);
        stand.setInvulnerable(true);
        tagHologram(stand, key);
        level.addFreshEntity(stand);
        HOLOGRAM_IDS.put(key, stand.getUUID());
        return stand;
    }

    private static void dedupeHolograms(ServerLevel level, BlockPos pos, SettingsConfig settings, ArmorStand keep) {
        for (ArmorStand stand : findHologramsAt(level, pos, settings)) {
            if (stand != keep && stand.isAlive()) {
                stand.discard();
            }
        }
    }

    private static int purgeHologramsAt(ServerLevel level, BlockPos pos, SettingsConfig settings) {
        if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return 0;
        String key = CrateLocationKey.encode(level.dimension(), pos);
        HOLOGRAM_IDS.remove(key);

        int removed = 0;
        for (ArmorStand stand : findHologramsAt(level, pos, settings)) {
            stand.discard();
            removed++;
        }
        return removed;
    }

    private static List<ArmorStand> findHologramsAt(ServerLevel level, BlockPos pos, SettingsConfig settings) {
        if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return List.of();
        List<ArmorStand> found = new ArrayList<>();
        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, searchBox(pos, settings))) {
            if (isHologramCandidate(stand, pos, settings)) {
                found.add(stand);
            }
        }
        found.sort(Comparator.comparingInt(stand -> hologramPriority(stand, pos)));
        return found;
    }

    private static int hologramPriority(ArmorStand stand, BlockPos pos) {
        if (isManagedHologram(stand, pos)) {
            return 0;
        }
        if (stand.hasCustomName()) {
            return 1;
        }
        return 2;
    }

    private static void tagHologram(ArmorStand stand, String locationKey) {
        stand.addTag(HOLOGRAM_TAG);
        CompoundTag data = stand.getPersistentData();
        data.putString(LOCATION_NBT, locationKey);
        data.putString(RuneveilCrates.MOD_ID, "hologram");
    }

    private static boolean isManagedHologram(ArmorStand stand) {
        return stand.getTags().contains(HOLOGRAM_TAG)
                || stand.getPersistentData().contains(RuneveilCrates.MOD_ID)
                || stand.getPersistentData().contains(LOCATION_NBT);
    }

    private static boolean isManagedHologram(ArmorStand stand, BlockPos pos) {
        if (!isManagedHologram(stand)) {
            return false;
        }
        String locationKey = stand.getPersistentData().getString(LOCATION_NBT);
        return locationKey.isBlank() || locationKey.equals(CrateLocationKey.encode(stand.level().dimension(), pos));
    }

    private static boolean isHologramCandidate(ArmorStand stand, BlockPos pos, SettingsConfig settings) {
        if (!isNearCrateHologramSpot(stand, pos, settings)) {
            return false;
        }
        return isManagedHologram(stand);
    }

    private static boolean isNearCrateHologramSpot(ArmorStand stand, BlockPos pos, SettingsConfig settings) {
        Vec3 anchor = anchor(pos, settings);
        Vec3 standPos = stand.position();
        return Math.abs(standPos.x - anchor.x) <= 1.25D
                && Math.abs(standPos.y - anchor.y) <= 1.25D
                && Math.abs(standPos.z - anchor.z) <= 1.25D;
    }

    private static int removeTaggedHolograms(ServerLevel level) {
        int removed = 0;
        for (ArmorStand stand : new ArrayList<>(level.getEntitiesOfClass(ArmorStand.class, worldBounds(level)))) {
            if (isManagedHologram(stand)) {
                stand.discard();
                removed++;
            }
        }
        return removed;
    }

    private static AABB worldBounds(ServerLevel level) {
        return new AABB(-30_000_000, level.getMinBuildHeight(), -30_000_000,
                30_000_000, level.getMaxBuildHeight(), 30_000_000);
    }

    private static Vec3 anchor(BlockPos pos, SettingsConfig settings) {
        double yOffset = settings == null || settings.hologram == null ? 1.35D : settings.hologram.yOffset;
        return new Vec3(pos.getX() + 0.5D, pos.getY() + yOffset, pos.getZ() + 0.5D);
    }

    private static AABB searchBox(BlockPos pos, SettingsConfig settings) {
        Vec3 anchor = anchor(pos, settings);
        return new AABB(anchor, anchor).inflate(1.25D, 1.25D, 1.25D);
    }

    private static ResourceKey<Level> parseDimensionKey(String key) {
        int separator = key.lastIndexOf('|');
        if (separator < 0) {
            return null;
        }
        return ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.tryParse(key.substring(0, separator))
        );
    }
}
