/**
 * 跨版本兼容层。
 *
 * <p>本插件要覆盖 1.20.4 及以上的所有版本，且同时跑在 Spigot 与 Paper 上。凡是版本之间有
 * 差异的东西——字段改名（1.21.3 把 {@code Attribute.GENERIC_MAX_HEALTH} 改成
 * {@code MAX_HEALTH}）、类不存在、Paper 专属 API——一律经由本包用反射兜底，
 * 主逻辑禁止直接静态引用，否则会在某个小版本上直接抛 {@link java.lang.NoSuchFieldError}。</p>
 *
 * <p>本包的约定：探测失败一律静默返回 null 或 false，不抛异常。</p>
 */
package com.xcreate.disaster.compat;
