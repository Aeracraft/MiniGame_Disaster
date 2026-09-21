/**
 * 地图定义与标点。
 *
 * <p>一张地图由 {@link com.xcreate.disaster.map.MapDefinition} 描述：模板世界、可用范围、
 * 出生点、观战点、按灾难分组的固定落点、补给箱。定义存在
 * {@code plugins/Disaster/maps/<id>.yml}，由服主用 {@code /ds map} 标点生成，
 * 之后也可以直接手改文件。</p>
 *
 * <p>几条贯穿全局的约定：</p>
 * <ul>
 *   <li>地图 id 会拼进副本世界名 {@code ds_<id>_<序号>}，所以只能用英文小写字母、数字、
 *       下划线、点和连字符；中文放 display-name。</li>
 *   <li>定义里的坐标都属于 {@code world} 字段指的那个模板世界。开局时整组坐标经
 *       {@link com.xcreate.disaster.map.MapDefinition#inWorld(String)} 挪到副本世界，
 *       点位本身不必知道副本名。</li>
 *   <li>地图文件的写入是同步的——文件只有几 KB，改地图是管理员偶尔为之的动作，
 *       不在游戏 tick 路径上。</li>
 * </ul>
 */
package com.xcreate.disaster.map;
