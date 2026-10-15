// Uploads a node's mod jar to Modrinth and CurseForge with `./gradlew publishMods` (or `publishAll` on the root).
// Tokens come from the MODRINTH_TOKEN / CURSEFORGE_TOKEN environment variables; without them the upload is a dry run.
// A platform is skipped while its project id (publish.* in stonecutter.properties.toml) is empty.
//
// The node's name ({minecraft}-{loader}) gives the version and loader. The loader build script sets `publishMods.file`
// and these extra properties:
//   mod.mc_releases  List<String>, the Minecraft releases to mark compatible
//   mod.java         JavaVersion the jar targets
plugins {
    id("me.modmuss50.mod-publish-plugin")
}

val modVersion = property("mod.version") as String
val modName = property("mod.name") as String
val minecraft = project.name.substringBeforeLast('-')
val loader = project.name.substringAfterLast('-')
val loaderName = mapOf("fabric" to "Fabric", "neoforge" to "NeoForge").getValue(loader)

/** The "## <version>" section of CHANGELOG.md. */
fun changelogFor(version: String): String {
    val lines = rootProject.file("CHANGELOG.md").readLines(Charsets.UTF_8)
    val start = lines.indexOfFirst { it.trim() == "## $version" }
    if (start < 0) throw GradleException("CHANGELOG.md has no '## $version' section")
    val end = lines.drop(start + 1).indexOfFirst { it.startsWith("## ") }.let { if (it < 0) lines.size else start + 1 + it }
    return lines.subList(start + 1, end).joinToString("\n").trim()
}

@Suppress("UNCHECKED_CAST")
val mcReleases = provider { extra["mod.mc_releases"] as List<String> }
val javaVersion = provider { extra["mod.java"] as JavaVersion }

val modrinthToken = providers.environmentVariable("MODRINTH_TOKEN")
val curseforgeToken = providers.environmentVariable("CURSEFORGE_TOKEN")
val modrinthId = findProperty("publish.modrinth") as String?
val curseforgeId = findProperty("publish.curseforge") as String?

publishMods {
    changelog = provider { changelogFor(modVersion) }
    type = STABLE
    displayName = "$modName $modVersion ($loaderName $minecraft)"
    version = "$modVersion+$minecraft-$loader"
    modLoaders.add(loader)
    dryRun = !modrinthToken.isPresent && !curseforgeToken.isPresent

    if (!modrinthId.isNullOrEmpty()) {
        modrinth {
            accessToken = modrinthToken
            projectId = modrinthId
            minecraftVersions.addAll(mcReleases)
            if (loader == "fabric") requires("fabric-api")
        }
    }
    if (!curseforgeId.isNullOrEmpty()) {
        curseforge {
            accessToken = curseforgeToken
            projectId = curseforgeId
            minecraftVersions.addAll(mcReleases)
            javaVersions.add(javaVersion)
            // The mod must be installed on both sides
            client = true
            server = true
            if (loader == "fabric") requires("fabric-api")
        }
    }
}
