package top.nalanquede.jukeboxmuse.neoforge;

//? if neoforge {
/*import java.nio.file.Path;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import top.nalanquede.jukeboxmuse.network.McPacket;
import top.nalanquede.jukeboxmuse.network.MusePayloads.Payload;
import top.nalanquede.jukeboxmuse.platform.LoaderServices;

final class NeoForgeServices implements LoaderServices {
    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public void sendToServer(Payload payload) {
        //? if >=1.21.6 {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new McPacket(payload));
        //?} elif >=1.20.5 {
        /^PacketDistributor.sendToServer(new McPacket(payload));
        ^///?} else
        /^PacketDistributor.SERVER.noArg().send(new McPacket(payload));^/
    }

    @Override
    public void sendToPlayer(ServerPlayer player, Payload payload) {
        // NeoForge refuses connections without the mod (its channels are required), so every player has it
        //? if >=1.20.5 {
        PacketDistributor.sendToPlayer(player, new McPacket(payload));
        //?} else
        /^PacketDistributor.PLAYER.with(player).send(new McPacket(payload));^/
    }
}
*///?}
