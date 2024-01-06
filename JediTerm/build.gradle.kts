plugins {
  kotlin("jvm") version "1.9.22"
  application
}

repositories {
  maven {
    url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
  }
}

dependencies {
  implementation(project(":ui"))
  implementation(project(":core"))

  implementation("org.jetbrains.pty4j:pty4j:0.12.25")
  implementation("org.slf4j:slf4j-api:2.0.9")
  implementation("org.slf4j:slf4j-jdk14:2.0.9")
  implementation("org.jetbrains:annotations:24.0.1")
}

application {
  mainClass = "com.jediterm.app.JediTermMain"
}
