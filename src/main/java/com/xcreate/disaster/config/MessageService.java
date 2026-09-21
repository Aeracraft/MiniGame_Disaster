package com.xcreate.disaster.config;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * 消息出口。
 *
 * <p>所有面向玩家的文案统一从这里走，代码里不硬编码。这样将来要加多语言
 * 只需要换实现，不必回头翻遍调用点。</p>
 */
public final class MessageService {

    private final FileConfiguration messages;
    private final String prefix;

    public MessageService(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        FileConfiguration loaded;
        try {
            loaded = YamlConfiguration.loadConfiguration(file);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "读取 messages.yml 失败，将使用内置默认值。", t);
            loaded = new YamlConfiguration();
        }

        // 用 jar 内的版本补齐用户文件里缺失的键，
        // 这样插件升级新增文案后，老配置文件不会显示成 null。
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                loaded.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8)));
                loaded.options().copyDefaults(true);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "合并默认 messages.yml 失败。", t);
        }

        this.messages = loaded;
        this.prefix = color(loaded.getString("prefix", ""));
    }

    /** 取一条原始文案（已染色，已替换 {prefix}）。 */
    public String raw(String key) {
        String value = messages.getString(key);
        if (value == null) {
            return "";
        }
        return color(value.replace("{prefix}", prefix));
    }

    /**
     * 取一条文案并填充占位符，占位符按 {名} 形式书写。
     *
     * @param key   消息键
     * @param pairs 交替出现的「名字, 值」序列，例如 {@code "room", "A1", "map", "city"}
     */
    public String get(String key, String... pairs) {
        String value = raw(key);
        if (pairs == null || pairs.length == 0) {
            return value;
        }
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            value = value.replace("{" + pairs[i] + "}", pairs[i + 1] == null ? "" : pairs[i + 1]);
        }
        return value;
    }

    /** 发送一条消息。文案为空时静默跳过（便于运维用留空来关掉某条提示）。 */
    public void send(CommandSender target, String key, String... pairs) {
        if (target == null) {
            return;
        }
        String message = get(key, pairs);
        if (!message.isEmpty()) {
            target.sendMessage(message);
        }
    }

    public boolean isEmpty(String key) {
        return raw(key).isEmpty();
    }

    public String prefix() {
        return prefix;
    }

    /** 把 &amp; 颜色代码转换成 Minecraft 的节符号。 */
    public static String color(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
