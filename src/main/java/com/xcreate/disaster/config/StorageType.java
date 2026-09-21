package com.xcreate.disaster.config;

import java.util.Locale;

/** 数据存储后端。 */
public enum StorageType {

    /** 本地 YAML 文件。单服部署的默认选择，也是 MySQL 不可用时的降级目标。 */
    YAML,

    /** MySQL / MariaDB。跨服共享数据（玩家统计、服务器心跳、房间池、成就）时使用。 */
    MYSQL;

    /**
     * 解析配置里的取值。无法识别时返回 {@link #YAML}——
     * 降级到本地存储总比拒绝启动好。
     */
    public static StorageType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return YAML;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return YAML;
        }
    }
}
