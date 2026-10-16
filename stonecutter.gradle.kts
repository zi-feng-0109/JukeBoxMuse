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
        // 26.1 renamed the GUI drawing API ("extracting" render state) without changing its arguments. Only the
        // mod's own spellings are listed, so its methods named render* stay as they are.
        string(current.parsed >= "26.1") {
            replace("GuiGraphics g", "GuiGraphicsExtractor g")
            replace("import net.minecraft.client.gui.GuiGraphics;", "import net.minecraft.client.gui.GuiGraphicsExtractor;")
            replace("g.drawCenteredString(", "g.centeredText(")
            replace("g.drawString(", "g.text(")
            replace("g.renderItem(", "g.item(")
            replace("import net.minecraft.client.gui.components.PlayerFaceRenderer;", "import net.minecraft.client.gui.components.PlayerFaceExtractor;")
            replace("PlayerFaceRenderer.draw(", "PlayerFaceExtractor.extractRenderState(")
        }
    }
}
