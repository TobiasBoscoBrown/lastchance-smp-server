plugins {
    java
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation("io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to (project.version as String? ?: "1.0.0"))
    }
}

tasks.jar {
    archiveBaseName.set("DeathsReporter")
}

val installPlugin by tasks.registering(Copy::class) {
    from(tasks.named("jar"))
    into("c:/mc/Server/plugins")
}

tasks.named("build") {
    finalizedBy(installPlugin)
}
