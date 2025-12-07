import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl
import java.util.Properties

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
    id("com.android.library")
    kotlin("plugin.serialization") version "2.1.0"
    `maven-publish`
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

    // Android target
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
        publishLibraryVariants("release")
    }

    // iOS targets
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "BossTermCompose"
            isStatic = true
        }
    }

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

    // Web targets
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "bossterm-compose.js"
            }
        }
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "bossterm-compose.wasm.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        // Common source sets
        val commonMain by getting {
            dependencies {
                // Compose dependencies
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

                // Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

                // Logging
                implementation("org.slf4j:slf4j-api:2.0.9")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            }
        }

        // Android source set
        val androidMain by getting {
            dependencies {
                implementation(project(":bossterm-core-mpp"))
                implementation("androidx.activity:activity-compose:1.9.3")
                implementation("androidx.appcompat:appcompat:1.7.0")
                implementation("androidx.core:core-ktx:1.15.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
            }
        }

        // Desktop source set
        val desktopMain by getting {
            dependencies {
                implementation(project(":bossterm-core-mpp"))
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
                implementation("org.jetbrains.pty4j:pty4j:0.12.13")
            }
        }

        val desktopTest by getting

        // JS source set
        val jsMain by getting {
            dependencies {
                implementation(compose.html.core)
            }
        }

        // Wasm source set
        val wasmJsMain by getting {
            dependencies {
                implementation(compose.runtime)
            }
        }
    }
}

android {
    namespace = "ai.rever.bossterm.compose"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/resources")
}

compose.desktop {
    application {
        mainClass = "ai.rever.bossterm.compose.demo.MainKt"

        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg)

            packageName = "BossTerm"
            packageVersion = "1.0.0"
            description = "Modern terminal emulator built with Kotlin/Compose Desktop"
            vendor = "Rever AI"
            copyright = "© 2025 Rever AI. All rights reserved."

            macOS {
                // iconFile.set(project.file("src/desktopMain/resources/icons/bossterm.icns"))
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

compose.experimental {
    web.application {}
}

// Note: macOS code signing is now handled by Compose Desktop's built-in signing configuration
// See macOS { signing { ... } } block above

publishing {
    publications {
        create<MavenPublication>("composeUi") {
            artifactId = "bossterm-compose-ui"
            pom {
                name.set("BossTerm Compose UI")
                description.set("Compose Multiplatform UI for BossTerm")
                url.set("https://github.com/JetBrains/bossterm")
                licenses {
                    license {
                        name.set("LGPL 3.0")
                        url.set("https://www.gnu.org/licenses/lgpl.txt")
                    }
                }
            }
        }
    }
}
