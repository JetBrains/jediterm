import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

plugins {
  kotlin("jvm") version "1.9.22"
  `java-library`
  `maven-publish`
}

sourceSets {
  main {
    java.srcDirs("src")
  }
  test {
    java.srcDirs("tests/src")
  }
}

java {
  withSourcesJar()
}

dependencies {
  implementation("org.slf4j:slf4j-api:2.0.9")
  implementation("org.jetbrains:annotations:24.0.1")
  testImplementation("junit:junit:4.13.2")
  testRuntimeOnly("org.slf4j:slf4j-simple:2.0.9")
}

tasks {
  val jvmTarget = JvmTarget.JVM_11
  compileJava {
    sourceCompatibility = jvmTarget.target
    targetCompatibility = jvmTarget.target
  }
  compileKotlin {
    compilerOptions.jvmTarget = jvmTarget
  }
  test {
    testLogging {
      events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED,
        TestLogEvent.STANDARD_OUT, TestLogEvent.STANDARD_ERROR)
      exceptionFormat = TestExceptionFormat.FULL
      showExceptions = true
      showCauses = true
      showStackTraces = true
      showStandardStreams = true
    }
  }
  jar {
    manifest {
      attributes(
        "Build-Timestamp" to DateTimeFormatter.ISO_INSTANT.format(ZonedDateTime.now()),
        "Created-By" to "Gradle ${gradle.gradleVersion}",
        "Build-Jdk" to System.getProperty("java.runtime.version"),
        "Build-OS" to "${System.getProperty("os.name")} ${System.getProperty("os.arch")} ${System.getProperty("os.version")}"
      )
    }
  }
}

tasks.withType<JavaCompile> {
  options.encoding = Charsets.UTF_8.name()
}

val resultArchiveBaseName = "jediterm-core"

tasks.withType<Jar> {
  archiveBaseName = resultArchiveBaseName // to change name of out/libs/*.jar
}

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      from(components["java"])
      artifactId = resultArchiveBaseName // by default `project.name` is used - "core"
      pom {
        name = "JediTerm"
        description = "Pure Java Terminal Emulator"
        url = "https://github.com/JetBrains/jediterm"
        licenses {
          license {
            name = "LGPL 3.0"
            url = "https://www.gnu.org/licenses/lgpl.txt"
          }
        }
        scm {
          connection = "scm:git:git://github.com/JetBrains/jediterm.git"
          developerConnection = "scm:git:ssh:github.com/JetBrains/jediterm.git"
          url = "https://github.com/JetBrains/jediterm"
        }
      }
    }
  }
  repositories {
    maven {
      url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
      credentials {
        username = System.getenv("INTELLIJ_DEPENDENCIES_BOT")
        password = System.getenv("INTELLIJ_DEPENDENCIES_TOKEN")
      }
    }
  }
}
