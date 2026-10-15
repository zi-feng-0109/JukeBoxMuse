package top.nalanquede.jukeboxmuse.fabric;

//? if fabric {
import java.nio.file.Path;
import java.util.function.Consumer;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import top.nalanquede.jukeboxmuse.platform.LoaderServices;

final class FabricServices implements LoaderServices {
    /** Set by the client entry point: ClientPlayNetworking only exists on the client. */
    static volatile Consumer<CustomPacketPayload> clientSender = p -> {
        throw new IllegalStateException("Not a client");
    };
    /** The running server, for sending to every player. */
    static volatile MinecraftServer server;

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        clientSender.accept(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        // Vanilla clients may join a Fabric server; they cannot receive the mod's packets
        if (ServerPlayNetworking.canSend(player, payload.type())) ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToAllPlayers(CustomPacketPayload payload) {
        MinecraftServer s = server;
        if (s == null) return;
        for (ServerPlayer player : PlayerLookup.all(s)) {
            sendToPlayer(player, payload);
        }
    }
}
//?}
