package com.xcreate.disaster.storage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 存储任务的执行器。
 *
 * <p>守护线程池，专跑磁盘与 JDBC。线程名带前缀，线上看线程转储时能一眼认出是谁在慢。</p>
 *
 * <p>YAML 后端请把线程数设成 1：所有读写自动串行，省掉文件锁。</p>
 */
public final class AsyncStorage implements AutoCloseable {

    private static final long SHUTDOWN_WAIT_SECONDS = 5L;

    private final ExecutorService executor;
    private final Logger logger;
    private final AtomicInteger threadSeq = new AtomicInteger();

    public AsyncStorage(String namePrefix, int threads, Logger logger) {
        this.logger = logger;
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable,
                    namePrefix + "-" + threadSeq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newFixedThreadPool(Math.max(1, threads), factory);
    }

    /**
     * 提交一个有返回值的任务。
     *
     * <p>失败会记一条 WARNING 并让 future 异常完成——调用方不需要 try/catch，
     * 但也别指望失败时能拿到默认值。</p>
     */
    public <T> CompletableFuture<T> supply(Supplier<T> work) {
        return CompletableFuture.supplyAsync(work, executor)
                .whenComplete((value, error) -> logFailure(error));
    }

    public CompletableFuture<Void> run(Runnable work) {
        return CompletableFuture.runAsync(work, executor)
                .whenComplete((value, error) -> logFailure(error));
    }

    private void logFailure(Throwable error) {
        if (error == null) {
            return;
        }
        Throwable cause = error instanceof CompletionException && error.getCause() != null
                ? error.getCause()
                : error;
        logger.log(Level.WARNING, "存储任务执行失败: " + cause, cause);
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                logger.warning("存储线程池未能在 " + SHUTDOWN_WAIT_SECONDS + " 秒内收尾，强制关闭。");
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
