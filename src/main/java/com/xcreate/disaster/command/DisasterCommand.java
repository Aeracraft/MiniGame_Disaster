package com.xcreate.disaster.command;

import com.xcreate.disaster.DisasterPlugin;
import com.xcreate.disaster.api.replay.ReplayProvider;
import com.xcreate.disaster.compat.ServerVersion;
import com.xcreate.disaster.permission.Permissions;
import com.xcreate.disaster.storage.StorageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /disaster} 的命令分发器。
 *
 * <p>目前只有诊断类子命令，玩法类（加入、投票、回放）等对应功能落地后再挂进来。</p>
 */
public final class DisasterCommand implements CommandExecutor, TabCompleter {

    private final DisasterPlugin plugin;
    private final MapCommand mapCommand;

    public DisasterCommand(DisasterPlugin plugin) {
        this.plugin = plugin;
        this.mapCommand = new MapCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help", "?" -> sendHelp(sender, label);
            case "info", "version", "ver" -> sendInfo(sender);
            case "reload" -> reload(sender);
            case "map" -> mapCommand.execute(sender, label, tail(args));
            default -> plugin.messages().send(sender, "command.unknown-subcommand", "input", args[0]);
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage("§8§m                                        ");
        sender.sendMessage("§6Disaster §7命令帮助");
        sender.sendMessage("§8» §f/" + label + " info §7— 查看运行环境与兼容信息");
        if (sender.hasPermission(Permissions.ADMIN_MAP)) {
            sender.sendMessage("§8» §f/" + label + " map §7— 地图管理与标点（§f" + label + " map§7）");
        }
        if (sender.hasPermission(Permissions.ADMIN_RELOAD)) {
            sender.sendMessage("§8» §f/" + label + " reload §7— 重载配置、消息与地图定义");
        }
        sender.sendMessage("§8 ");
        sender.sendMessage("§7玩法类命令（加入、投票、回放）尚未开放。");
        sender.sendMessage("§8§m                                        ");
    }

    private void sendInfo(CommandSender sender) {
        var server = plugin.serverVersion();
        var platform = plugin.platform();
        var config = plugin.pluginConfig();

        StorageManager storage = plugin.storage();
        String storageLabel = storage.degraded()
                ? "§e" + storage.providerId() + " §7(降级：" + storage.degradedReason() + "§7)"
                : "§a" + storage.providerId();

        ReplayProvider provider = plugin.replayProvider();
        String replayLabel = provider == null
                ? "§8未接入 §7（一期仅预埋契约）"
                : "§a" + provider.id();

        String bridgeId = plugin.permissions().bridgeId();
        String bridgeLabel = (bridgeId.isEmpty() ? "§7原生权限" : "§a" + bridgeId)
                + " §8(缓存 " + config.permission().cacheTtlSeconds() + "s)";

        sender.sendMessage("§8§m                                        ");
        sender.sendMessage("§6Disaster §7v" + plugin.getDescription().getVersion());
        sender.sendMessage("§8» §7服务端     §f" + platform.brand());
        sender.sendMessage("§8» §7Minecraft  §f" + server
                + " §8(最低支持 " + ServerVersion.MIN_SUPPORTED + ")");
        sender.sendMessage("§8» §7运行平台   §f" + (platform.isPaper() ? "Paper" : "Spigot")
                + (platform.supportsAdventure() ? " §7(Adventure 可用)" : ""));
        sender.sendMessage("§8» §7权限桥接   " + bridgeLabel);
        sender.sendMessage("§8» §7数据存储   " + storageLabel);
        sender.sendMessage("§8» §7地图       §f" + plugin.maps().all().size()
                + " §7张，可开局 §f" + plugin.maps().playable().size() + " §7张"
                + " §8(抽图 " + config.maps().mode().lowerName() + ")");
        sender.sendMessage("§8» §7房间上限   §f" + config.rooms().maxRooms());
        sender.sendMessage("§8» §7回放引擎   " + replayLabel);
        sender.sendMessage("§8§m                                        ");
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission(Permissions.ADMIN_RELOAD)) {
            plugin.messages().send(sender, "command.no-permission");
            return;
        }
        plugin.reloadAll();
        sender.sendMessage(plugin.messages().prefix() + "§a配置与消息文件已重载。");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length > 1 && args[0].equalsIgnoreCase("map")) {
            return mapCommand.complete(sender, tail(args));
        }
        if (args.length != 1) {
            return List.of();
        }

        List<String> options = new ArrayList<>();
        options.add("help");
        options.add("info");
        if (sender.hasPermission(Permissions.ADMIN_MAP)) {
            options.add("map");
        }
        if (sender.hasPermission(Permissions.ADMIN_RELOAD)) {
            options.add("reload");
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.startsWith(prefix)).toList();
    }

    /** 去掉第一个参数，剩下的交给子命令。 */
    private static String[] tail(String[] args) {
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        return rest;
    }

    /** 玩家专用子命令的前置检查，非玩家时回一条提示。 */
    @SuppressWarnings("unused")
    private boolean requirePlayer(CommandSender sender) {
        if (sender instanceof Player) {
            return true;
        }
        plugin.messages().send(sender, "command.player-only");
        return false;
    }
}
