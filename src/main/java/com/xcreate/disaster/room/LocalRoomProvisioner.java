package com.xcreate.disaster.room;

import com.xcreate.disaster.api.room.ProvisionedRoom;
import com.xcreate.disaster.api.room.RoomProvisioner;
import com.xcreate.disaster.api.room.RoomRequest;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 本地房间来源：拷一份模板世界，加载成副本。
 *
 * <p>拷目录是阻塞 IO，一个世界几十到几百 MB，放在主线程会直接卡住整个服务器，
 * 所以拷贝走专用线程。世界加载必须在主线程，于是流程被切成两段：
 * 主线程取目录 → 异步拷贝 → 回主线程加载。房间在拷贝与加载期间处于
 * {@link RoomState#CREATING}，不接人。</p>
 *
 * <p>拷贝串行执行。多个房间同时拷会互相抢磁盘，谁也快不了，还容易把主线程的 TPS 拖下来。</p>
 */
public final class LocalRoomProvisioner implements RoomProvisioner {

    public static final String ID = "local";

    private final Plugin plugin;
    private final Logger logger;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(new WorkerFactory());

    /** 卸载了但没删掉的目录，下次拷贝前再试一次。删不掉通常是文件还被系统占着。 */
    private final Queue<File> orphans = new ConcurrentLinkedQueue<>();

    private volatile boolean closed;

    public LocalRoomProvisioner(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean available() {
        return !closed;
    }

    @Override
    public void provision(RoomRequest request, Consumer<ProvisionedRoom> callback) {
        File container = Bukkit.getWorldContainer();
        File target = new File(container, request.worldName());

        worker.execute(() -> {
            ProvisionedRoom failure = null;
            try {
                purgeOrphans();
                long bytes = WorldFolder.copy(request.templateFolder(), target);
                logger.info("已拷贝副本世界 " + request.worldName()
                        + "（" + WorldFolder.humanSize(bytes) + "），正在加载。");
            } catch (IOException e) {
                WorldFolder.delete(target);
                failure = ProvisionedRoom.failed(request.roomId(), request.worldName(), e.getMessage());
                logger.log(Level.WARNING, "拷贝副本世界失败：" + request.worldName(), e);
            }

            ProvisionedRoom error = failure;
            mainThread(() -> {
                if (error != null) {
                    callback.accept(error);
                    return;
                }
                World world = load(request);
                if (world == null) {
                    WorldFolder.delete(target);
                    callback.accept(ProvisionedRoom.failed(request.roomId(), request.worldName(),
                            "世界加载失败，详见控制台"));
                    return;
                }
                callback.accept(ProvisionedRoom.ready(request.roomId(), request.worldName(),
                        world, target));
            });
        });
    }

    @Override
    public void release(ProvisionedRoom room, Runnable whenDone) {
        World world = room.world();
        if (world != null && !Bukkit.unloadWorld(world, false)) {
            logger.warning("无法卸载副本世界 " + room.worldName() + "，可能有玩家仍留在其中。");
        }

        File folder = room.folder();
        if (folder == null) {
            runNow(whenDone);
            return;
        }

        worker.execute(() -> {
            if (!WorldFolder.delete(folder)) {
                logger.warning("副本目录 " + folder + " 暂时删不掉，留待下次清理。");
                orphans.add(folder);
            }
            mainThread(whenDone);
        });
    }

    @Override
    public void shutdown() {
        closed = true;
        worker.shutdown();
        try {
            // 关服时销毁的房间会往这里排队删目录，给它们一点时间跑完。
            // 删不掉也只是留个目录，下次启动会被 RoomNaming 当作已占用而跳过，不会撞名。
            if (!worker.awaitTermination(3, TimeUnit.SECONDS)) {
                logger.warning("仍有副本目录没清理完，可在服务器停止后手动删除残留的 ds_ 目录。");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private World load(RoomRequest request) {
        WorldCreator creator = new WorldCreator(request.worldName());
        if (request.environment() != null) {
            creator.environment(request.environment());
        }
        try {
            return creator.createWorld();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "加载副本世界 " + request.worldName() + " 时出错。", t);
            return null;
        }
    }

    private void purgeOrphans() {
        File folder;
        while ((folder = orphans.peek()) != null) {
            if (!WorldFolder.delete(folder)) {
                return;
            }
            orphans.poll();
        }
    }

    private void mainThread(Runnable task) {
        if (closed || !plugin.isEnabled()) {
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (Throwable t) {
            logger.log(Level.FINE, "调度回主线程失败，插件可能正在停用。", t);
        }
    }

    /** 已经在主线程上的完成回调直接跑，省一次调度。 */
    private void runNow(Runnable task) {
        if (closed) {
            return;
        }
        try {
            task.run();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "回收房间的回调执行失败。", t);
        }
    }

    private static final class WorkerFactory implements ThreadFactory {

        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "disaster-worldcopy-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
