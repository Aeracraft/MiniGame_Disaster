package com.xcreate.disaster.command;

import com.xcreate.disaster.DisasterPlugin;
import com.xcreate.disaster.permission.Permissions;
import com.xcreate.disaster.reputation.RatingWindow;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * 评价与互评命令。
 *
 * <p>评的是「刚打完的那一局」，不用手输局标识——结算时给参与者开过窗，这里从窗口取，
 * 窗口一过就没什么可评的了。</p>
 *
 * <p>标签一律从固定选项里挑，不收自填字符串。</p>
 */
public final class EvaluationCommand {

    private final DisasterPlugin plugin;

    public EvaluationCommand(DisasterPlugin plugin) {
        this.plugin = plugin;
    }

    public void rate(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "command.player-only");
            return;
        }
        if (!player.hasPermission(Permissions.PLAY)) {
            plugin.messages().send(player, "command.no-permission");
            return;
        }
        if (args.length == 0) {
            sendUsage(player, label);
            return;
        }
        Optional<Integer> stars = parseStars(args[0]);
        if (stars.isEmpty()) {
            sendUsage(player, label);
            return;
        }
        plugin.reputation().rate(player, stars.get(), tags(args, 1));
    }

    public void recommend(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "command.player-only");
            return;
        }
        if (!player.hasPermission(Permissions.PLAY)) {
            plugin.messages().send(player, "command.no-permission");
            return;
        }
        if (args.length == 0) {
            sendUsage(player, label);
            plugin.reputation().showReputation(player, player.getUniqueId());
            return;
        }
        plugin.reputation().recommend(player, args[0], tags(args, 1));
    }

    public List<String> completeRate(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 1 ? args[0] : "";
            return filter(List.of("1", "2", "3", "4", "5"), prefix);
        }
        return filter(plugin.pluginConfig().rating().tags(), args[args.length - 1]);
    }

    public List<String> completeRecommend(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        if (args.length <= 1) {
            String prefix = args.length == 1 ? args[0] : "";
            return filter(participantNames(player), prefix);
        }
        return filter(plugin.pluginConfig().rating().repTags(), args[args.length - 1]);
    }

    private void sendUsage(Player player, String label) {
        plugin.messages().send(player, "rating.usage", "label", label);
        plugin.messages().send(player, "rating.tag-hint",
                "tags", String.join("、", plugin.pluginConfig().rating().tags()));
        plugin.messages().send(player, "rating.rep-tag-hint",
                "tags", String.join("、", plugin.pluginConfig().rating().repTags()));
    }

    /** 本局其他参与者的名字，用于互评时的补全。 */
    private List<String> participantNames(Player player) {
        Optional<RatingWindow.Entry> entry = plugin.reputation().entryOf(player);
        if (entry.isEmpty()) {
            return List.of();
        }
        UUID self = player.getUniqueId();
        List<String> names = new ArrayList<>();
        entry.get().participants().forEach((playerId, name) -> {
            if (!playerId.equals(self) && !name.isBlank()) {
                names.add(name);
            }
        });
        return names;
    }

    private static Optional<Integer> parseStars(String input) {
        try {
            return Optional.of(Integer.parseInt(input));
        } catch (NumberFormatException error) {
            return Optional.empty();
        }
    }

    private static List<String> tags(String[] args, int from) {
        if (args.length <= from) {
            return List.of();
        }
        return List.of(args).subList(from, args.length);
    }

    private static List<String> filter(List<String> options, String prefix) {
        String needle = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(needle))
                .toList();
    }
}
