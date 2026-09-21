package com.xcreate.disaster.disaster;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 灾难的自由参数。
 *
 * <p>每个灾种的参数差别很大（流星的散落半径、落雷的采样密度、龙卷风的入边宽度），
 * 硬塞进固定字段会让定义类长满只被一两个灾种用到的属性，所以统一走一张名字到值的表。</p>
 *
 * <p>取值一律带默认值且不抛异常：YAML 里写错类型是服主常犯的错，那种情况退回默认值继续跑，
 * 比让整个定义读不出来强。</p>
 */
public final class DisasterOptions {

    private static final DisasterOptions EMPTY = new DisasterOptions(Map.of());

    private final Map<String, Object> values;

    private DisasterOptions(Map<String, Object> values) {
        this.values = values;
    }

    public static DisasterOptions empty() {
        return EMPTY;
    }

    public static DisasterOptions of(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return EMPTY;
        }
        return new DisasterOptions(new LinkedHashMap<>(values));
    }

    public Map<String, Object> raw() {
        return Map.copyOf(values);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public boolean has(String key) {
        return values.get(key) != null;
    }

    public int getInt(String key, int fallback) {
        Double number = number(key);
        return number == null ? fallback : (int) Math.round(number);
    }

    public double getDouble(String key, double fallback) {
        Double number = number(key);
        return number == null ? fallback : number;
    }

    public boolean getBoolean(String key, boolean fallback) {
        Object value = values.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text.trim());
        }
        return fallback;
    }

    public String getString(String key, String fallback) {
        Object value = values.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    /** 逗号分隔的写法也认，省得服主为了一个列表改成缩进格式。 */
    public List<String> getStringList(String key) {
        Object value = values.get(key);
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        if (value instanceof String text && !text.isBlank()) {
            List<String> result = new ArrayList<>();
            for (String part : text.split(",")) {
                if (!part.isBlank()) {
                    result.add(part.trim());
                }
            }
            return result;
        }
        return List.of();
    }

    /** 展开成一行，给命令输出与日志用。 */
    public String shortText() {
        if (values.isEmpty()) {
            return "无";
        }
        StringBuilder text = new StringBuilder();
        values.forEach((key, value) -> {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(key).append('=').append(value);
        });
        return text.toString();
    }

    private Double number(String key) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
