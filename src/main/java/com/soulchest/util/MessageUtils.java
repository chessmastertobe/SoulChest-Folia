package com.soulchest.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

public final class MessageUtils {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.legacyAmpersand();

    private MessageUtils() {
        // Utility class - prevent instantiation
    }

    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(input);
    }

    public static Component parseLegacy(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        return LEGACY_SERIALIZER.deserialize(input);
    }

    public static Component parseAny(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        // Auto-detect MiniMessage vs legacy format
        if (input.contains("<") && input.contains(">")) {
            return parse(input);
        }
        return parseLegacy(input);
    }

    public static void send(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) {
            return;
        }
        sender.sendMessage(parse(message));
    }

    public static String stripTags(String input) {
        if (input == null) return "";
        return MINI_MESSAGE.stripTags(input);
    }
}