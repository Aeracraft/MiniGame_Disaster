package com.xcreate.disaster.reputation;

import com.xcreate.disaster.DisasterPlugin;
import com.xcreate.disaster.api.event.DisasterMatchRatedEvent;
import com.xcreate.disaster.api.event.DisasterPlayerRepEvent;
import com.xcreate.disaster.api.reputation.RatingVerdict;
import com.xcreate.disaster.api.reputation.ReputationProvider;
import com.xcreate.disaster.api.storage.MatchRating;
import com.xcreate.disaster.api.storage.ReputationEntry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 评价与互评的编排。
 *
 * <p>自己既不落库也不管防刷策略，那两件事归 {@link ReputationProvider}。这里管的是游戏侧
 * 那部分：从窗口取出「评的是哪一局」、过掉时间窗与参与者两道闸、把请求交给实现方、
 * 广播语义事件、把结果说给玩家听。</p>
 *
 * <p>存储不可用时只回一句「暂时收不了」，绝不让它把命令或对局收尾拖住。</p>
 */
public final class ReputationService {

    /** 清理过期登记的周期（tick）。窗口以分钟计，这个频率足够了。 */
    private static final long PRUNE_PERIOD_TICKS = 20L * 60L;

    private final DisasterPlugin plugin;
    private final ReputationProvider provider;
    private final RatingWindow window = new RatingWindow();

    private BukkitTask pruner;

    public ReputationService(DisasterPlugin plugin, ReputationProvider provider) {
        this.plugin = plugin;
        this.provider = provider;
    }

    public ReputationProvider provider() {
        return provider;
    }

    public RatingWindow window() {
        return window;
    }

    public boolean usable() {
        return plugin.pluginConfig().rating().enabled() && provider.available();
    }

    public void start() {
        pruner = Bukkit.getScheduler().runTaskTimer(plugin, this::prune,
                PRUNE_PERIOD_TICKS, PRUNE_PERIOD_TICKS);
    }

    public void stop() {
        if (pruner != null) {
            pruner.cancel();
            pruner = null;
        }
        window.clear();
    }

    /** 结算时给全体参与者开窗。窗口一过，这些人就不再能评这一局。 */
    public void open(Map<UUID, String> participants, String matchId) {
        if (!plugin.pluginConfig().rating().enabled()) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + plugin.pluginConfig().rating().windowMillis();
        window.open(participants, matchId, expiresAt);
    }

    /** 收下一条对局评价。 */
    public void rate(Player player, int stars, List<String> tags) {
        if (rejectIfUnusable(player)) {
            return;
        }
        RatingVerdict verdict = RatingGuard.checkStars(stars);
        if (verdict.accepted()) {
            verdict = RatingGuard.checkTags(tags, plugin.pluginConfig().rating().tagSet());
        }
        if (!verdict.accepted()) {
            report(player, verdict);
            return;
        }

        Optional<RatingWindow.Entry> entry = entryOf(player);
        verdict = RatingGuard.checkWindow(entry.isPresent(),
                entry.filter(open -> open.contains(player.getUniqueId())).isPresent());
        if (!verdict.accepted()) {
            report(player, verdict);
            return;
        }

        MatchRating rating = new MatchRating(entry.get().matchId(), player.getUniqueId(),
                stars, RatingGuard.distinct(tags), System.currentTimeMillis());
        provider.submit(rating).whenComplete((result, error) ->
                onMainThread(() -> {
                    if (error != null) {
                        fail(player, error);
                        return;
                    }
                    report(player, result);
                    if (result.accepted()) {
                        Bukkit.getPluginManager().callEvent(new DisasterMatchRatedEvent(
                                rating.matchId(), rating.raterId(), rating.stars(),
                                rating.issueTags()));
                    }
                }));
    }

    /** 收下一次互赞。目标必须是本局参与者，名字按不区分大小写匹配。 */
    public void recommend(Player player, String targetName, List<String> tags) {
        if (rejectIfUnusable(player)) {
            return;
        }
        Optional<RatingWindow.Entry> entry = entryOf(player);
        if (entry.isEmpty() || !entry.get().contains(player.getUniqueId())) {
            report(player, RatingVerdict.WINDOW_CLOSED);
            return;
        }

        UUID targetId = entry.get().idOf(targetName);
        if (targetId == null) {
            report(player, RatingVerdict.NOT_PARTICIPANT);
            return;
        }

        RatingVerdict verdict = RatingGuard.checkTags(tags,
                plugin.pluginConfig().rating().repTagSet());
        if (!verdict.accepted()) {
            report(player, verdict);
            return;
        }

        ReputationEntry rep = new ReputationEntry(entry.get().matchId(), player.getUniqueId(),
                targetId, RatingGuard.distinct(tags), System.currentTimeMillis());
        provider.recommend(rep).whenComplete((result, error) ->
                onMainThread(() -> {
                    if (error != null) {
                        fail(player, error);
                        return;
                    }
                    report(player, result);
                    if (result.accepted()) {
                        Bukkit.getPluginManager().callEvent(new DisasterPlayerRepEvent(
                                rep.matchId(), rep.fromId(), rep.toId(), rep.tags()));
                    }
                }));
    }

    /** 查荣誉值，异步取回来后回给玩家。 */
    public void showReputation(Player player, UUID targetId) {
        if (rejectIfUnusable(player)) {
            return;
        }
        provider.reputationOf(targetId).whenComplete((value, error) ->
                onMainThread(() -> {
                    if (error != null) {
                        fail(player, error);
                        return;
                    }
                    plugin.messages().send(player, "rating.reputation",
                            "value", String.valueOf(value == null ? 0L : value));
                }));
    }

    /** 清掉过期的窗口登记，顺带回收按局存的临时计数。 */
    public void prune() {
        for (String matchId : window.prune(System.currentTimeMillis())) {
            if (provider instanceof BuiltinReputationProvider builtin) {
                builtin.forget(matchId);
            }
        }
    }

    /** 重载配置后换一份给实现方。已经开着的窗口与已记的计数都留着。 */
    public void apply() {
        if (provider instanceof BuiltinReputationProvider builtin) {
            builtin.apply(plugin.pluginConfig().rating());
        }
    }

    /** 玩家当前能评价的那一局，没有则返回空。 */
    public Optional<RatingWindow.Entry> entryOf(Player player) {
        return window.of(player.getUniqueId(), System.currentTimeMillis());
    }

    private boolean rejectIfUnusable(Player player) {
        if (!plugin.pluginConfig().rating().enabled()) {
            report(player, RatingVerdict.DISABLED);
            return true;
        }
        if (!provider.available()) {
            report(player, RatingVerdict.STORAGE_UNAVAILABLE);
            return true;
        }
        return false;
    }

    private void report(Player player, RatingVerdict verdict) {
        plugin.messages().send(player,
                verdict.accepted() ? "rating.accepted" : "rating.rejected",
                "reason", verdict.label());
    }

    private void fail(Player player, Throwable error) {
        plugin.getLogger().warning("玩家 " + player.getName() + " 的评价提交失败："
                + error.getMessage());
        plugin.messages().send(player, "rating.rejected",
                "reason", RatingVerdict.STORAGE_UNAVAILABLE.label());
    }

    /** 存储回调不一定落在主线程，回执与事件广播都得挪回来。 */
    private void onMainThread(Runnable action) {
        if (Bukkit.isPrimaryThread()) {
            action.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, action);
    }
}
