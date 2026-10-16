plugins {
    id("dev.kikugie.stonecutter")
    // Fabric Loom, applied per node by loom-back-compat: fabric-loom-remap for obfuscated Minecraft (before 26.1),
    // fabric-loom for 26.1+. 1.19 needs Gradle 9.8 running on Java 25 (gradle/gradle-daemon-jvm.properties).
    id("net.fabricmc.fabric-loom-remap") version "1.19.1" apply false
    id("net.fabricmc.fabric-loom") version "1.19.1" apply false
}

// The node the sources in src/ are currently written for. Change it with the "Set active project to ..." tasks.
stonecutter active "1.21.1-fabric"

stonecutter parameters {
    val (_, loader) = current.project.split('-', limit = 2)

    // Picks the [mod], ["<minecraft>"], [<loader>] and [<loader>."<minecraft>"] sections of stonecutter.properties.toml
    properties {
        tags(current.version, loader)
    }

    // `//? if fabric {` and `//? if neoforge {`
    constants {
        match(loader, "fabric", "neoforge")
    }

    // Renames in Mojang's names, applied to every file
    replacements {
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }
    }
}
