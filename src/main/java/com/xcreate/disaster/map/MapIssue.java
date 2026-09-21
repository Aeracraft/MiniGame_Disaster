package com.xcreate.disaster.map;

/**
 * 一条地图校验结果。
 *
 * <p>ERROR 会让地图失去开局资格；WARNING 只提示，服主自己判断要不要管。</p>
 */
public record MapIssue(Severity severity, String message) {

    public enum Severity {
        ERROR,
        WARNING
    }

    public static MapIssue error(String message) {
        return new MapIssue(Severity.ERROR, message);
    }

    public static MapIssue warning(String message) {
        return new MapIssue(Severity.WARNING, message);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    public String text() {
        return (isError() ? "&c✖ " : "&e⚠ ") + message;
    }
}
