plugins {
  kotlin("jvm")
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

  implementation("org.jetbrains.pty4j:pty4j:0.13.8")
  implementation("org.slf4j:slf4j-api:2.0.9")
  implementation("org.slf4j:slf4j-jdk14:2.0.9")
  implementation("org.jetbrains:annotations:24.0.1")

  testImplementation("junit:junit:4.13.2")
}

application {
  mainClass = "com.jediterm.app.JediTermMain"
}
