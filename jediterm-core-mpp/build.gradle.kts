import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    `maven-publish`
}

kotlin {
    jvmToolchain(17)
    jvm()
    androidTarget()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    js(IR) {
        browser()
    }
    wasmJs {
        browser()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.slf4j:slf4j-api:2.0.9")
                implementation("org.jetbrains:annotations:24.0.1")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("junit:junit:4.13.2")
                runtimeOnly("org.slf4j:slf4j-simple:2.0.9")
            }
        }
    }
}

android {
    namespace = "com.jediterm"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Ensure Java compilation depends on Kotlin compilation and can see Kotlin classes
tasks.named<JavaCompile>("compileJvmMainJava") {
    dependsOn("compileKotlinJvm")
    val compileKotlin = tasks.named("compileKotlinJvm")
    classpath += files(compileKotlin.map { it.outputs })
}

// Configure Java compilation to see Kotlin-generated classes
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:none")
}

val resultArchiveBaseName = "jediterm-core-mpp"

tasks.withType<Jar> {
  archiveBaseName = resultArchiveBaseName // to change name of out/libs/*.jar
}

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      from(components["kotlin"])
      artifactId = resultArchiveBaseName 
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
