plugins {
    kotlin("jvm") version "2.1.21" apply false
    id("org.jetbrains.compose") version "1.7.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("com.android.library") version "8.5.2" apply false
}

val projectVersion = rootProject.projectDir.resolve("VERSION").readText().trim() +
  if (System.getenv("INTELLIJ_DEPENDENCIES_BOT") == null) "-SNAPSHOT" else ""

allprojects {
  version = projectVersion
  group = "org.jetbrains.jediterm"
  layout.buildDirectory = rootProject.projectDir.resolve(".gradleBuild/" + project.name)
}

subprojects {
  repositories {
    mavenCentral()
    google()
  }
}