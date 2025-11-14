import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.library")
    `maven-publish`
}

group = "org.jetbrains.jediterm"

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
            baseName = "JediTermCompose"
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
                outputFileName = "jediterm-compose.js"
            }
        }
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "jediterm-compose.wasm.js"
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

        val jvmMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(project(":jediterm-core-mpp"))
            }
        }

        // Android source set
        val androidMain by getting {
            dependsOn(jvmMain)
            dependencies {
                implementation("androidx.activity:activity-compose:1.9.3")
                implementation("androidx.appcompat:appcompat:1.7.0")
                implementation("androidx.core:core-ktx:1.15.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
            }
        }

        // iOS source sets
        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
        }

        // Desktop source set
        val desktopMain by getting {
            dependsOn(jvmMain)
            dependencies {
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
    namespace = "org.jetbrains.jediterm.compose"
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
        mainClass = "org.jetbrains.jediterm.compose.demo.MainKt"
    }
}

compose.experimental {
    web.application {}
}

publishing {
    publications {
        create<MavenPublication>("composeUi") {
            artifactId = "jediterm-compose-ui"
            pom {
                name.set("JediTerm Compose UI")
                description.set("Compose Multiplatform UI for JediTerm")
                url.set("https://github.com/JetBrains/jediterm")
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
