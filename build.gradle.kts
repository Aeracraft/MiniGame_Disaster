plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.xcreate"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    // Spigot 官方仓库（snapshot）。打底 API，编译目标 Java 17。
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    // ---- 编译期依赖（一律不打进 jar）----
    // 兼容范围：Minecraft 1.20.4+。以 Spigot API 打底，
    // Paper 专属能力运行时探测后再启用（放独立包 + 类隔离）。
    compileOnly("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")

    // ---- 需要 shade 进 jar 的唯一例外（见 README「协议声明」）----
    // HikariCP  Apache-2.0  连接池
    // MariaDB Connector/J  LGPL-2.1  兼容 MySQL 协议，规避 MySQL 官方驱动的 GPL-2.0
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.10")

    // ---- 测试 ----
    // 地图文件的读写只用到 YamlConfiguration，不需要起服务端，所以测试也挂 spigot-api。
    testImplementation("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

// 主产物就是 fat jar：禁用默认的空壳 jar，让 shadowJar 顶替它。
tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveClassifier.set("")

    // 只重定位我们确实打包进来的两个库，避免与服务器上其它插件冲突。
    relocate("com.zaxxer.hikari", "com.xcreate.disaster.libs.hikari")
    relocate("org.mariadb.jdbc", "com.xcreate.disaster.libs.mariadb")

    // 必须为 INCLUDE：否则 META-INF/services 下的重名文件会在交给
    // ServiceFileTransformer 合并之前就被静默丢弃（JDBC 驱动注册就靠它）。
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    exclude("module-info.class", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.named("build") {
    dependsOn("shadowJar")
}

tasks.test {
    useJUnitPlatform()
}
