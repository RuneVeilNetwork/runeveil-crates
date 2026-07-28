package com.runeveil.crates.config;

import com.runeveil.crates.util.RarityDefinitions;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConfigValidator {
    private static final Set<String> INVENTORY_POLICIES = Set.of("drop", "deny", "discard");

    private ConfigValidator() {}

    public static List<String> validate(SettingsConfig settings, Map<String, KeyConfig> keys,
                                        Map<String, CrateDefinition> crates) {
        List<String> errors = new ArrayList<>();
        if (settings == null) return List.of("settings.json did not produce a configuration object");
        if (settings.messages == null) errors.add("settings.messages must be an object");
        if (settings.allowedBlocks == null) errors.add("settings.allowedBlocks must be an array");
        if (settings.maxLookDistance < 1 || settings.maxLookDistance > 64) errors.add("maxLookDistance must be between 1 and 64");
        if (settings.openCooldownSeconds < 0) errors.add("openCooldownSeconds cannot be negative");
        if (settings.editorPermissionLevel < 0 || settings.editorPermissionLevel > 4) errors.add("editorPermissionLevel must be 0-4");
        if (settings.adminPermissionLevel < 0 || settings.adminPermissionLevel > 4) errors.add("adminPermissionLevel must be 0-4");
        if (!INVENTORY_POLICIES.contains(normalizePolicy(settings.inventoryFullPolicy))) errors.add("inventoryFullPolicy must be drop, deny, or discard");

        Set<String> keyIds = new HashSet<>();
        keys.forEach((fileId, key) -> {
            String id = CrateConfigManager.normalize(key == null ? fileId : key.id);
            if (id.isBlank()) errors.add("Key " + fileId + " has a blank id");
            if (!keyIds.add(id)) errors.add("Duplicate key id: " + id);
            if (key == null || key.displayName == null || key.displayName.isBlank()) errors.add("Key " + id + " has no displayName");
            ResourceLocation item = key == null ? null : ResourceLocation.tryParse(key.item);
            if (item == null || !ForgeRegistries.ITEMS.containsKey(item)) errors.add("Key " + id + " has unknown item: " + (key == null ? "null" : key.item));
        });

        Set<String> crateIds = new HashSet<>();
        crates.forEach((fileId, crate) -> validateCrate(fileId, crate, keyIds, crateIds, errors));
        return errors;
    }

    private static void validateCrate(String fileId, CrateDefinition crate, Set<String> keyIds,
                                      Set<String> crateIds, List<String> errors) {
        if (crate == null) { errors.add("Crate " + fileId + " is null"); return; }
        String id = CrateConfigManager.normalize(crate.id);
        if (id.isBlank()) errors.add("Crate " + fileId + " has a blank id");
        if (!crateIds.add(id)) errors.add("Duplicate crate id: " + id);
        if (!keyIds.contains(CrateConfigManager.normalize(crate.requiredKey))) errors.add("Crate " + id + " references unknown key " + crate.requiredKey);
        if (crate.cooldownSeconds < -1) errors.add("Crate " + id + " cooldownSeconds cannot be less than -1");
        if (crate.pityPullsWithoutRarePlus != null && crate.pityPullsWithoutRarePlus < 1) errors.add("Crate " + id + " pity override must be at least 1");
        String policy = normalizePolicy(crate.inventoryFullPolicy);
        if (!"inherit".equals(policy) && !INVENTORY_POLICIES.contains(policy)) errors.add("Crate " + id + " inventoryFullPolicy must be inherit, drop, deny, or discard");
        if (crate.rewards == null || crate.rewards.isEmpty()) { errors.add("Crate " + id + " has no rewards"); return; }
        for (RewardEntry reward : crate.rewards) {
            if (reward == null) { errors.add("Crate " + id + " contains a null reward"); continue; }
            if (reward.id == null || reward.id.isBlank()) errors.add("Crate " + id + " contains a reward with a blank id");
            if (reward.weight < 1) errors.add("Reward " + reward.id + " in " + id + " must have weight >= 1");
            if (!RarityDefinitions.ALL.contains(RarityDefinitions.normalize(reward.rarity))) errors.add("Reward " + reward.id + " in " + id + " has invalid rarity " + reward.rarity);
            if ("command".equalsIgnoreCase(reward.type)) {
                if (reward.commands == null || reward.commands.stream().allMatch(String::isBlank)) errors.add("Command reward " + reward.id + " in " + id + " has no commands");
            } else {
                ResourceLocation item = ResourceLocation.tryParse(reward.item);
                if (item == null || !ForgeRegistries.ITEMS.containsKey(item)) errors.add("Reward " + reward.id + " in " + id + " has unknown item " + reward.item);
                if (reward.minCount < 1 || reward.maxCount < reward.minCount) errors.add("Reward " + reward.id + " in " + id + " has invalid count range");
            }
        }
    }

    public static String normalizePolicy(String value) {
        return value == null ? "inherit" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
