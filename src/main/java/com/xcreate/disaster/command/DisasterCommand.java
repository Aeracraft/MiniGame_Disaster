package com.xcreate.disaster.command;

import com.xcreate.disaster.DisasterPlugin;
import com.xcreate.disaster.api.replay.ReplayProvider;
import com.xcreate.disaster.config.StorageType;
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

    private static final String PERM_ADMIN_RELOAD = "disaster.admin.reload";

    private final DisasterPlugin plugin;

    public DisasterCommand(DisasterPlugin plugin) {
        this.plugin = plugin;
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
            default -> plugin.messages().send(sender, "command.unknown-subcommand", "input", args[0]);
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage("§8§m                                        ");
        sender.sendMessage("§6Disaster §7命令帮助");
        sender.sendMessage("§8» §f/" + label + " info §7— 查看运行环境与兼容信息");
        if (sender.hasPermission(PERM_ADMIN_RELOAD)) {
            sender.sendMessage("§8» §f/" + label + " reload §7— 重载配置与消息文件");
        }
        sender.sendMessage("§8 ");
        sender.sendMessage("§7玩法类命令（加入、投票、回放）尚未开放。");
        sender.sendMessage("§8§m                                        ");
    }

    private void sendInfo(CommandSender sender) {
        var server = plugin.serverVersion();
        var platform = plugin.platform();
        var config = plugin.pluginConfig();

        String storageLabel = config.storage().type() == StorageType.MYSQL
                ? "MySQL §7(" + config.storage().host() + ":" + config.storage().port()
                  + "/" + config.storage().database() + ") §8[尚未实装]"
                : "YAML";

        ReplayProvider provider = plugin.replayProvider();
        String replayLabel = provider == null
                ? "§8未接入 §7（一期仅预埋契约）"
                : "§a" + provider.id();

        sender.sendMessage("§8§m                                        ");
        sender.sendMessage("§6Disaster §7v" + plugin.getDescription().getVersion());
        sender.sendMessage("§8» §7服务端     §f" + platform.brand());
        sender.sendMessage("§8» §7Minecraft  §f" + server
                + " §8(最低支持 " + com.xcreate.disaster.compat.ServerVersion.MIN_SUPPORTED + ")");
        sender.sendMessage("§8» §7运行平台   §f" + (platform.isPaper() ? "Paper" : "Spigot")
                + (platform.supportsAdventure() ? " §7(Adventure 可用)" : ""));
        sender.sendMessage("§8» §7数据存储   §f" + storageLabel);
        sender.sendMessage("§8» §7房间上限   §f" + config.rooms().maxRooms());
        sender.sendMessage("§8» §7回放引擎   " + replayLabel);
        sender.sendMessage("§8§m                                        ");
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission(PERM_ADMIN_RELOAD)) {
            plugin.messages().send(sender, "command.no-permission");
            return;
        }
        plugin.reloadAll();
        sender.sendMessage(plugin.messages().prefix() + "§a配置与消息文件已重载。");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        List<String> options = new ArrayList<>();
        options.add("help");
        options.add("info");
        if (sender.hasPermission(PERM_ADMIN_RELOAD)) {
            options.add("reload");
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.startsWith(prefix)).toList();
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
