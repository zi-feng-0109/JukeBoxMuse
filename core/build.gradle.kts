// Minecraft-independent code shared by every loader and game version: the KuGou / NetEase / QQ Music clients,
// settings, lyric parsing and the data types the mod sends around. Must never depend on Minecraft or a mod loader.
// The mod nodes compile these sources into their own jar; this project only exists for the IDE and the unit tests.
plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

dependencies {
    // Provided at runtime: Gson and SLF4J by Minecraft, ZXing bundled into the mod jar.
    // Versions match what Minecraft 1.21.1 ships.
    compileOnly("com.google.code.gson:gson:2.10.1")
    compileOnly("org.slf4j:slf4j-api:2.0.9")
    compileOnly("com.google.zxing:core:3.5.3")

    testImplementation("com.google.code.gson:gson:2.10.1")
    testImplementation("org.slf4j:slf4j-api:2.0.9")
    testImplementation("com.google.zxing:core:3.5.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // LiveApiTest only runs with JUKEBOXMUSE_LIVE=1; it must see the variable even from a long-lived daemon
    environment("JUKEBOXMUSE_LIVE", System.getenv("JUKEBOXMUSE_LIVE") ?: "")
}
