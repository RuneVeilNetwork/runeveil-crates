package com.runeveil.crates.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Map;

public final class MessageUtil {
    private MessageUtil() {
    }

    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }

        String text = input.replace('&', '\u00A7');
        MutableComponent result = Component.empty();
        Style currentStyle = Style.EMPTY;
        StringBuilder segment = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '\u00A7' && i + 1 < text.length()) {
                if (!segment.isEmpty()) {
                    result.append(Component.literal(segment.toString()).withStyle(currentStyle));
                    segment.setLength(0);
                }
                char code = Character.toLowerCase(text.charAt(++i));
                ChatFormatting formatting = ChatFormatting.getByCode(code);
                if (formatting != null) {
                    currentStyle = formatting == ChatFormatting.RESET ? Style.EMPTY : currentStyle.applyFormat(formatting);
                }
            } else {
                segment.append(character);
            }
        }

        if (!segment.isEmpty()) {
            result.append(Component.literal(segment.toString()).withStyle(currentStyle));
        }

        return result;
    }

    public static Component format(String template, Map<String, String> placeholders) {
        String resolved = template;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return parse(resolved);
    }

    public static Component format(String template, String placeholder, String value) {
        return format(template, Map.of(placeholder, value == null ? "" : value));
    }

    public static Component format(String template, String p1, String v1, String p2, String v2) {
        return format(template, Map.of(p1, v1 == null ? "" : v1, p2, v2 == null ? "" : v2));
    }

    public static Component format(String template, String p1, String v1, String p2, String v2, String p3, String v3) {
        return format(template, Map.of(
                p1, v1 == null ? "" : v1,
                p2, v2 == null ? "" : v2,
                p3, v3 == null ? "" : v3
        ));
    }
}
