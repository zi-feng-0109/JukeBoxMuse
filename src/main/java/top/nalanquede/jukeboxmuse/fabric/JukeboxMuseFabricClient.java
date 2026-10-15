package top.nalanquede.jukeboxmuse.fabric;

//? if fabric {
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import top.nalanquede.jukeboxmuse.client.ClientHandlers;
import top.nalanquede.jukeboxmuse.client.ClientHooks;
import top.nalanquede.jukeboxmuse.network.MusePayloads;
import top.nalanquede.jukeboxmuse.network.PayloadType;

/** Fabric client entry point: forwards client events to {@link ClientHooks}. Runs after {@link JukeboxMuseFabric}. */
public final class JukeboxMuseFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        //? if >=1.20.5 {
        FabricServices.clientSender = p -> ClientPlayNetworking.send(new top.nalanquede.jukeboxmuse.network.McPacket(p));
        //?} else
        /*FabricServices.clientSender = p -> ClientPlayNetworking.send(p.type().id(), FabricNetwork.write(p));*/
        ClientHooks.init();
        //? if >=1.21.9
        /*net.minecraft.client.KeyMapping.Category.register(ClientHooks.CATEGORY.id());*/
        KeyBindingHelper.registerKeyBinding(ClientHooks.OPEN_KEY);

        for (PayloadType<?> type : MusePayloads.TO_CLIENT) {
            //? if >=1.20.5 {
            // Payload receivers run on the client thread
            ClientPlayNetworking.registerGlobalReceiver(type.mcType(), (p, ctx) -> ClientHandlers.handle(p.payload()));
            //?} else {
            /*// Channel receivers run on the network thread, and the buffer is released once they return
            ClientPlayNetworking.registerGlobalReceiver(type.id(), (client, handler, buf, sender) -> {
                var payload = type.decode(buf);
                client.execute(() -> ClientHandlers.handle(payload));
            });
            *///?}
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientHooks.tick());
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> ClientHooks.renderHud(graphics));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientHooks::disconnect));
    }
}
//?}
