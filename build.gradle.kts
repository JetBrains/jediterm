plugins {
    kotlin("jvm") version "2.1.21" apply false
    id("org.jetbrains.compose") version "1.9.3" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
    id("com.android.library") version "8.5.2" apply false
    id("com.vanniktech.maven.publish") version "0.30.0" apply false
}

// Version format: MAJOR.MINOR.PATCH
// - MAJOR.MINOR from VERSION file (e.g., "1.0")
// - PATCH from BUILD_NUMBER env var (GitHub run number) or "0" for local
// - Local builds get "-SNAPSHOT" suffix
val baseVersion = rootProject.projectDir.resolve("VERSION").readText().trim()
val buildNumber = System.getenv("BUILD_NUMBER") ?: "0"
val isReleaseBuild = System.getenv("RELEASE_BUILD") != null || System.getenv("INTELLIJ_DEPENDENCIES_BOT") != null
val projectVersion = "$baseVersion.$buildNumber" + if (!isReleaseBuild) "-SNAPSHOT" else ""

allprojects {
  version = projectVersion
  group = "ai.rever.bossterm"
  layout.buildDirectory = rootProject.projectDir.resolve(".gradleBuild/" + project.name)
}

subprojects {
  repositories {
    mavenCentral()
    google()
  }
}