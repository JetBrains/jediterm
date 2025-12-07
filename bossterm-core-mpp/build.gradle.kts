import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.vanniktech.maven.publish.SonatypeHost
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.JavadocJar

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("com.vanniktech.maven.publish")
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
                implementation("com.ibm.icu:icu4j:74.1") // For grapheme cluster segmentation
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
    namespace = "ai.rever.bossterm"
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

val resultArchiveBaseName = "bossterm-core"

tasks.withType<Jar> {
    archiveBaseName = resultArchiveBaseName
}

// Maven Central + GitHub Packages publishing
mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates("com.risaboss", "bossterm-core", version.toString())

    configure(KotlinMultiplatform(
        javadocJar = JavadocJar.Empty(),
        sourcesJar = true,
        androidVariantsToPublish = listOf("release"),
    ))

    pom {
        name.set("BossTerm Core")
        description.set("Terminal emulation engine for Kotlin Multiplatform")
        url.set("https://github.com/kshivang/BossTerm")
        licenses {
            license {
                name.set("LGPL 3.0")
                url.set("https://www.gnu.org/licenses/lgpl.txt")
            }
        }
        developers {
            developer {
                id.set("kshivang")
                name.set("Shivang")
                email.set("shivang.risa@gmail.com")
            }
        }
        scm {
            url.set("https://github.com/kshivang/BossTerm")
            connection.set("scm:git:git://github.com/kshivang/BossTerm.git")
            developerConnection.set("scm:git:ssh://github.com/kshivang/BossTerm.git")
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/kshivang/BossTerm")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}
