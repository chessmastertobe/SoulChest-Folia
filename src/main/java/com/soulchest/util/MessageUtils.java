package com.soulchest.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

public final class MessageUtils {

    private static final MiniMessage MINI =
            MiniMessage.miniMessage();

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private MessageUtils() { }

    public static Component parse(String input) {
        if (input == null) return Component.empty();
        return MINI.deserialize(input);
    }

    public static Component parseLegacy(String input) {
        if (input == null) return Component.empty();
        return LEGACY.deserialize(input);
    }

    public static Component parseAny(String input) {
        if (input == null) return Component.empty();
        if (input.contains("<") && input.contains(">")) return parse(input);
        return parseLegacy(input);
    }

    public static void send(CommandSender sender, String miniMessage) {
        if (miniMessage == null || miniMessage.isEmpty()) return;
        sender.sendMessage(parse(miniMessage));
    }

    public static String stripColor(String input) {
        if (input == null) return "";
        return MINI.stripTags(input);
    }
}