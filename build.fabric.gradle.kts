// Build script of the Fabric nodes (versions/{minecraft}-fabric): src/ plus core's sources, built with Fabric Loom.
plugins {
    // Applies fabric-loom-remap or fabric-loom depending on the Minecraft version
    id("dev.kikugie.loom-back-compat")
    // After Loom, whose jar task it publishes
    id("jukeboxmuse.publish")
}

val minecraft = sc.current.version
val modId = project.property("mod.id") as String

version = "${project.property("mod.version")}+$minecraft-fabric"
base.archivesName = modId

val requiredJava = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}

repositories {
    exclusiveContent {
        forRepository { maven("https://maven.parchmentmc.org/") { name = "ParchmentMC" } }
        filter { includeGroup("org.parchmentmc.data") }
    }
}

sourceSets.main {
    java.srcDir(rootProject.file("core/src/main/java"))
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")
    // Mojang names (+ Parchment parameter names), the same as the NeoForge nodes use; a no-op from 26.1 on
    val parchment = findProperty("deps.parchment") as String?
    if (parchment == null) loomx.applyMojangMappings()
    else "mappings"(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-$minecraft:$parchment@zip")
    })

    // `mod*` configurations work on 26.1+ too: loom-back-compat maps them to the plain ones
    modImplementation("net.fabricmc:fabric-loader:${project.property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("deps.fabric_api")}")

    // Gson and SLF4J come with Minecraft; these are nested into the mod jar
    include(implementation("com.google.zxing:core:${project.property("deps.zxing")}")!!)
    include(implementation("javazoom:jlayer:${project.property("deps.jlayer")}")!!)
}

// Client game tests (src/gametest/): `runClientGameTest` starts the game, drives the music screen and saves
// screenshots. Fabric API has them from 1.21.4 on.
if (sc.current.parsed >= "1.21.4") {
    fabricApi.configureTests {
        createSourceSet = true
        modId = "$modId-test"
        enableGameTests = false
        enableClientGameTests = true
        eula = true
    }
}

loom {
    runConfigs.all {
        ideConfigGenerated(true)
        // Shared by every node; the game keeps config/jukeboxmuse/ (settings, logged-in accounts) here.
        // The game tests get a throwaway directory of their own (screenshots end up in its screenshots/).
        runDir = if (name == "clientGameTest") "build/run/clientGameTest" else "../../run/$environment"
        appendProjectPathToConfigName = false
        configName = if (name == "clientGameTest") "Fabric Client Game Test $minecraft"
        else "Fabric ${environment.replaceFirstChar(Char::uppercase)} $minecraft"
    }
    // A world per version: worlds only upgrade, so an older server cannot open a newer one's world
    runConfigs.named("server") { programArgs("--world", "world-$minecraft") }
    // -Pjukeboxmuse.join=<address>: the client joins that server on start (scripts/smoke-test.sh)
    providers.gradleProperty("jukeboxmuse.join").orNull?.let { address ->
        runConfigs.named("client") { programArgs("--quickPlayMultiplayer", address) }
    }
    // -Pjukeboxmuse.screenshots: the client screenshots the music screen once in game, then quits (ScreenshotTour)
    if (providers.gradleProperty("jukeboxmuse.screenshots").isPresent) {
        runConfigs.named("client") { vmArg("-Djukeboxmuse.screenshotTour=${project.name}") }
    }
}

java {
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
    toolchain.languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    processResources {
        val props = mapOf(
            "mod_id" to modId,
            "mod_name" to project.property("mod.name"),
            "mod_version" to project.property("mod.version"),
            "mod_license" to project.property("mod.license"),
            "mod_authors" to project.property("mod.authors"),
            "minecraft" to project.property("mod.mc_compat"),
            "fabric_loader" to project.property("deps.fabric_loader"),
            "java" to requiredJava.majorVersion,
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
        exclude("META-INF/neoforge.mods.toml")
    }

    // Ship the license and third-party notices inside the mod jar
    withType<Jar>().configureEach {
        from(rootProject.file("LICENSE"))
        from(rootProject.file("THIRD_PARTY_NOTICES.md"))
    }

    // Collects the distributable jar into build/libs/{mod version}/ at the root
    register<Copy>("collectJar") {
        group = "build"
        from(loomx.modJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/${project.property("mod.version")}"))
    }
}

extra["mod.mc_releases"] = sc.properties.rawOrNull("mod", "mc_releases")?.asList().orEmpty().map { it.toString() }
extra["mod.java"] = requiredJava
publishMods.file = loomx.modJar.flatMap { it.archiveFile }
