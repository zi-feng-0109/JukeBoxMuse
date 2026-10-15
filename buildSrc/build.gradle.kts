// Convention plugins shared by the per-loader build scripts (src/main/kotlin/*.gradle.kts)
plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    // Used by jukeboxmuse.publish
    implementation("me.modmuss50:mod-publish-plugin:2.2.1")
}
