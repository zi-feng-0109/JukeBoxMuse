// Build script of the NeoForge nodes (versions/{minecraft}-neoforge): src/ plus core's sources, built with ModDevGradle.
plugins {
    id("net.neoforged.moddev") version "2.0.148"
    id("neoforge-mutex")
    id("jukeboxmuse.publish")
}

val minecraft = sc.current.version
val modId = project.property("mod.id") as String

version = "${project.property("mod.version")}+$minecraft-neoforge"
base.archivesName = modId

val requiredJava = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

// NeoForge 20.4's net.minecraftforge:unsafe asks for log4j 2.11.+; a dynamic version is looked up online again every
// 24 hours, so builds fail whenever a repository is unreachable. Pin what it resolves to.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.apache.logging.log4j" && requested.version == "2.11.+") useVersion("2.11.2")
    }
}

sourceSets.main {
    java.srcDir(rootProject.file("core/src/main/java"))
}

neoForge {
    version = project.property("deps.neoforge") as String

    (findProperty("deps.parchment") as String?)?.let {
        parchment {
            mappingsVersion = it
            minecraftVersion = minecraft
        }
    }

    runs {
        register("client") {
            client()
            // Shared by every node; the game keeps config/jukeboxmuse/ (settings, logged-in accounts) here
            gameDirectory = rootProject.file("run/client")
            ideName = "NeoForge Client $minecraft"
            // -Pjukeboxmuse.join=<address>: the client joins that server on start (scripts/smoke-test.sh)
            providers.gradleProperty("jukeboxmuse.join").orNull?.let { programArguments.addAll("--quickPlayMultiplayer", it) }
            // -Pjukeboxmuse.screenshots: the client screenshots the music screen once in game, then quits (ScreenshotTour)
            if (providers.gradleProperty("jukeboxmuse.screenshots").isPresent) {
                systemProperty("jukeboxmuse.screenshotTour", project.name)
            }
        }
        register("server") {
            server()
            // A world per version: worlds only upgrade, so an older server cannot open a newer one's world
            programArguments.addAll("--nogui", "--world", "world-$minecraft")
            gameDirectory = rootProject.file("run/server")
            ideName = "NeoForge Server $minecraft"
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    // Gson and SLF4J come with Minecraft; these are bundled into the mod jar
    jarJar(implementation("com.google.zxing:core:${project.property("deps.zxing")}")!!)
    jarJar(implementation("javazoom:jlayer:${project.property("deps.jlayer")}")!!)
    // Before 1.21.9 the game in runClient only sees libraries on this extra classpath
    if (sc.current.parsed < "1.21.9") {
        "additionalRuntimeClasspath"("com.google.zxing:core:${project.property("deps.zxing")}")
        "additionalRuntimeClasspath"("javazoom:jlayer:${project.property("deps.jlayer")}")
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
            "neoforge" to project.property("deps.neoforge"),
        )
        inputs.properties(props)
        // NeoForge reads META-INF/mods.toml before 20.5. Decided here: the closure runs at execution time, where the
        // configuration cache allows no project references (sc)
        val legacyModsToml = sc.current.parsed < "1.20.5"
        inputs.property("legacyModsToml", legacyModsToml)
        filesMatching("META-INF/neoforge.mods.toml") {
            expand(props)
            if (legacyModsToml) name = "mods.toml"
        }
        exclude("fabric.mod.json")
    }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }

    // Ship the license and third-party notices inside the mod jar
    withType<Jar>().configureEach {
        from(rootProject.file("LICENSE"))
        from(rootProject.file("THIRD_PARTY_NOTICES.md"))
    }

    // Collects the distributable jar into build/libs/{mod version}/ at the root
    register<Copy>("collectJar") {
        group = "build"
        from(jar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/${project.property("mod.version")}"))
    }
}

extra["mod.mc_releases"] = sc.properties.rawOrNull("mod", "mc_releases")?.asList().orEmpty().map { it.toString() }
extra["mod.java"] = requiredJava
publishMods.file = tasks.jar.flatMap { it.archiveFile }
