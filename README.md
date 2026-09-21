# Disaster

复刻 **Hypixel Disasters** 的多人灾难生存小游戏，作为 Minecraft 服务端插件运行。

> **当前状态：M2「地图与权限基建」**
> 工程骨架、存储层（YAML / MySQL）、地图定义与标点、权限缓存与外部插件桥接骨架已就位。
> **对局玩法尚未实装**——`/ds` 目前只有 `help` / `info` / `reload` 与 `map` 四组子命令，
> 加入对局、投票、回放仍未开放。

---

## 兼容范围

| 项 | 说明 |
|---|---|
| Minecraft | **1.20.4 及以上** |
| 服务端 | **Spigot 打底**；运行时探测 Paper，探测到后启用 Adventure / MiniMessage 增强 |
| Folia | 不支持 |
| Java | 编译目标 **Java 17** 字节码 |

跨版本易碎点（例如 1.21.3 把 `Attribute.GENERIC_MAX_HEALTH` 改名为 `MAX_HEALTH`）
一律经由 `compat` 包反射兜底，主逻辑不直接静态引用，避免在某个小版本上抛
`NoSuchFieldError`。

---

## 构建

```bash
./gradlew build
```

产物为 `build/libs/Disaster-<version>.jar`（已含所需依赖，直接丢进 `plugins/` 即可）。

---

## 命令与权限

| 命令 | 权限 | 说明 |
|---|---|---|
| `/ds help` | `disaster.command` | 命令帮助 |
| `/ds info` | `disaster.command` | 查看运行环境与兼容信息 |
| `/ds reload` | `disaster.admin.reload` | 重载配置、消息与地图定义 |
| `/ds map …` | `disaster.admin.map` | 地图管理与标点，见下节 |

玩法类命令（加入对局、投票选图、查看战绩）将随对应里程碑开放。

### 权限节点

| 节点 | 默认 | 用途 |
|---|---|---|
| `disaster.command` | 所有人 | 使用 `/disaster` |
| `disaster.play` | 所有人 | 加入对局、投票选图、查看个人战绩 |
| `disaster.bypass` | OP | 豁免反滥用限制的总开关 |
| `disaster.bypass.protection` | OP | 无视方块破坏与放置的限制 |
| `disaster.admin` | OP | 全部管理命令的总开关，展开为下列四项 |
| `disaster.admin.map` | OP | 地图管理：标点、设边界、重载定义、生成测试城市 |
| `disaster.admin.room` | OP | 房间管理：强制开局、强制结束、踢出玩家 |
| `disaster.admin.reload` | OP | 重载配置、消息与地图定义 |
| `disaster.admin.debug` | OP | 调试命令：房间状态、存储状态、上报队列状态 |

权限检查走服务端原生的 `hasPermission`，**天然兼容所有权限插件，不需要任何额外配置**。
装了 LuckPerms 的服务器可以直接在 `/lp editor` 里看到上面这棵树。

一个要当心的点：**LuckPerms 的通配符 `disaster.*` 会把 `disaster.admin.*` 一起给出去。**
给普通玩家配权限时请逐个给，别用这个通配符。

`disaster.bypass.*` 只豁免本插件自己施加的限制，不会绕过服务端或其它插件的保护。

---

## 做一张地图

地图定义存在 `plugins/Disaster/maps/<id>.yml`，可以用命令标点生成，也可以直接手改文件。
首次运行会释放一份 `_example.yml` 作为字段说明（下划线开头的文件不参与加载，可以放心留着）。

**推荐流程**：在自己的模板世界里把图做好 → 加载该世界 → 用命令标点。

```bash
/ds map create city 城市          # 建定义
/ds map pos1                      # 站在一角
/ds map pos2                      # 站到对角
/ds map bounds city               # 写入边界
/ds map spawn add city 北门        # 依次标出生点（至少 2 个）
/ds map spec city                 # 设观战点
/ds map info city                 # 看校验结果
```

想跳过做图，直接生成一座测试城市（街道网格 + 方盒楼房，同一 id 每次生成结果一致）：

```bash
/ds map scaffold city 97
```

它会写好边界、16 个路口出生点与观战点，并把你传送过去。

**几条要点**

- 地图 `id` 会拼进副本世界名 `ds_<id>_<序号>`，只能用英文小写字母、数字、下划线、
  点和连字符；中文放在 `display-name`。
- 定义里的坐标都属于 `world` 字段指向的模板世界。开局时插件把模板世界拷成副本世界，
  整组坐标一并挪过去，所以不必为每个副本重新标点。
- `min-players` / `max-players` 填 `-1` 表示跟随 `config.yml` 的全局设置。
- 某场灾难若不配 `disaster-anchors`，它的落点就是全图随机。

抽图模式由 `config.yml` 的 `map-selection.mode` 决定（`RANDOM` / `VOTE` / `ROTATE` / `FIXED`），
优先级固定为**管理员指定 > 投票 > 配置模式**；`avoid-repeat` 会避开最近用过的图。

---

## 依赖说明

本插件**不强制依赖任何其它插件**。以下均为可选，装与不装都不影响正常使用：

| 插件 | 用途 | 状态 |
|---|---|---|
| LuckPerms | 读取玩家称号、在权限变更时立即刷新本插件的权限缓存 | 接口已预留 |
| WorldEdit / FastAsyncWorldEdit | 地图快照与重置（备选后端之一） | 待实装 |
| Multiverse-Core 5.x | 世界克隆与重置（备选后端之一） | 待实装 |

> 服主侧优先推荐 **FAWE** 而非 WorldEdit：WorldEdit 的插件版本矩阵不连续，
> 而 FAWE 单个 jar 即可覆盖 1.20.4–1.21.x 全区间。

### 关于 LuckPerms

**装 LuckPerms 不需要任何额外配置，也不需要什么扩展。** 权限检查走服务端原生的
`hasPermission`，LuckPerms 直接接管 `plugin.yml` 里声明的那棵树。

插件额外预留了一层可选桥接（`PermissionBridge` / `TitleProvider`），用于将来接上
权限插件独有的能力——读玩家称号、在权限变更时立即刷新权限缓存。目前**没有实现类**，
未接入时缓存按 `permission.cache-ttl-seconds` 自然过期，功能不缺失。

实现方通过 `ServicesManager` 注册，依赖方向单向：接口在本插件，实现方 `compileOnly`
依赖本插件。**实现必须放在独立包、且只在探测到该插件时才加载**，原因写在
`permission` 包注释里。

---

## 关于反作弊（请务必阅读）

**本插件不包含移动层反作弊，这是有意为之，不是遗漏。**

本游戏的玩家位移来源极多——龙卷风会卷起玩家、洪水会推动玩家、爆炸会击飞玩家。
任何基于「位移异常」的判定在这种场景下都会大量误报，把正常玩家误判为作弊者。
因此本插件只负责自己擅长的两层：

- **L1 游戏规则校验**：方块破坏/放置白名单、非 PvP 阶段拦截、旁观者隔离、
  伤害来源过滤、出界检测、禁用合成与丢弃关键物品。
- **L2 挂机检测**：综合评分式判定（主动事件权重高、视角转动居中、位置变化权重低
  ——因为它会被灾难推着走），先警告后处置，挂机者不计入获胜名单。

**移动层反作弊请另行安装独立插件**，推荐 **GrimAC** / **Vulcan** / **Matrix**
三者之一，它们在本游戏场景下表现良好。

---

## 开源协议声明

本项目遵循**零 shade 原则**：不把任何第三方代码打进自己的 jar。

WorldEdit 与 FastAsyncWorldEdit 使用 **GPL-3.0** 许可，因此本插件只在编译期
（`compileOnly`）依赖它们的 API，运行期由服务端自行加载对应插件，本项目不分发
也不嵌入其任何代码。Multiverse-Core 使用 BSD-3-Clause，处理方式相同。

**唯一例外**是 MySQL 支持所需的两项依赖，它们会被打包进本插件的 jar：

| 依赖 | 许可 | 用途 |
|---|---|---|
| [HikariCP](https://github.com/brettwooldridge/HikariCP) | Apache-2.0 | JDBC 连接池 |
| [MariaDB Connector/J](https://github.com/mariadb-corporation/mariadb-connector-j) | LGPL-2.1 | 连接 MySQL / MariaDB |

选择 MariaDB Connector/J 而不是 MySQL 官方驱动，是为了规避后者的 GPL-2.0
许可（及其 FOSS 例外条款）；该驱动完全兼容 MySQL 协议。

两项依赖在打包时会被重定位到 `com.xcreate.disaster.libs.*` 命名空间下，
不会与服务器上其它插件的类产生冲突。

---

## 数据流向

本插件**只出不进**：不监听任何端口，不暴露任何 HTTP 接口。

对局数据通过 webhook **单向推送**到服主自建的服务，落库逻辑完全由该服务负责，
本插件不需要知道目标数据库的存在。

- 每条推送带全局唯一 `eventId` 与 HMAC 签名 + 时间戳（防重放）
- 推送失败进入**本地持久化队列**并指数退避重试，重启不丢
- 接收端需按 `eventId` 幂等去重（带重试 ⇒ 必然至少一次投递）

---

## 目录结构

```
src/main/java/com/xcreate/disaster/
├── DisasterPlugin.java        主类
├── api/                       对外契约（第三方插件依赖此包）
│   ├── event/                 语义事件：对局开始/灾难触发/淘汰/方块变更/对局结束
│   ├── replay/                回放引擎契约（ReplayProvider）与数据载体
│   └── storage/               存储契约（StorageProvider）与值对象
├── command/                   /disaster 命令
├── compat/                    跨版本兼容层：版本探测、平台探测、反射工具
├── config/                    配置与消息
├── listener/                  事件监听，目前只有会话边界上的权限缓存维护
├── map/                       地图定义、标点、校验、选图、测试城市生成
├── permission/                权限节点常量、查询缓存、外部权限插件桥接
└── storage/                   存储实现：YAML / MySQL 双后端与降级

src/test/java/com/xcreate/disaster/
├── map/                       地图文件读写与选图规则的回归
└── permission/                权限缓存有效期与失效的回归
```

---

## 开发

```bash
./gradlew test        # 跑单元测试
./gradlew build       # 构建（会一并跑测试）
```

单元测试不启动服务端——地图文件读写只用到 `YamlConfiguration`，选图与权限缓存都是纯逻辑。
凡是「编译期看不出来、只会在服主机器上炸」的东西（文件格式、防连刷、降级路径）
都应当补一条回归。
