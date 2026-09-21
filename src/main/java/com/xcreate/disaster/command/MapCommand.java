package com.xcreate.disaster.command;

import com.xcreate.disaster.DisasterPlugin;
import com.xcreate.disaster.config.MessageService;
import com.xcreate.disaster.map.MapBounds;
import com.xcreate.disaster.map.MapCheck;
import com.xcreate.disaster.map.MapDefinition;
import com.xcreate.disaster.map.MapIssue;
import com.xcreate.disaster.map.MapPoint;
import com.xcreate.disaster.map.MapRegistry;
import com.xcreate.disaster.map.MapScaffold;
import com.xcreate.disaster.map.MapSelector;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * {@code /ds map} 的子命令集。
 *
 * <p>作图流程：{@code create} 建定义 → 在模板世界里 {@code pos1}/{@code pos2} 框范围并
 * {@code bounds} 写入 → 逐个 {@code spawn add} 标出生点 → {@code spec} 设观战点。
 * 想跳过做图的可以用 {@code scaffold} 直接生成一座测试城市。</p>
 *
 * <p>报错与状态提示走 messages.yml，成块的列表/详情输出直接拼在这里——前者服主可能会改文案，
 * 后者是结构化的诊断信息，拆成消息键反而更难维护。</p>
 */
public final class MapCommand {

    private static final String PERMISSION = "disaster.admin.map";

    private static final int DEFAULT_SCAFFOLD_SIZE = 97;

    private final DisasterPlugin plugin;
    private final Map<UUID, Location> cornerA = new HashMap<>();
    private final Map<UUID, Location> cornerB = new HashMap<>();

    public MapCommand(DisasterPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            plugin.messages().send(sender, "command.no-permission");
            return true;
        }
        if (args.length == 0) {
            help(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> list(sender);
            case "info", "check" -> info(sender, arg(args, 1));
            case "create" -> create(sender, args);
            case "delete", "remove" -> delete(sender, arg(args, 1));
            case "enable" -> toggle(sender, arg(args, 1), true);
            case "disable" -> toggle(sender, arg(args, 1), false);
            case "reload" -> reload(sender);
            case "pos1" -> markCorner(sender, 1);
            case "pos2" -> markCorner(sender, 2);
            case "bounds" -> bounds(sender, arg(args, 1));
            case "spawn" -> spawn(sender, args);
            case "spec", "spectator" -> spectator(sender, arg(args, 1));
            case "anchor" -> anchor(sender, args);
            case "loot" -> loot(sender, args);
            case "scaffold" -> scaffold(sender, args);
            case "tp", "teleport" -> teleport(sender, arg(args, 1));
            case "select" -> select(sender, arg(args, 1));
            default -> plugin.messages().send(sender, "command.unknown-subcommand", "input", args[0]);
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage("§8§m                                        ");
        sender.sendMessage("§6地图管理 §7用法");
        sender.sendMessage("§8» §f/" + label + " map list §7— 列出全部地图");
        sender.sendMessage("§8» §f/" + label + " map info <id> §7— 详情与校验");
        sender.sendMessage("§8» §f/" + label + " map create <id> [名称] §7— 新建地图定义");
        sender.sendMessage("§8» §f/" + label + " map delete <id> §7— 删除地图定义");
        sender.sendMessage("§8» §f/" + label + " map enable|disable <id> §7— 启用或停用");
        sender.sendMessage("§8» §f/" + label + " map reload §7— 重新扫描 maps 目录");
        sender.sendMessage("§8§m                                        ");
        sender.sendMessage("§6标点 §7站在图里执行");
        sender.sendMessage("§8» §f/" + label + " map pos1 §7/ §fpos2 §7— 取两个对角点");
        sender.sendMessage("§8» §f/" + label + " map bounds <id> §7— 把两个对角点写成边界");
        sender.sendMessage("§8» §f/" + label + " map spawn add <id> [名称] §7— 加入出生点");
        sender.sendMessage("§8» §f/" + label + " map spawn list|remove|clear <id> §7— 查看/移除/清空");
        sender.sendMessage("§8» §f/" + label + " map spec <id> §7— 设观战点");
        sender.sendMessage("§8» §f/" + label + " map anchor add|clear <id> <灾难id> §7— 固定落点");
        sender.sendMessage("§8» §f/" + label + " map loot add|clear <id> §7— 补给箱位置");
        sender.sendMessage("§8§m                                        ");
        sender.sendMessage("§6调试");
        sender.sendMessage("§8» §f/" + label + " map scaffold <id> [边长] §7— 生成测试城市");
        sender.sendMessage("§8» §f/" + label + " map tp <id> §7— 传送到模板世界");
        sender.sendMessage("§8» §f/" + label + " map select [id] §7— 试抽一次地图");
        sender.sendMessage("§8§m                                        ");
    }

    private void list(CommandSender sender) {
        MapRegistry registry = plugin.maps();
        List<MapDefinition> all = new ArrayList<>(registry.all());
        all.sort((a, b) -> a.id().compareTo(b.id()));
        List<MapDefinition> playable = registry.playable();

        sender.sendMessage("§8§m                                        ");
        sender.sendMessage("§6地图列表 §7共 " + all.size() + " 张，可开局 " + playable.size() + " 张");
        if (all.isEmpty()) {
            sender.sendMessage("§8» §7还没有地图，用 §f/ds map create <id> §7建一个，"
                    + "或 §f/ds map scaffold <id> §7直接生成测试城市。");
        }
        for (MapDefinition definition : all) {
            sender.sendMessage("§8» " + (definition.playable() ? "§a" : "§c")
                    + definition.id() + " §7" + definition.displayName()
                    + " §8| §7出生点 §f" + definition.spawns().size()
                    + " §8| §7边界 " + (definition.bounds() == null ? "§c未设" : "§a已设")
                    + " §8| §7权重 §f" + trim(definition.weight())
                    + (definition.enabled() ? "" : " §8| §c已停用"));
        }
        sender.sendMessage("§8§m                                        ");
    }

    private void info(CommandSender sender, String id) {
        Optional<MapDefinition> found = find(sender, id);
        if (found.isEmpty()) {
            return;
        }
        MapDefinition definition = found.get();

        sender.sendMessage("§8§m                                        ");
        sender.sendMessage("§6地图 §f" + definition.id() + " §7" + definition.displayName());
        sender.sendMessage("§8» §7状态     " + (definition.enabled() ? "§a已启用" : "§c已停用"));
        sender.sendMessage("§8» §7模板世界 §f" + blank(definition.world(), "未记录")
                + " §8(" + definition.templateFolder() + ", "
                + (plugin.maps().templateExists(definition) ? "§a存在§8" : "§c缺失§8") + ")");
        MapBounds bounds = definition.bounds();
        sender.sendMessage("§8» §7边界     "
                + (bounds == null ? "§c未设置" : "§f" + bounds.shortText()));
        sender.sendMessage("§8» §7出生点   §f" + definition.spawns().size() + " 个");
        sender.sendMessage("§8» §7观战点   "
                + (definition.spectatorSpawn() == null ? "§c未设置"
                : "§f" + definition.spectatorSpawn().shortText()));
        sender.sendMessage("§8» §7固定落点 §f" + definition.anchors().size() + " 类"
                + " §8| §7补给箱 §f" + definition.lootChests().size());
        sender.sendMessage("§8» §7权重     §f" + trim(definition.weight())
                + " §8| §7人数 §f" + players(definition));

        List<MapIssue> issues = MapCheck.check(definition);
        if (issues.isEmpty()) {
            sender.sendMessage("§8» §a校验通过，可以开局。");
        } else {
            sender.sendMessage("§8» §7校验结果：");
            for (MapIssue issue : issues) {
                sender.sendMessage("§8  " + MessageService.color(issue.text()));
            }
        }
        sender.sendMessage("§8§m                                        ");
    }

    private void create(CommandSender sender, String[] args) {
        String id = arg(args, 1);
        if (id == null) {
            sender.sendMessage("§8» §7用法：§f/ds map create <id> [显示名]");
            return;
        }
        id = id.toLowerCase(Locale.ROOT);
        if (!MapCheck.validId(id)) {
            plugin.messages().send(sender, "map.invalid-id");
            return;
        }
        if (plugin.maps().exists(id)) {
            plugin.messages().send(sender, "map.already-exists", "id", id);
            return;
        }

        String displayName = args.length > 2 ? String.join(" ", drop(args, 2)) : id;
        save(sender, MapDefinition.blank(id, displayName));
        plugin.messages().send(sender, "map.created", "id", id);
    }

    private void delete(CommandSender sender, String id) {
        Optional<MapDefinition> found = find(sender, id);
        if (found.isEmpty()) {
            return;
        }
        boolean removed = plugin.maps().delete(found.get().id());
        if (removed) {
            plugin.messages().send(sender, "map.deleted", "id", found.get().id());
        } else {
            plugin.messages().send(sender, "map.delete-failed", "id", found.get().id());
        }
    }

    private void toggle(CommandSender sender, String id, boolean enabled) {
        Optional<MapDefinition> found = find(sender, id);
        if (found.isEmpty()) {
            return;
        }
        if (save(sender, found.get().toBuilder().enabled(enabled).build())) {
            plugin.messages().send(sender, "map.toggled",
                    "id", found.get().id(), "state", enabled ? "启用" : "停用");
        }
    }

    private void reload(CommandSender sender) {
        int count = plugin.maps().reload();
        plugin.messages().send(sender, "map.reloaded", "count", String.valueOf(count));
    }

    private void markCorner(CommandSender sender, int index) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        Location location = player.getLocation().clone();
        Map<UUID, Location> corners = index == 1 ? cornerA : cornerB;
        corners.put(player.getUniqueId(), location);
        plugin.messages().send(sender, "map.pos-set",
                "which", index == 1 ? "pos1" : "pos2",
                "point", MapPoint.of(location).shortText());
    }

    private void bounds(CommandSender sender, String id) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        Optional<MapDefinition> found = find(sender, id);
        if (found.isEmpty()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Location a = cornerA.get(uuid);
        Location b = cornerB.get(uuid);
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) {
            plugin.messages().send(sender, "map.bounds-need-two");
            return;
        }
        if (!a.getWorld().equals(b.getWorld())) {
            plugin.messages().send(sender, "map.bounds-cross-world");
            return;
        }

        MapBounds bounds = MapBounds.between(a, b);
        MapDefinition definition = found.get().toBuilder()
                .world(a.getWorld().getName())
                .bounds(bounds)
                .build();
        if (save(sender, definition)) {
            plugin.messages().send(sender, "map.bounds-set",
                    "id", definition.id(), "bounds", bounds.shortText());
        }
    }

    private void spawn(CommandSender sender, String[] args) {
        String action = arg(args, 1);
        String id = arg(args, 2);
        if (action == null || id == null) {
            sender.sendMessage("§8» §7用法：§f/ds map spawn add|list|remove|clear <id>");
            return;
        }
        Optional<MapDefinition> found = find(sender, id);
        if (found.isEmpty()) {
            return;
        }
        MapDefinition definition = found.get();

        switch (action.toLowerCase(Locale.ROOT)) {
            case "add" -> {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return;
                }
                String name = args.length > 3 ? String.join(" ", drop(args, 3)) : "";
                MapPoint point = MapPoint.of(player.getLocation(), name);
                MapDefinition updated = definition.toBuilder().addSpawn(point).build();
                if (save(sender, updated)) {
                    plugin.messages().send(sender, "map.spawn-added",
                            "point", point.shortText(),
                            "count", String.valueOf(updated.spawns().size()));
                }
            }
            case "list" -> {
                sender.sendMessage("§8§m                                        ");
                sender.sendMessage("§6" + definition.id() + " §7的出生点（" + definition.spawns().size() + " 个）");
                List<MapPoint> spawns = definition.spawns();
                for (int index = 0; index < spawns.size(); index++) {
                    MapPoint point = spawns.get(index);
                    sender.sendMessage("§8» §f" + (index + 1) + ". §7"
                            + (point.name().isBlank() ? "" : point.name() + " §8")
                            + "§f" + point.shortText());
                }
                if (spawns.isEmpty()) {
                    sender.sendMessage("§8» §7一个都还没有，站到位置后执行 §f/ds map spawn add "
                            + definition.id());
                }
                sender.sendMessage("§8§m                                        ");
            }
            case "remove" -> {
                int index = parseInt(arg(args, 3), -1);
                if (index < 1) {
                    sender.sendMessage("§8» §7用法：§f/ds map spawn remove <id> <序号>");
                    return;
                }
                var builder = definition.toBuilder();
                if (!builder.removeSpawn(index)) {
                    plugin.messages().send(sender, "map.spawn-index-invalid", "id", definition.id());
                    return;
                }
                if (save(sender, builder.build())) {
                    plugin.messages().send(sender, "map.spawn-removed", "index", String.valueOf(index));
                }
            }
            case "clear" -> {
                if (save(sender, definition.toBuilder().clearSpawns().build())) {
                    plugin.messages().send(sender, "map.spawn-cleared", "id", definition.id());
                }
            }
            default -> sender.sendMessage("§8» §7用法：§f/ds map spawn add|list|remove|clear <id>");
        }
    }

    private void spectator(CommandSender sender, String id) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        Optional<MapDefinition> found = find(sender, id);
        if (found.isEmpty()) {
            return;
        }
        MapPoint point = MapPoint.of(player.getLocation(), "观战");
        MapDefinition updated = found.get().toBuilder().spectatorSpawn(point).build();
        if (save(sender, updated)) {
            plugin.messages().send(sender, "map.spectator-set", "point", point.shortText());
        }
    }

    private void anchor(CommandSender sender, String[] args) {
        String action = arg(args, 1);
        String id = arg(args, 2);
        String disasterId = arg(args, 3);
        if (action == null || id == null || disasterId == null) {
            sender.sendMessage("§8» §7用法：§f/ds map anchor add|clear <id> <灾难id>");
            return;
        }
        Optional<MapDefinition> found = find(sender, id);
        if (found.isEmpty()) {
            return;
        }
        MapDefinition definition = found.get();

        switch (action.toLowerCase(Locale.ROOT)) {
            case "add" -> {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return;
                }
                MapPoint point = MapPoint.of(player.getLocation());
                MapDefinition updated = definition.toBuilder()
                        .addAnchor(disasterId.toLowerCase(Locale.ROOT), point).build();
                if (save(sender, updated)) {
                    plugin.messages().send(sender, "map.anchor-added",
                            "disaster", disasterId, "point", point.shortText());
                }
            }
            case "clear" -> {
                MapDefinition updated = definition.toBuilder()
                        .clearAnchors(disasterId.toLowerCase(Locale.ROOT)).build();
                if (save(sender, updated)) {
                    plugin.messages().send(sender, "map.anchor-cleared", "disaster", disasterId);
                }
            }
            default -> sender.sendMessage("§8» §7用法：§f/ds map anchor add|clear <id> <灾难id>");
        }
    }

    private void loot(CommandSender sender, String[] args) {
        String action = arg(args, 1);
        String id = arg(args, 2);
        if (action == null || id == null) {
            sender.sendMessage("§8» §7用法：§f/ds map loot add|clear <id>");
            return;
        }
        Optional<MapDefinition> found = find(sender, id);
        if (found.isEmpty()) {
            return;
        }
        MapDefinition definition = found.get();

        switch (action.toLowerCase(Locale.ROOT)) {
            case "add" -> {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return;
                }
                MapPoint point = MapPoint.of(player.getLocation());
                MapDefinition updated = definition.toBuilder().addLootChest(point).build();
                if (save(sender, updated)) {
                    plugin.messages().send(sender, "map.loot-added", "point", point.shortText());
                }
            }
            case "clear" -> {
                if (save(sender, definition.toBuilder().clearLootChests().build())) {
                    plugin.messages().send(sender, "map.loot-cleared");
                }
            }
            default -> sender.sendMessage("§8» §7用法：§f/ds map loot add|clear <id>");
        }
    }

    private void scaffold(CommandSender sender, String[] args) {
        String id = arg(args, 1);
        if (id == null) {
            sender.sendMessage("§8» §7用法：§f/ds map scaffold <id> [边长]");
            return;
        }
        id = id.toLowerCase(Locale.ROOT);

        MapDefinition definition = plugin.maps().find(id).orElse(null);
        if (definition == null) {
            if (!MapCheck.validId(id)) {
                plugin.messages().send(sender, "map.invalid-id");
                return;
            }
            definition = MapDefinition.blank(id, id);
        }

        int size = MapScaffold.clampSize(parseInt(arg(args, 2), DEFAULT_SCAFFOLD_SIZE));
        String worldName = definition.templateFolder();
        World world = MapScaffold.prepareWorld(worldName);
        if (world == null) {
            plugin.messages().send(sender, "map.scaffold-world-failed", "world", worldName);
            return;
        }

        // 先把世界名落进定义，生成过程中服主中途重启也不会丢
        MapDefinition target = definition.toBuilder()
                .world(worldName)
                .templateFolder(worldName)
                .build();
        save(sender, target);

        final String mapId = id;
        final World targetWorld = world;
        plugin.messages().send(sender, "map.scaffold-started",
                "id", mapId, "size", String.valueOf(size));
        MapScaffold.start(plugin, world, size, mapId, generated -> {
            MapDefinition refreshed = plugin.maps().find(mapId).orElse(target);
            var builder = refreshed.toBuilder()
                    .world(targetWorld.getName())
                    .templateFolder(targetWorld.getName())
                    .bounds(generated.bounds())
                    .clearSpawns()
                    .spectatorSpawn(generated.spectatorSpawn());
            for (MapPoint point : generated.spawns()) {
                builder.addSpawn(point);
            }
            if (!save(sender, builder.build())) {
                return;
            }
            plugin.messages().send(sender, "map.scaffold-done",
                    "id", mapId, "blocks", String.valueOf(generated.blocks()));
            if (sender instanceof Player player) {
                player.teleport(new Location(targetWorld, 0.5,
                        targetWorld.getHighestBlockYAt(0, 0) + 2, 0.5, 0f, 0f));
            }
        });
    }

    private void teleport(CommandSender sender, String id) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        Optional<MapDefinition> found = find(sender, id);
        if (found.isEmpty()) {
            return;
        }
        MapDefinition definition = found.get();
        World world = Bukkit.getWorld(definition.templateFolder());
        if (world == null) {
            world = Bukkit.getWorld(definition.world());
        }
        if (world == null) {
            plugin.messages().send(sender, "map.template-not-loaded",
                    "world", definition.templateFolder());
            return;
        }
        player.teleport(world.getSpawnLocation());
    }

    private void select(CommandSender sender, String adminPick) {
        MapSelector selector = plugin.mapSelector();
        List<MapDefinition> pool = plugin.maps().playable();
        if (pool.isEmpty()) {
            plugin.messages().send(sender, "map.select-empty");
            return;
        }
        Optional<MapSelector.Pick> pick = selector.select(pool, adminPick, Map.of(), Set.of());
        if (pick.isEmpty()) {
            plugin.messages().send(sender, "map.select-empty");
            return;
        }
        plugin.messages().send(sender, "map.select-result",
                "id", pick.get().map().id(), "source", pick.get().source());
        sender.sendMessage("§8» §7候选 §f" + pool.size() + " §7张：§f"
                + String.join("§7, §f", pool.stream().map(MapDefinition::id).toList()));
    }

    private Optional<MapDefinition> find(CommandSender sender, String id) {
        if (id == null) {
            sender.sendMessage("§8» §7要指定地图 id。");
            return Optional.empty();
        }
        Optional<MapDefinition> found = plugin.maps().find(id);
        if (found.isEmpty()) {
            plugin.messages().send(sender, "map.not-found", "id", id);
        }
        return found;
    }

    /** 落盘。成功不吭声——各子命令自己会报一条有意义的提示。 */
    private boolean save(CommandSender sender, MapDefinition definition) {
        try {
            plugin.maps().save(definition);
            return true;
        } catch (IOException e) {
            plugin.messages().send(sender, "map.save-failed",
                    "id", definition.id(), "reason", String.valueOf(e.getMessage()));
            return false;
        }
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        plugin.messages().send(sender, "command.player-only");
        return null;
    }

    private static String arg(String[] args, int index) {
        return index < args.length ? args[index] : null;
    }

    private static String[] drop(String[] args, int from) {
        String[] rest = new String[args.length - from];
        System.arraycopy(args, from, rest, 0, rest.length);
        return rest;
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String players(MapDefinition definition) {
        String min = definition.minPlayers() == MapDefinition.FOLLOW_GLOBAL
                ? "默认" : String.valueOf(definition.minPlayers());
        String max = definition.maxPlayers() == MapDefinition.FOLLOW_GLOBAL
                ? "默认" : String.valueOf(definition.maxPlayers());
        return min + " ~ " + max;
    }

    public List<String> complete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length <= 1) {
            return filter(List.of("list", "info", "create", "delete", "enable", "disable", "reload",
                    "pos1", "pos2", "bounds", "spawn", "spec", "anchor", "loot",
                    "scaffold", "tp", "select"), args.length == 1 ? args[0] : "");
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        List<String> ids = plugin.maps().all().stream()
                .map(MapDefinition::id).sorted().toList();

        if (args.length == 2) {
            return switch (action) {
                case "spawn", "anchor", "loot" ->
                        filter(List.of("add", "list", "remove", "clear"), args[1]);
                case "create" -> List.of();
                default -> filter(ids, args[1]);
            };
        }
        if (args.length == 3) {
            return switch (action) {
                case "spawn", "anchor", "loot" -> filter(ids, args[2]);
                default -> List.of();
            };
        }
        if (args.length == 4 && action.equals("anchor")) {
            return plugin.maps().find(args[2])
                    .map(definition -> filter(List.copyOf(definition.anchors().keySet()), args[3]))
                    .orElseGet(List::of);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(lower)).toList();
    }
}
