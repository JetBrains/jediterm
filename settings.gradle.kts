pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "jediterm"
include(
    ":core",
    ":ui",
    ":JediTerm",
    ":jediterm-pty",
    ":jediterm-core-mpp",
    ":compose-ui"
)

