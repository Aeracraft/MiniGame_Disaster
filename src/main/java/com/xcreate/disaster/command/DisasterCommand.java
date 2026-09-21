package com.xcreate.disaster.command;

import com.xcreate.disaster.DisasterPlugin;
import com.xcreate.disaster.config.PluginConfig;
import com.xcreate.disaster.disaster.DisasterDefinition;
import com.xcreate.disaster.disaster.DisasterRoll;
import com.xcreate.disaster.disaster.DisasterTier;
import com.xcreate.disaster.disaster.SpawnContext;
import com.xcreate.disaster.disaster.SpawnOutcome;
import com.xcreate.disaster.disaster.SpawnPlanner;
import com.xcreate.disaster.disaster.WaveRoller;
import com.xcreate.disaster.disaster.WorldTerrain;
import com.xcreate.disaster.map.MapDefinition;
import com.xcreate.disaster.map.MapPoint;
import com.xcreate.disaster.permission.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * 灾种与落点命令。
 *
 * <p>{@code roll} 是干跑掷骰，{@code try} 是落点预演。两者都不改动任何房间或世界，
 * 只在聊天框里把结果打出来——灾种权重和边界标得对不对，靠嘴上说没用，跑一遍才知道。</p>
 *
 * <p>干跑用独立的随机源，不碰正在进行的对局的随机序列。</p>
 */
public final class DisasterCommand {

    private static final int DEFAULT_ROLLS = 10;
    private static final int MAX_ROLLS = 200;
    private static final int MAX_POINTS_SHOWN = 12;

    private final DisasterPlugin plugin;

    public DisasterCommand(DisasterPlugin plugin) {
        this.plugin = plugin;
    }

    public void execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN_DISASTER)) {
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
            case "roll" -> roll(sender, arg(args, 1));
            case "try" -> preview(sender, arg(args, 1), arg(args, 2));
            case "reload" -> reload(sender);
            default -> plugin.messages().send(sender, "command.unknown-subcommand", "input", args[0]);
        }
    }

    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 1 ? args[0] : "";
            return filter(List.of("list", "info", "roll", "try", "reload"), prefix);
        }
        String prefix = args[args.length - 1];
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "info", "try" -> filter(disasterIds(), prefix);
            case "try-map" -> filter(mapIds(), prefix);
            default -> List.of();
        };
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage("§8§m                                        ");
        sender.sendMessage("§6灾难 §7命令");
        sender.sendMessage("§8» §f/" + label + " disaster list §7— 列出全部灾种");
        sender.sendMessage("§8» §f/" + label + " disaster info <灾种> §7— 查看单个灾种");
        sender.sendMessage("§8» §f/" + label + " disaster roll [波数] §7— 干跑掷骰");
        sender.sendMessage("§8» §f/" + label + " disaster try <灾种> [地图] §7— 落点预演");
        sender.sendMessage("§8» §f/" + label + " disaster reload §7— 重扫灾种目录");
        sender.sendMessage("§8§m                                        ");
    }

    private void list(CommandSender sender) {
        List<DisasterDefinition> primary = plugin.disasters().byTier(DisasterTier.PRIMARY);
        List<DisasterDefinition> secondary = plugin.disasters().byTier(DisasterTier.SECONDARY);

        sender.sendMessage("§8§m                                        ");
        sender.sendMessage("§6灾种 §7共 " + (primary.size() + secondary.size()) + " 个"
                + "（主 " + primary.size() + " / 次 " + secondary.size() + "）");
        if (primary.isEmpty() && secondary.isEmpty()) {
            sender.sendMessage("§8» §7一个都没有。检查 §fplugins/Disaster/disasters/ §7目录。");
        }
        for (DisasterDefinition definition : primary) {
            sender.sendMessage(line(definition));
        }
        for (DisasterDefinition definition : secondary) {
            sender.sendMessage(line(definition));
        }
        sender.sendMessage("§8§m                                        ");
    }

    private static String line(DisasterDefinition definition) {
        String head = definition.enabled() ? "§f" : "§8";
        String tail = definition.enabled() ? "" : " §8(已禁用)";
        return "§8» " + head + definition.id()
                + " §7" + definition.displayName()
                + " §8| §7" + definition.tier().label()
                + " §8| §7权重 " + trim(definition.weight())
                + " §8| §7" + definition.strategy().label()
                + points(definition)
                + tail;
    }

    private static String points(DisasterDefinition definition) {
        if (!definition.producesPoints()) {
            return "";
        }
        if (definition.strategy() == com.xcreate.disaster.disaster.SpawnStrategy.AROUND_EACH_PLAYER) {
            return " ×" + definition.pointCount() + "/人";
        }
        return " ×" + definition.pointCount();
    }

    private void info(CommandSender sender, String token) {
        DisasterDefinition definition = find(sender, token);
        if (definition == null) {
            return;
        }
        PluginConfig config = plugin.pluginConfig();
        double cap = config.disasters().weightCap();

        sender.sendMessage("§8§m                                        ");
        sender.sendMessage("§6灾种 §f" + definition.id());
        sender.sendMessage("§8» §7名称     §f" + definition.displayName());
        sender.sendMessage("§8» §7层级     §f" + definition.tier().label());
        sender.sendMessage("§8» §7状态     " + (definition.enabled() ? "§a启用" : "§c已禁用"));
        sender.sendMessage("§8» §7权重     §f" + trim(definition.weight())
                + " §8(上限 " + trim(cap) + " 截断后 " + trim(definition.effectiveWeight(cap)) + ")");
        sender.sendMessage("§8» §7落点     §f" + definition.strategy().name()
                + " §7" + definition.strategy().label() + points(definition));
        sender.sendMessage("§8» §7参数     §f" + definition.options().shortText());
        sender.sendMessage("§8» §7固定落点 §7在地图定义里用 §f/ds map anchor <地图> "
                + definition.id() + " §7指定，标了就不再随机");
        sender.sendMessage("§8§m                                        ");
    }

    /** 干跑若干波，把每波结果与总频次打出来。 */
    private void roll(CommandSender sender, String raw) {
        int waves = parse(raw, DEFAULT_ROLLS);
        if (waves <= 0) {
            waves = DEFAULT_ROLLS;
        }
        waves = Math.min(waves, MAX_ROLLS);

        PluginConfig config = plugin.pluginConfig();
        Random random = config.disasters().hasFixedSeed()
                ? new Random(config.disasters().randomSeed())
                : new Random();
        WaveRoller roller = new WaveRoller(plugin.disasters(), config, random);

        Map<String, Integer> tally = new LinkedHashMap<>();
        List<String> active = new ArrayList<>();

        sender.sendMessage("§8§m                                        ");
        sender.sendMessage("§6掷骰预演 §7共 " + waves + " 波"
                + (config.disasters().hasFixedSeed()
                ? "（固定种子 " + config.disasters().randomSeed() + "）" : ""));
        for (int wave = 1; wave <= waves; wave++) {
            DisasterRoll result = roller.roll(wave, active);
            sender.sendMessage("§8» §7第 " + wave + " 波  §f" + result.summary());
            for (DisasterDefinition definition : result.all()) {
                tally.merge(definition.displayName(), 1, Integer::sum);
                active.add(definition.id());
            }
        }
        sender.sendMessage("§8 ");
        sender.sendMessage("§7出现次数： §f" + (tally.isEmpty() ? "无" : join(tally)));
        sender.sendMessage("§8§m                                        ");
    }

    /** 在一张真实地图上跑一次落点规划，不改动任何方块。 */
    private void preview(CommandSender sender, String disasterToken, String mapToken) {
        DisasterDefinition definition = find(sender, disasterToken);
        if (definition == null) {
            return;
        }
        MapDefinition map = resolveMap(sender, mapToken);
        if (map == null) {
            return;
        }
        if (map.bounds() == null) {
            plugin.messages().send(sender, "disaster.no-bounds", "id", map.id());
            return;
        }

        World world = resolveWorld(map);
        if (world == null) {
            plugin.messages().send(sender, "disaster.world-missing",
                    "id", map.id(), "world", map.templateFolder());
            return;
        }

        List<MapPoint> players = playersIn(world);
        boolean standIn = players.isEmpty();
        if (standIn) {
            players = standInPlayers(map, world.getName());
        }
        if (players.isEmpty()) {
            sender.sendMessage(plugin.messages().prefix()
                    + "§c这张地图既没有在线玩家也没有出生点，没法预演需要玩家的灾种。");
            return;
        }

        SpawnContext context = new SpawnContext(world.getName(), map.bounds(), players,
                inWorld(map.spawns(), world.getName()), map.anchors());
        SpawnOutcome outcome = plugin.spawnPlanner()
                .plan(definition, context, new WorldTerrain(world));

        sender.sendMessage("§8§m                                        ");
        sender.sendMessage("§6落点预演 §f" + definition.displayName()
                + " §7on §f" + map.id() + " §8(" + world.getName() + ")");
        sender.sendMessage("§8» §7范围     §f" + map.bounds().shortText());
        sender.sendMessage("§8» §7玩家     §f" + players.size() + " §7个"
                + (standIn ? " §8（场上没人，用出生点代替）" : ""));
        sender.sendMessage("§8» §7结果     " + (outcome.abandoned() ? "§c" : "§a") + outcome.summary());
        List<String> texts = outcome.pointTexts();
        for (int index = 0; index < texts.size() && index < MAX_POINTS_SHOWN; index++) {
            sender.sendMessage("§8  · §7" + texts.get(index));
        }
        if (texts.size() > MAX_POINTS_SHOWN) {
            sender.sendMessage("§8  · §7……另有 " + (texts.size() - MAX_POINTS_SHOWN) + " 个");
        }
        sender.sendMessage("§8§m                                        ");
    }

    private void reload(CommandSender sender) {
        int count = plugin.disasters().reload();
        plugin.messages().send(sender, "disaster.reloaded", "count", String.valueOf(count));
    }

    private DisasterDefinition find(CommandSender sender, String token) {
        if (token == null) {
            sender.sendMessage("§8» §7用法：§f/ds disaster list §7查看全部灾种。");
            return null;
        }
        Optional<DisasterDefinition> found = plugin.disasters().find(token);
        if (found.isEmpty()) {
            plugin.messages().send(sender, "disaster.not-found", "id", token);
            return null;
        }
        return found.get();
    }

    private MapDefinition resolveMap(CommandSender sender, String token) {
        if (token != null && !token.isBlank()) {
            Optional<MapDefinition> found = plugin.maps().find(token);
            if (found.isEmpty()) {
                plugin.messages().send(sender, "map.not-found", "id", token);
                return null;
            }
            return found.get();
        }
        List<MapDefinition> playable = plugin.maps().playable();
        if (playable.isEmpty()) {
            plugin.messages().send(sender, "map.select-empty");
            return null;
        }
        // 玩家站在哪张图里就预演哪张，不然取第一张可开局的
        if (sender instanceof Player player) {
            String standing = player.getWorld().getName();
            for (MapDefinition map : playable) {
                if (map.templateFolder().equalsIgnoreCase(standing)
                        || map.world().equalsIgnoreCase(standing)) {
                    return map;
                }
            }
        }
        return playable.get(0);
    }

    private World resolveWorld(MapDefinition map) {
        World world = map.world().isBlank() ? null : Bukkit.getWorld(map.world());
        if (world == null) {
            world = Bukkit.getWorld(map.templateFolder());
        }
        if (world != null) {
            return world;
        }
        if (!plugin.maps().templateExists(map)) {
            return null;
        }
        return new WorldCreator(map.templateFolder()).createWorld();
    }

    private static List<MapPoint> playersIn(World world) {
        List<MapPoint> points = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            points.add(MapPoint.of(player.getLocation()));
        }
        return points;
    }

    /** 场上没人时拿出生点当玩家位置，好让预演还能跑出个结果。 */
    private static List<MapPoint> standInPlayers(MapDefinition map, String worldName) {
        return inWorld(map.spawns(), worldName);
    }

    private static List<MapPoint> inWorld(List<MapPoint> points, String worldName) {
        List<MapPoint> moved = new ArrayList<>(points.size());
        for (MapPoint point : points) {
            moved.add(point.inWorld(worldName));
        }
        return moved;
    }

    private List<String> disasterIds() {
        List<String> ids = new ArrayList<>();
        plugin.disasters().all().forEach(definition -> ids.add(definition.id()));
        ids.sort(String::compareTo);
        return ids;
    }

    private List<String> mapIds() {
        List<String> ids = new ArrayList<>();
        plugin.maps().all().forEach(map -> ids.add(map.id()));
        ids.sort(String::compareTo);
        return ids;
    }

    private static String join(Map<String, Integer> tally) {
        StringBuilder text = new StringBuilder();
        tally.forEach((name, count) -> {
            if (text.length() > 0) {
                text.append("§7, §f");
            }
            text.append(name).append(' ').append(count);
        });
        return text.toString();
    }

    private static String trim(double value) {
        return value == Math.rint(value)
                ? String.valueOf((long) value)
                : String.valueOf(Math.round(value * 100) / 100.0);
    }

    private static int parse(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
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
