plugins {
    id("dev.kikugie.stonecutter")
    // Fabric Loom, applied per node by loom-back-compat. 1.15.x until the Gradle wrapper is upgraded:
    // 1.16 needs Gradle 9.4, 1.18+ (needed for 26.1's fabric-loom) runs Gradle on Java 25
    id("net.fabricmc.fabric-loom-remap") version "1.15.5" apply false
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
}
