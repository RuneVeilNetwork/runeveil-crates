package com.runeveil.crates.storage;

import com.runeveil.crates.config.CrateConfigManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class CrateLocationKey {
    private CrateLocationKey() {
    }

    public static String encode(ResourceKey<Level> dimension, BlockPos pos) {
        return dimension.location().toString() + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static BlockPos decodePos(String key) {
        int separator = key.lastIndexOf('|');
        if (separator < 0) {
            return null;
        }
        String[] parts = key.substring(separator + 1).split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static ResourceKey<Level> decodeDimension(String key) {
        int separator = key.lastIndexOf('|');
        if (separator < 0) {
            return null;
        }
        ResourceLocation location = ResourceLocation.tryParse(key.substring(0, separator));
        return location == null ? null : ResourceKey.create(Registries.DIMENSION, location);
    }
}
