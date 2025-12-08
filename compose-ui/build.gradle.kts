import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import com.vanniktech.maven.publish.SonatypeHost
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.JavadocJar

// Load local.properties for signing configuration (gitignored)
val localProperties = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { load(it) }
    }
}
val macosSigningIdentity: String = System.getenv("MACOS_DEVELOPER_ID")
    ?: localProperties.getProperty("macos.signing.identity")
    ?: "-"  // Ad-hoc signing fallback

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("plugin.serialization") version "2.1.21"
    id("com.vanniktech.maven.publish")
}

group = "ai.rever.bossterm"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

kotlin {
    jvmToolchain(17)

    // Note: Android target removed from compose-ui - no androidMain source set exists
    // The core library (bossterm-core-mpp) supports Android
    // compose-ui is Desktop-only for now

    // Desktop JVM target
    jvm("desktop") {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
    }

    // Note: JS and Wasm targets removed - no actual implementation exists yet
    // Can be added when jsMain/wasmJsMain source sets are implemented

    sourceSets {
        // Common source sets
        val commonMain by getting {
            dependencies {
                // Compose dependencies
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

                // Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

                // Logging
                implementation("org.slf4j:slf4j-api:2.0.17")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            }
        }

        // Desktop source set
        val desktopMain by getting {
            dependencies {
                implementation(project(":bossterm-core-mpp"))
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
                implementation("org.jetbrains.pty4j:pty4j:0.13.9")

                // JNA for native macOS notifications
                implementation("net.java.dev.jna:jna:5.18.1")
                implementation("net.java.dev.jna:jna-platform:5.18.1")

                // Ktor client for auto-update
                val ktorVersion = "3.3.2"
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-cio:$ktorVersion")
                implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
            }
        }

        val desktopTest by getting
    }
}

// Note: Android configuration removed - compose-ui is Desktop-only
// See bossterm-core-mpp for Android support

compose.desktop {
    application {
        mainClass = "ai.rever.bossterm.compose.demo.MainKt"

        // JVM args for macOS blur effect (access to internal AWT classes)
        jvmArgs += listOf(
            "--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED",
            "--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
            "--add-opens", "java.desktop/java.awt=ALL-UNNAMED",
            "--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED"
        )

        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg)

            packageName = "BossTerm"
            packageVersion = project.version.toString().removeSuffix("-SNAPSHOT")
            description = "Modern terminal emulator built with Kotlin/Compose Desktop"
            vendor = "risalabs.ai"
            copyright = "© 2025 risalabs.ai. All rights reserved."

            // Include CLI script in app resources
            appResourcesRootDir.set(rootProject.file("cli-resources"))

            macOS {
                iconFile.set(rootProject.file("BossTerm.icns"))
                bundleID = "ai.rever.bossterm"
                dockName = "BossTerm"
                // Allow access to all files for terminal operations
                entitlementsFile.set(project.file("src/desktopMain/resources/entitlements.plist"))

                // Code signing configuration for distribution
                signing {
                    val skipSigning = System.getenv("DISABLE_MACOS_SIGNING") == "true"
                    sign.set(!skipSigning)
                    identity.set(macosSigningIdentity)

                    println("🔐 macOS Code Signing: ${if (skipSigning) "DISABLED" else macosSigningIdentity}")
                }

                infoPlist {
                    extraKeysRawXml = """
                        <key>NSHighResolutionCapable</key>
                        <true/>
                        <key>LSMinimumSystemVersion</key>
                        <string>11.0</string>
                    """.trimIndent()
                }
            }

            // Include required JVM modules
            modules("java.sql", "jdk.unsupported", "jdk.management.agent")

            // JVM args for better performance
            jvmArgs += listOf(
                "-Xmx2G",
                "-Dapple.awt.application.appearance=system"
            )
        }
    }
}

// Note: macOS code signing is now handled by Compose Desktop's built-in signing configuration
// See macOS { signing { ... } } block above

// Maven Central + GitHub Packages publishing
mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates("com.risaboss", "bossterm-compose", version.toString())

    configure(KotlinMultiplatform(
        javadocJar = JavadocJar.Empty(),
        sourcesJar = true,
    ))

    pom {
        name.set("BossTerm Compose")
        description.set("Embeddable terminal composable for Kotlin Compose Multiplatform")
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
