package com.xcreate.disaster.room;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * 世界目录的拷贝与删除。
 *
 * <p>纯文件操作，不碰 Bukkit。拷贝必须发生在世界的加载与卸载之外——服务端正持有那个目录时
 * 拷出来的是半截数据。</p>
 *
 * <p>以下几种东西刻意不拷：</p>
 * <ul>
 *   <li>{@code session.lock}：拷过去会带一份陈旧的会话锁，新世界加载时可能直接失败</li>
 *   <li>{@code uid.dat}：世界唯一标识。副本该有自己的新标识，沿用同一个容易在多世界并存时出问题</li>
 *   <li>{@code playerdata} / {@code stats} / {@code advancements}：模板世界的玩家数据。
 *       不排除的话，在模板世界里做过测试的人进副本会继承那份存档</li>
 * </ul>
 */
public final class WorldFolder {

    private static final Set<String> SKIPPED = Set.of(
            "session.lock", "uid.dat", "playerdata", "stats", "advancements", "level.dat_old");

    private WorldFolder() {
    }

    /**
     * 递归拷贝世界目录。
     *
     * @return 拷贝的字节数，用于日志
     */
    public static long copy(File source, File target) throws IOException {
        if (source == null || !source.isDirectory()) {
            throw new IOException("模板世界目录不存在：" + source);
        }
        Path sourcePath = source.toPath();
        Path targetPath = target.toPath();
        Files.createDirectories(targetPath);

        AtomicLong bytes = new AtomicLong();
        Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes)
                    throws IOException {
                Path relative = sourcePath.relativize(dir);
                if (skipped(relative)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(targetPath.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                    throws IOException {
                Path relative = sourcePath.relativize(file);
                if (skipped(relative)) {
                    return FileVisitResult.CONTINUE;
                }
                Files.copy(file, targetPath.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
                bytes.addAndGet(attributes.size());
                return FileVisitResult.CONTINUE;
            }
        });
        return bytes.get();
    }

    /** 删掉整个目录。删不掉时返回 false，由调用方决定要不要重试。 */
    public static boolean delete(File folder) {
        if (folder == null || !folder.exists()) {
            return true;
        }
        try (Stream<Path> stream = Files.walk(folder.toPath())) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 单个文件删不掉不影响其余部分，最后统一看目录还在不在
                }
            });
        } catch (IOException e) {
            return false;
        }
        return !folder.exists();
    }

    public static long sizeOf(File folder) {
        if (folder == null || !folder.isDirectory()) {
            return 0L;
        }
        try (Stream<Path> stream = Files.walk(folder.toPath())) {
            return stream.filter(Files::isRegularFile).mapToLong(path -> {
                try {
                    return Files.size(path);
                } catch (IOException e) {
                    return 0L;
                }
            }).sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    public static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
    }

    private static boolean skipped(Path relative) {
        if (relative.getNameCount() == 0) {
            return false;
        }
        String top = relative.getName(0).toString().toLowerCase(Locale.ROOT);
        return SKIPPED.contains(top);
    }
}
