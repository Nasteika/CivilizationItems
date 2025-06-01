plugins {
    kotlin("jvm") version "2.1.20-RC"
    id("com.gradleup.shadow") version "9.0.0-beta9"
}

group = "ru.vkabz"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://jitpack.io")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.dmulloy2.net/repository/public/") // Добавлен репозиторий для ProtocolLib
    maven("https://repo.essentialsx.net/releases/")
    maven("https://nexus.sirblobman.xyz/public/")



}

dependencies {
    // Обновлена версия Paper API до 1.20.6
    compileOnly("io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")

    implementation(kotlin("stdlib-jdk8"))

    // Обновлены зависимости WorldGuard и WorldEdit
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.9")
    compileOnly("com.sk89q.worldedit:worldedit-core:7.2.18")

    compileOnly("com.github.sirblobman.api:core:2.9-SNAPSHOT")
    compileOnly("com.github.sirblobman.combatlogx:api:11.4-SNAPSHOT")


    // Обновлена версия ProtocolLib до последней стабильной для 1.20.x
    compileOnly("com.comphenix.protocol:ProtocolLib:5.1.0")
}

val targetJavaVersion = 21

kotlin {
    jvmToolchain(targetJavaVersion)

}

tasks.build {
    dependsOn("shadowJar")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveBaseName.set(project.name)
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
}


tasks.register<Copy>("copyPlugin") {
    dependsOn("shadowJar")
    from(layout.buildDirectory.file("libs/${project.name}.jar"))
    into("/home/miss/server/plugins")
}
