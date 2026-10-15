pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
    }
}

plugins {
    // Multi-version builds, see https://stonecutter.kikugie.dev/
    id("dev.kikugie.stonecutter") version "0.9.8"
    // Picks fabric-loom-remap (obfuscated Minecraft, before 26.1) or fabric-loom (26.1+) per version
    id("dev.kikugie.loom-back-compat") version "0.4.3"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "jukeboxmuse"

// core: plain Java (platform clients, settings, lyrics, data types), with its own unit tests.
// Its sources are compiled into every mod jar.
include("core")

// Every other source lives in src/ and is built once per node below: versions/{minecraft}-{loader}.
// Loader-specific code is switched with `//? if fabric {` / `//? if neoforge {`.
stonecutter {
    create(rootProject) {
        fun mc(version: String, vararg loaders: String) {
            for (loader in loaders) version("$version-$loader", version).buildscript("build.$loader.gradle.kts")
        }

        mc("1.20.4", "fabric", "neoforge")
        mc("1.20.6", "fabric", "neoforge")
        mc("1.21.1", "fabric", "neoforge")
        mc("1.21.4", "fabric", "neoforge")
        mc("1.21.5", "fabric", "neoforge")
        mc("1.21.8", "fabric", "neoforge")
        mc("1.21.10", "fabric", "neoforge")
        mc("1.21.11", "fabric", "neoforge")
        vcsVersion = "1.21.1-fabric"
    }
}
