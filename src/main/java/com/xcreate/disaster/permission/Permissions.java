package com.xcreate.disaster.permission;

/**
 * 权限节点。
 *
 * <p>同一批节点也写在 plugin.yml 里——那边是给服主看的，这里是给代码用的，改名要一起动。</p>
 */
public final class Permissions {

    public static final String COMMAND = "disaster.command";
    public static final String PLAY = "disaster.play";

    public static final String ADMIN = "disaster.admin";
    public static final String ADMIN_MAP = "disaster.admin.map";
    public static final String ADMIN_ROOM = "disaster.admin.room";
    public static final String ADMIN_RELOAD = "disaster.admin.reload";
    public static final String ADMIN_DEBUG = "disaster.admin.debug";

    public static final String BYPASS = "disaster.bypass";
    public static final String BYPASS_PROTECTION = "disaster.bypass.protection";

    private Permissions() {
    }
}
