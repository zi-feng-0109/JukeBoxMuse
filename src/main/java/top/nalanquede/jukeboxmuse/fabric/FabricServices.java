package top.nalanquede.jukeboxmuse.fabric;

//? if fabric {
import java.nio.file.Path;
import java.util.function.Consumer;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import top.nalanquede.jukeboxmuse.network.MusePayloads.Payload;
import top.nalanquede.jukeboxmuse.platform.LoaderServices;

final class FabricServices implements LoaderServices {
    /** Set by the client entry point: ClientPlayNetworking only exists on the client. */
    static volatile Consumer<Payload> clientSender = p -> {
        throw new IllegalStateException("Not a client");
    };

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public void sendToServer(Payload payload) {
        clientSender.accept(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, Payload payload) {
        // Vanilla clients may join a Fabric server; they cannot receive the mod's packets
        //? if >=1.20.5 {
        if (ServerPlayNetworking.canSend(player, payload.type().mcType())) {
            ServerPlayNetworking.send(player, new top.nalanquede.jukeboxmuse.network.McPacket(payload));
        }
        //?} else {
        /*if (ServerPlayNetworking.canSend(player, payload.type().id())) {
            ServerPlayNetworking.send(player, payload.type().id(), FabricNetwork.write(payload));
        }
        *///?}
    }
}
//?}
