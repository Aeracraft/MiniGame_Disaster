/**
 * 权限：节点常量、查询缓存，以及与权限插件的可选集成。
 *
 * <p>权限检查走服务端原生的 {@code hasPermission}，因此天然兼容所有权限插件，
 * 不需要安装或依赖任何一个——LuckPerms 会自动读取 plugin.yml 里的节点树。
 * 下面两条约束只针对「用上权限插件独有能力」这条可选路径，还没有实现类。</p>
 *
 * <p>一是 Adventure。LuckPerms 这类插件重度使用它，而 Spigot 上并没有（Paper 才有）。
 * API 里同时存在返回 String 与返回 Component 的同类方法，调错后者会在运行时、只在 Spigot 上、
 * 可能只在读某个玩家数据的那一刻才炸。所以本包对外只暴露 String，
 * 集成实现必须放在独立包且只在探测到该插件时才加载。</p>
 *
 * <p>二是 {@code ContextCalculator}。它是在每一次 {@code hasPermission} 调用时执行的，
 * 在里面查房间状态、或者再做一次权限判断，都会重入。真要做上下文，实现只能是纯内存查表。</p>
 */
package com.xcreate.disaster.permission;
