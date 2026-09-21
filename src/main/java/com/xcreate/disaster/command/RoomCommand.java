package com.xcreate.disaster.command;

import com.xcreate.disaster.DisasterPlugin;
import com.xcreate.disaster.permission.Permissions;
import com.xcreate.disaster.room.JoinOutcome;
import com.xcreate.disaster.room.Room;
import com.xcreate.disaster.room.RoomManager;
import com.xcreate.disaster.room.WorldFolder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * 房间相关命令。
 *
 * <p>{@code /ds join} 与 {@code /ds leave} 面向玩家；{@code /ds room ...} 面向管理员。
 * 列表一类状态输出在命令层直接拼——它们是给人现场看的诊断信息，拆成消息键反而更难改。</p>
 */
public final class RoomCommand {

    private final DisasterPlugin plugin;

    public RoomCommand(DisasterPlugin plugin) {
        this.plugin = plugin;
    }

    // ---- /ds join [地图] ----

    public void join(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "command.player-only");
            return;
        }
        if (!player.hasPermission(Permissions.PLAY)) {
            plugin.messages().send(sender, "command.no-permission");
            return;
        }

        String wanted = args.length > 0 ? args[0].trim() : null;
        if (wanted != null && !wanted.isEmpty() && !plugin.maps().exists(wanted)) {
            plugin.messages().send(player, "map.not-found", "id", wanted);
            return;
        }

        JoinOutcome outcome = plugin.rooms().join(player, wanted);
        switch (outcome.status()) {
            case JOINED -> plugin.messages().send(player, "game.joined-room",
                    "room", outcome.room().id(), "map", outcome.room().map().displayName());
            case CREATING -> plugin.messages().send(player, "game.room-creating",
                    "room", outcome.room().id(), "map", outcome.room().map().displayName());
            case ALREADY_IN -> plugin.messages().send(player, "game.already-in-room",
                    "room", outcome.room().id());
            case QUEUED -> plugin.messages().send(player, "game.queued",
                    "position", outcome.detail());
            case FAILED -> sendFailure(player, outcome.detail());
        }
    }

    // ---- /ds leave ----

    public void leave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "command.player-only");
            return;
        }
        if (!player.hasPermission(Permissions.PLAY)) {
            plugin.messages().send(sender, "command.no-permission");
            return;
        }
        if (!plugin.rooms().leave(player)) {
            plugin.messages().send(player, "game.not-in-room");
            return;
        }
        plugin.messages().send(player, "game.left-room");
    }

    // ---- /ds room ... ----

    public void execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN_ROOM)) {
            plugin.messages().send(sender, "command.no-permission");
            return;
        }
        if (args.length == 0) {
            sendUsage(sender, label);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> list(sender);
            case "info" -> info(sender, arg(args, 1));
            case "close" -> close(sender, arg(args, 1));
            case "closeall" -> closeAll(sender);
            case "kick" -> kick(sender, arg(args, 1));
            case "gc" -> collect(sender);
            default -> plugin.messages().send(sender, "command.unknown-subcommand", "input", args[0]);
        }
    }

    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 1 ? args[0] : "";
            return filter(List.of("list", "info", "close", "closeall", "kick", "gc"), prefix);
        }
        String prefix = args[args.length - 1];
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "info", "close" -> filter(roomIds(), prefix);
            case "kick" -> filter(onlineNames(), prefix);
            default -> List.of();
        };
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage("§8§m                                        ");
        sender.sendMessage("§6房间 §7命令");
        sender.sendMessage("§8» §f/" + label + " room list §7— 列出全部房间");
        sender.sendMessage("§8» §f/" + label + " room info <房间> §7— 查看详情");
        sender.sendMessage("§8» §f/" + label + " room close <房间> §7— 关闭房间");
        sender.sendMessage("§8» §f/" + label + " room closeall §7— 关闭全部");
        sender.sendMessage("§8» §f/" + label + " room kick <玩家> §7— 踢出玩家");
        sender.sendMessage("§8» §f/" + label + " room gc §7— 立即回收空闲房间");
        sender.sendMessage("§8§m                                        ");
    }

    private void list(CommandSender sender) {
        RoomManager rooms = plugin.rooms();
        List<Room> all = new ArrayList<>(rooms.rooms());
        int max = plugin.pluginConfig().rooms().maxRooms();

        sender.sendMessage("§8§m                                        ");
        sender.sendMessage("§6房间 §7共 " + all.size() + " 间（上限 " + max + "）"
                + "，排队 " + rooms.queueSize() + " 人");
        if (all.isEmpty()) {
            sender.sendMessage("§8» §7当前没有房间。");
        }
        for (Room room : all) {
            sender.sendMessage("§8» §f" + room.id()
                    + " §7" + room.map().displayName()
                    + " §8| " + stateLabel(room)
                    + " §8| §f" + room.size() + "/" + rooms.capacity(room)
                    + " §8| §7" + uptime(room));
        }
        sender.sendMessage("§8§m                                        ");
    }

    private void info(CommandSender sender, String token) {
        Room room = find(sender, token);
        if (room == null) {
            return;
        }
        World world = room.world();
        String folder = room.folder() == null
                ? "§8未就绪"
                : "§f" + room.folder().getName() + " §7("
                  + WorldFolder.humanSize(WorldFolder.sizeOf(room.folder())) + ")";

        sender.sendMessage("§8§m                                        ");
        sender.sendMessage("§6房间 §f" + room.id());
        sender.sendMessage("§8» §7地图     §f" + room.mapId() + " §7(" + room.map().displayName() + ")");
        sender.sendMessage("§8» §7状态     " + stateLabel(room));
        sender.sendMessage("§8» §7世界     " + (world == null ? "§8未加载" : "§a已加载"));
        sender.sendMessage("§8» §7人数     §f" + room.size() + "/" + plugin.rooms().capacity(room)
                + " §8(最少 " + plugin.rooms().requiredPlayers(room) + " 人开局)");
        sender.sendMessage("§8» §7存在     §f" + uptime(room));
        sender.sendMessage("§8» §7目录     " + folder);
        sender.sendMessage("§8» §7在座     §f" + memberNames(room));
        plugin.matches().matchOf(room.id()).ifPresent(match -> sender.sendMessage(
                "§8» §7对局     §f" + match.matchId() + " §7第 §f" + match.waveIndex()
                        + " §7波，存活 §f" + match.alive().size() + "/"
                        + match.participants().size() + " §8("
                        + match.elapsedSeconds(System.currentTimeMillis()) + "s)"));
        if (!room.note().isEmpty()) {
            sender.sendMessage("§8» §7备注     §c" + room.note());
        }
        sender.sendMessage("§8§m                                        ");
    }

    private void close(CommandSender sender, String token) {
        Room room = find(sender, token);
        if (room == null) {
            return;
        }
        String id = room.id();
        plugin.rooms().destroy(room, "admin");
        sender.sendMessage(plugin.messages().prefix() + "§a已关闭房间 §f" + id + "§a。");
    }

    private void closeAll(CommandSender sender) {
        int count = plugin.rooms().destroyAll("admin");
        sender.sendMessage(plugin.messages().prefix() + "§a已关闭 §f" + count + " §a个房间。");
    }

    private void kick(CommandSender sender, String name) {
        if (name == null) {
            sender.sendMessage("§8» §7用法：§f/ds room kick <玩家>");
            return;
        }
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            plugin.messages().send(sender, "room.player-offline", "player", name);
            return;
        }
        if (!plugin.rooms().leave(target)) {
            plugin.messages().send(sender, "game.not-in-room");
            return;
        }
        plugin.messages().send(target, "room.kicked", "staff", sender.getName());
        sender.sendMessage(plugin.messages().prefix() + "§a已将 §f" + target.getName() + " §a移出房间。");
    }

    private void collect(CommandSender sender) {
        int count = plugin.rooms().reap();
        sender.sendMessage(plugin.messages().prefix() + "§a已回收 §f" + count + " §a个空闲房间。");
    }

    private Room find(CommandSender sender, String token) {
        if (token == null) {
            sender.sendMessage("§8» §7用法：§f/ds room list §7查看现有房间。");
            return null;
        }
        Optional<Room> found = plugin.rooms().byId(token);
        if (found.isEmpty()) {
            plugin.messages().send(sender, "room.not-found", "room", token);
            return null;
        }
        return found.get();
    }

    private void sendFailure(Player player, String reason) {
        switch (reason) {
            case "map-not-playable" -> plugin.messages().send(player, "room.map-not-playable");
            case "no-map" -> plugin.messages().send(player, "map.select-empty");
            case "queue-full" -> plugin.messages().send(player, "room.queue-full");
            case "world-name-exhausted" -> plugin.messages().send(player, "room.name-exhausted");
            default -> plugin.messages().send(player, "room.create-failed", "reason", reason);
        }
    }

    private String memberNames(Room room) {
        if (room.isEmpty()) {
            return "§8（空）";
        }
        List<String> names = new ArrayList<>();
        for (UUID playerId : room.players()) {
            Player player = Bukkit.getPlayer(playerId);
            names.add(player == null ? playerId.toString().substring(0, 8) : player.getName());
        }
        return String.join("§7, §f", names);
    }

    private static String stateLabel(Room room) {
        return switch (room.state()) {
            case CREATING -> "§e准备中";
            case IDLE -> "§a空闲";
            case WAITING -> "§a等候中";
            case COUNTDOWN -> "§6倒计时";
            case RUNNING -> "§c进行中";
            case ENDING -> "§6结算中";
            case RESETTING -> "§e重置中";
            case CLOSED -> "§8已关闭";
        };
    }

    private static String uptime(Room room) {
        long seconds = room.aliveMillis() / 1000;
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private List<String> roomIds() {
        List<String> ids = new ArrayList<>();
        for (Room room : plugin.rooms().rooms()) {
            ids.add(room.id());
        }
        return ids;
    }

    private static List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private static List<String> filter(List<String> options, String prefix) {
        String needle = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(needle)) {
                matches.add(option);
            }
        }
        return matches;
    }

    private static String arg(String[] args, int index) {
        return index < args.length ? args[index] : null;
    }
}
