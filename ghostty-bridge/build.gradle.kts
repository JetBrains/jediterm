import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  kotlin("jvm") version "2.4.0"
  `java-library`
}

// This module hosts the bridge between JediTerm's data model and the ghostty VT
// engine (libghostty-vt) via the Java FFM API (Project Panama). FFM is stable
// since JDK 22, so unlike :core (which targets JVM 11) this module needs a
// modern toolchain.
java {
  // Compile on JDK 25 (supplies the stable FFM API) but emit Java 22 bytecode: FFM
  // (java.lang.foreign) is stable @since 22 (JEP 454), the minimum this module needs.
  // source/targetCompatibility must equal the Kotlin jvmTarget below, or the Kotlin
  // plugin's JVM-target validation fails the build.
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
  sourceCompatibility = JavaVersion.VERSION_22
  targetCompatibility = JavaVersion.VERSION_22
}

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.JVM_22
  }
}

sourceSets {
  main {
    java.srcDirs("src")
  }
  test {
    java.srcDirs("tests/src")
  }
}

repositories {
  // pty4j (the demo's PTY backend) is published to the JetBrains dependencies repo.
  maven { url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies") }
}

dependencies {
  implementation(project(":core"))
  implementation(project(":ui"))
  implementation("org.slf4j:slf4j-api:2.0.9")
  implementation("org.jetbrains:annotations:24.0.1")
  implementation("org.jetbrains.pty4j:pty4j:0.13.8")
  testImplementation("junit:junit:4.13.2")
  testRuntimeOnly("org.slf4j:slf4j-simple:2.0.9")
}

// Absolute path of the libghostty-vt shared library produced by
//   (cd ghostty && zig build -Demit-lib-vt)
val ghosttyVtLib = rootProject.projectDir
  .resolve("ghostty/zig-out/lib/libghostty-vt.dylib")

tasks.withType<JavaCompile> {
  options.encoding = Charsets.UTF_8.name()
}

// Launches the Swing demo: a JediTermWidget driven by the ghostty engine, running a real shell.
//   ./gradlew :ghostty-bridge:runGhosttyDemo
tasks.register<JavaExec>("runGhosttyDemo") {
  group = "application"
  description = "Run a JediTermWidget backed by the ghostty VT engine with a real shell."
  mainClass = "com.jediterm.ghostty.demo.GhosttyDemo"
  classpath = sourceSets.main.get().runtimeClasspath
  javaLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(25)
  }
  jvmArgs("--enable-native-access=ALL-UNNAMED")
  systemProperty("ghostty.vt.lib", ghosttyVtLib.absolutePath)
  systemProperty("file.encoding", "UTF-8")
}

tasks.test {
  // Only *Test classes are tests; GhosttyTestTerminal / GhosttyResizeSession are support classes.
  include("**/*Test.class")
  // FFM downcalls are "restricted" native access; opt in to avoid the warning
  // (and the hard failure in future JDKs).
  jvmArgs("--enable-native-access=ALL-UNNAMED")
  systemProperty("ghostty.vt.lib", ghosttyVtLib.absolutePath)
  systemProperty("file.encoding", "UTF-8")
  testLogging {
    events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED,
      TestLogEvent.STANDARD_OUT, TestLogEvent.STANDARD_ERROR)
    exceptionFormat = TestExceptionFormat.FULL
    showStandardStreams = true
  }
}
