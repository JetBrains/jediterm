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
  }
}