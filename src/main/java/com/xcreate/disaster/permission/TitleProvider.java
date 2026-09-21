package com.xcreate.disaster.permission;

import java.util.UUID;

/**
 * 玩家称号来源，可选接入。
 *
 * <p>只以 String 出参。接口签名里一旦出现 Adventure 的 Component，Spigot 侧加载实现类
 * 就会 {@code NoClassDefFoundError}——Spigot 不带 Adventure，Paper 才有。
 * 颜色沿用 {@code &} 号写法，由本插件统一转换。</p>
 */
public interface TitleProvider {

    String id();

    boolean available();

    /** 称号前缀，含颜色代码；没有称号时返回空串，不返回 null。 */
    String prefix(UUID playerId);
}
