import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import com.vanniktech.maven.publish.SonatypeHost
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.JavadocJar
import javax.inject.Inject
import org.gradle.process.ExecOperations

// Interface for injecting ExecOperations into tasks
// Replaces deprecated project.exec() calls for Gradle 9.0 compatibility
interface InjectedExecOps {
    @get:Inject val execOps: ExecOperations
}

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
                // JVM runtime also needs entitlements for notarization
                runtimeEntitlementsFile.set(project.file("src/desktopMain/resources/runtime-entitlements.plist"))

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

// Sign PTY4J native binaries with hardened runtime for macOS notarization
tasks.register("signPty4jBinaries") {
    description = "Signs PTY4J native binaries with hardened runtime for Apple notarization"
    group = "build"

    // Only run on macOS and when signing is enabled
    onlyIf {
        val isMacOS = System.getProperty("os.name").lowercase().contains("mac")
        val signingDisabled = System.getenv("DISABLE_MACOS_SIGNING") == "true"
        isMacOS && !signingDisabled
    }

    // Inject ExecOperations for exec calls (replaces deprecated project.exec)
    val injected = project.objects.newInstance<InjectedExecOps>()

    doLast {
        println("🔧 Signing PTY4J native binaries with hardened runtime for notarization...")

        // Get developer identity from environment or use default
        val developerId = System.getenv("MACOS_DEVELOPER_ID")
            ?: localProperties.getProperty("macos.signing.identity")
            ?: "-"

        if (developerId == "-") {
            println("⚠️ No signing identity found, skipping PTY4J signing")
            return@doLast
        }

        // Find the built app in the standard Compose Desktop location
        val appDir = project.layout.buildDirectory.dir("compose/binaries/main/app").get().asFile
        val appFile = appDir.listFiles()?.find { it.name.endsWith(".app") }

        if (appFile?.exists() == true) {
            println("Found app: ${appFile.name}")

            // Find PTY4J jar inside the app
            val appContents = File(appFile, "Contents/app")
            val pty4jJar = appContents.listFiles()?.find {
                it.name.startsWith("pty4j-") && it.name.endsWith(".jar")
            }

            if (pty4jJar?.exists() == true) {
                println("Processing PTY4J jar: ${pty4jJar.name}")

                // Create temporary directory for jar manipulation
                val tempDir = File(System.getProperty("java.io.tmpdir"), "pty4j-sign-${System.currentTimeMillis()}")
                tempDir.mkdirs()

                try {
                    // Extract the entire jar
                    injected.execOps.exec {
                        workingDir = tempDir
                        commandLine("jar", "xf", pty4jJar.absolutePath)
                    }

                    // Sign PTY4J native libraries with hardened runtime
                    val nativeFiles = tempDir.walkTopDown().filter {
                        it.isFile && (it.name.endsWith(".dylib") || it.name.contains("spawn-helper"))
                    }.toList()

                    if (nativeFiles.isNotEmpty()) {
                        println("Found ${nativeFiles.size} PTY4J native binary(ies) to sign:")

                        for (nativeFile in nativeFiles) {
                            println("  Signing: ${nativeFile.relativeTo(tempDir)}")

                            // Make executable
                            nativeFile.setExecutable(true)

                            // Sign with hardened runtime
                            try {
                                injected.execOps.exec {
                                    commandLine(
                                        "codesign",
                                        "--force",
                                        "--options", "runtime",
                                        "--sign", developerId,
                                        "--timestamp",
                                        nativeFile.absolutePath
                                    )
                                }

                                // Verify signature
                                injected.execOps.exec {
                                    commandLine("codesign", "-vv", nativeFile.absolutePath)
                                }

                                println("    ✅ Successfully signed ${nativeFile.name}")
                            } catch (e: Exception) {
                                println("    ⚠️ Warning: Failed to sign ${nativeFile.name}: ${e.message}")
                            }
                        }

                        // Recreate the jar with signed native libraries
                        val signedJar = File(pty4jJar.parentFile, "${pty4jJar.nameWithoutExtension}-signed.jar")
                        injected.execOps.exec {
                            workingDir = tempDir
                            commandLine("jar", "cf", signedJar.absolutePath, ".")
                        }

                        // Replace original jar with signed version
                        pty4jJar.delete()
                        signedJar.renameTo(pty4jJar)

                        println("✅ PTY4J jar updated with signed native libraries")

                    } else {
                        println("⚠️ Warning: No PTY4J native binaries found in jar")
                    }

                } finally {
                    // Clean up temp directory
                    tempDir.deleteRecursively()
                }

                // CRITICAL: Re-sign the entire app bundle after modifying the JAR
                println("🔒 Re-signing app bundle after PTY4J modifications...")
                try {
                    injected.execOps.exec {
                        commandLine(
                            "codesign",
                            "--force",
                            "--deep",
                            "--options", "runtime",
                            "--sign", developerId,
                            "--timestamp",
                            "--entitlements", project.file("src/desktopMain/resources/entitlements.plist").absolutePath,
                            appFile.absolutePath
                        )
                    }

                    // Verify the re-signed app
                    injected.execOps.exec {
                        commandLine("codesign", "-vvv", "--deep", "--strict", appFile.absolutePath)
                    }

                    println("✅ App bundle re-signed successfully")
                } catch (e: Exception) {
                    println("❌ Failed to re-sign app bundle: ${e.message}")
                    throw e
                }

            } else {
                println("⚠️ Warning: PTY4J jar not found in app bundle")
            }
        } else {
            println("⚠️ Warning: Built app not found at expected location")
        }
    }
}

// Configure task dependencies for PTY4J signing and DMG packaging
afterEvaluate {
    val isMacOS = System.getProperty("os.name").lowercase().contains("mac")
    val signingDisabled = System.getenv("DISABLE_MACOS_SIGNING") == "true"

    // Make signPty4jBinaries run AFTER createDistributable
    tasks.findByName("signPty4jBinaries")?.apply {
        mustRunAfter("createDistributable")
    }

    // CRITICAL: Make createDistributable finalize with signPty4jBinaries
    // This ensures PTY4J natives are signed before Compose Desktop signs the whole app
    tasks.findByName("createDistributable")?.apply {
        if (isMacOS && !signingDisabled) {
            finalizedBy("signPty4jBinaries")
            println("📝 createDistributable will be finalized by signPty4jBinaries")
        }
    }

    // Make sure signing happens before packaging
    tasks.findByName("packageDmg")?.apply {
        if (isMacOS && !signingDisabled) {
            mustRunAfter("signPty4jBinaries")
            println("📝 packageDmg will run after PTY4J signing")
        }
    }
}
