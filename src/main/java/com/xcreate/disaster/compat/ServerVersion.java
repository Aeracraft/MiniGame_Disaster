package com.xcreate.disaster.compat;

import org.bukkit.Bukkit;

/**
 * 服务端 Minecraft 版本号。
 *
 * <p>2026 年起版本号从 {@code 1.x} 换成 年.次.补丁 的写法（例如 {@code 26.1}）。
 * 这里统一按点分数字段从高位到低位比较，两套方案排序都正确：
 * {@code 26.1.0 > 1.20.4}、{@code 26.2.0 > 26.1.0}。</p>
 */
public final class ServerVersion implements Comparable<ServerVersion> {

    /** 最低支持版本。低于它的服务端将拒绝启用本插件。 */
    public static final ServerVersion MIN_SUPPORTED = new ServerVersion(1, 20, 4);

    private final int major;
    private final int minor;
    private final int patch;
    private final String raw;

    public ServerVersion(int major, int minor, int patch) {
        this(major, minor, patch, major + "." + minor + "." + patch);
    }

    private ServerVersion(int major, int minor, int patch, String raw) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.raw = raw;
    }

    /**
     * 从 {@link Bukkit#getBukkitVersion()} 解析当前服务端版本。
     * 形如 {@code 1.20.4-R0.1-SNAPSHOT} 或 {@code 26.1-R0.1-SNAPSHOT}。
     */
    public static ServerVersion detect() {
        return parse(Bukkit.getBukkitVersion());
    }

    /**
     * 解析版本字符串。无法识别的输入会返回 {@link #MIN_SUPPORTED}，
     * 宁可误判为「刚好够用」也不要误判为「不支持」而拒绝启动。
     */
    public static ServerVersion parse(String input) {
        if (input == null || input.isBlank()) {
            return MIN_SUPPORTED;
        }

        // 去掉 -R0.1-SNAPSHOT 之类的后缀，只保留版本号部分
        String cleaned = input.trim();
        int cut = indexOfAny(cleaned, '-', '+', ' ');
        if (cut > 0) {
            cleaned = cleaned.substring(0, cut);
        }

        String[] parts = cleaned.split("\\.");
        try {
            int major = parts.length > 0 ? Integer.parseInt(digitsOnly(parts[0])) : 0;
            int minor = parts.length > 1 ? Integer.parseInt(digitsOnly(parts[1])) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(digitsOnly(parts[2])) : 0;
            return new ServerVersion(major, minor, patch, cleaned);
        } catch (NumberFormatException ex) {
            return MIN_SUPPORTED;
        }
    }

    private static int indexOfAny(String s, char... chars) {
        for (int i = 0; i < s.length(); i++) {
            for (char c : chars) {
                if (s.charAt(i) == c) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String digitsOnly(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.length() == 0 ? "0" : sb.toString();
    }

    public boolean isSupported() {
        return compareTo(MIN_SUPPORTED) >= 0;
    }

    public boolean isAtLeast(int major, int minor, int patch) {
        return compareTo(new ServerVersion(major, minor, patch)) >= 0;
    }

    public boolean isBelow(int major, int minor, int patch) {
        return compareTo(new ServerVersion(major, minor, patch)) < 0;
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public int patch() {
        return patch;
    }

    /** 原始（已清洗后缀的）版本字符串。 */
    public String raw() {
        return raw;
    }

    @Override
    public int compareTo(ServerVersion other) {
        int r = Integer.compare(this.major, other.major);
        if (r != 0) {
            return r;
        }
        r = Integer.compare(this.minor, other.minor);
        if (r != 0) {
            return r;
        }
        return Integer.compare(this.patch, other.patch);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServerVersion other)) {
            return false;
        }
        return major == other.major && minor == other.minor && patch == other.patch;
    }

    @Override
    public int hashCode() {
        return (major * 31 + minor) * 31 + patch;
    }

    @Override
    public String toString() {
        return raw;
    }
}
