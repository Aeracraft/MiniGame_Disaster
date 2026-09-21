package com.xcreate.disaster.command;

import com.xcreate.disaster.DisasterPlugin;
import com.xcreate.disaster.api.replay.ReplayProvider;
import com.xcreate.disaster.compat.ServerVersion;
import com.xcreate.disaster.disaster.DisasterTier;
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
 * <p>本身只处理 help / info / reload，其余转发给对应的子命令对象；
 * 投票与回放等子命令等对应功能落地后再挂进来。</p>
 */
public final class DisasterRootCommand implements CommandExecutor, TabCompleter {

    private final DisasterPlugin plugin;
    private final MapCommand mapCommand;
    private final RoomCommand roomCommand;
    private final DisasterCommand disasterCommand;

    public DisasterRootCommand(DisasterPlugin plugin) {
        this.plugin = plugin;
        this.mapCommand = new MapCommand(plugin);
        this.roomCommand = new RoomCommand(plugin);
        this.disasterCommand = new DisasterCommand(plugin);
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
            case "join", "j" -> roomCommand.join(sender, label, tail(args));
            case "leave", "quit" -> roomCommand.leave(sender);
            case "reload" -> reload(sender);
            case "map" -> mapCommand.execute(sender, label, tail(args));
            case "room" -> roomCommand.execute(sender, label, tail(args));
            case "disaster", "ds" -> disasterCommand.execute(sender, label, tail(args));
            default -> plugin.messages().send(sender, "command.unknown-subcommand", "input", args[0]);
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage("§8§m                                        ");
        sender.sendMessage("§6Disaster §7命令帮助");
        if (sender.hasPermission(Permissions.PLAY)) {
            sender.sendMessage("§8» §f/" + label + " join [地图] §7— 加入对局");
            sender.sendMessage("§8» §f/" + label + " leave §7— 离开当前对局");
        }
        sender.sendMessage("§8» §f/" + label + " info §7— 查看运行环境与兼容信息");
        if (sender.hasPermission(Permissions.ADMIN_ROOM)) {
            sender.sendMessage("§8» §f/" + label + " room §7— 房间管理（§f" + label + " room§7）");
        }
        if (sender.hasPermission(Permissions.ADMIN_MAP)) {
            sender.sendMessage("§8» §f/" + label + " map §7— 地图管理与标点（§f" + label + " map§7）");
        }
        if (sender.hasPermission(Permissions.ADMIN_DISASTER)) {
            sender.sendMessage("§8» §f/" + label + " disaster §7— 灾种与落点预演（§f"
                    + label + " disaster§7）");
        }
        if (sender.hasPermission(Permissions.ADMIN_RELOAD)) {
            sender.sendMessage("§8» §f/" + label + " reload §7— 重载配置、消息、地图与灾种定义");
        }
        sender.sendMessage("§8 ");
        sender.sendMessage("§7投票选图与录像回放尚未开放。");
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
        sender.sendMessage("§8» §7房间       §f" + plugin.rooms().rooms().size()
                + " §7间，排队 §f" + plugin.rooms().queueSize() + " §7人"
                + " §8(上限 " + config.rooms().maxRooms() + ")");
        sender.sendMessage("§8» §7房间来源   §f" + plugin.provisioner().id());
        sender.sendMessage("§8» §7灾种       §f"
                + plugin.disasters().byTier(DisasterTier.PRIMARY).size()
                + " §7主 / §f" + plugin.disasters().byTier(DisasterTier.SECONDARY).size()
                + " §7次"
                + " §8(每波 " + config.game().primaryPerWave() + " 主，次灾 "
                + Math.round(config.game().secondaryDisasterChance() * 100) + "%)");
        sender.sendMessage("§8» §7回放引擎   " + replayLabel);
        sender.sendMessage("§8§m                                        ");
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission(Permissions.ADMIN_RELOAD)) {
            plugin.messages().send(sender, "command.no-permission");
            return;
        }
        plugin.reloadAll();
        sender.sendMessage(plugin.messages().prefix() + "§a配置、消息、地图与灾种定义已重载。");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length > 1 && args[0].equalsIgnoreCase("map")) {
            return mapCommand.complete(sender, tail(args));
        }
        if (args.length > 1 && args[0].equalsIgnoreCase("room")) {
            return roomCommand.complete(sender, tail(args));
        }
        if (args.length > 1 && (args[0].equalsIgnoreCase("disaster")
                || args[0].equalsIgnoreCase("ds"))) {
            return disasterCommand.complete(sender, tail(args));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("join")
                || args[0].equalsIgnoreCase("j"))) {
            return filterByPrefix(mapIds(), args[1]);
        }
        if (args.length != 1) {
            return List.of();
        }

        List<String> options = new ArrayList<>();
        options.add("help");
        options.add("info");
        if (sender.hasPermission(Permissions.PLAY)) {
            options.add("join");
            options.add("leave");
        }
        if (sender.hasPermission(Permissions.ADMIN_ROOM)) {
            options.add("room");
        }
        if (sender.hasPermission(Permissions.ADMIN_MAP)) {
            options.add("map");
        }
        if (sender.hasPermission(Permissions.ADMIN_DISASTER)) {
            options.add("disaster");
        }
        if (sender.hasPermission(Permissions.ADMIN_RELOAD)) {
            options.add("reload");
        }
        return filterByPrefix(options, args[0]);
    }

    private List<String> mapIds() {
        List<String> ids = new ArrayList<>();
        plugin.maps().playable().forEach(map -> ids.add(map.id()));
        return ids;
    }

    private static List<String> filterByPrefix(List<String> options, String prefix) {
        String needle = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(needle))
                .toList();
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
