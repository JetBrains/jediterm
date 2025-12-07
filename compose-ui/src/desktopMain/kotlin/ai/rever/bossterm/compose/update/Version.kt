package ai.rever.bossterm.compose.update

import kotlinx.serialization.Serializable

/**
 * Version management and comparison utilities for BossTerm application.
 *
 * Supports semantic versioning with pre-release tags:
 * - major.minor.patch (e.g., 1.0.0)
 * - major.minor.patch-prerelease (e.g., 1.0.0-beta)
 */
@Serializable
data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null
) : Comparable<Version> {

    companion object {
        /**
         * Current application version.
         * This is loaded from the VERSION file at build time.
         */
        val CURRENT: Version by lazy {
            // Try to load from system property set during build
            val versionString = System.getProperty("bossterm.version")
                ?: System.getenv("BOSSTERM_VERSION")
                ?: "1.0.0"
            parse(versionString) ?: Version(1, 0, 0)
        }

        /**
         * Parse a version string into a Version object.
         *
         * @param versionString Version string (e.g., "1.0.0", "v1.0.0", "1.0.0-beta")
         * @return Parsed Version or null if parsing fails
         */
        fun parse(versionString: String): Version? {
            return try {
                val cleanVersion = versionString.removePrefix("v").trim()
                val parts = cleanVersion.split("-", limit = 2)
                val versionPart = parts[0]
                val preRelease = parts.getOrNull(1)

                val versionNumbers = versionPart.split(".")
                if (versionNumbers.size >= 3) {
                    Version(
                        major = versionNumbers[0].toInt(),
                        minor = versionNumbers[1].toInt(),
                        patch = versionNumbers[2].toInt(),
                        preRelease = preRelease
                    )
                } else if (versionNumbers.size == 2) {
                    // Handle "1.0" format
                    Version(
                        major = versionNumbers[0].toInt(),
                        minor = versionNumbers[1].toInt(),
                        patch = 0,
                        preRelease = preRelease
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun compareTo(other: Version): Int {
        // Compare major.minor.patch
        val result = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
        if (result != 0) return result

        // Handle pre-release versions (pre-release < stable)
        return when {
            this.preRelease == null && other.preRelease == null -> 0
            this.preRelease == null && other.preRelease != null -> 1  // stable > prerelease
            this.preRelease != null && other.preRelease == null -> -1 // prerelease < stable
            else -> this.preRelease!!.compareTo(other.preRelease!!) // compare prerelease strings
        }
    }

    override fun toString(): String {
        return if (preRelease != null) {
            "$major.$minor.$patch-$preRelease"
        } else {
            "$major.$minor.$patch"
        }
    }

    fun isNewerThan(other: Version): Boolean = this > other
}
