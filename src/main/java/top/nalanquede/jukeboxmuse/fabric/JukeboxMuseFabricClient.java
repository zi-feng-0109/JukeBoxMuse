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
import top.nalanquede.jukeboxmuse.network.MusePayloads.*;

/** Fabric client entry point: forwards client events to {@link ClientHooks}. Runs after {@link JukeboxMuseFabric}. */
public final class JukeboxMuseFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricServices.clientSender = ClientPlayNetworking::send;
        ClientHooks.init();
        //? if >=1.21.9
        /*net.minecraft.client.KeyMapping.Category.register(ClientHooks.CATEGORY.id());*/
        KeyBindingHelper.registerKeyBinding(ClientHooks.OPEN_KEY);

        // Fabric runs play receivers on the client thread
        ClientPlayNetworking.registerGlobalReceiver(SearchResultS2C.TYPE, (p, ctx) -> ClientHandlers.onSearchResult(p));
        ClientPlayNetworking.registerGlobalReceiver(QueueS2C.TYPE, (p, ctx) -> ClientHandlers.onQueue(p));
        ClientPlayNetworking.registerGlobalReceiver(PlayS2C.TYPE, (p, ctx) -> ClientHandlers.onPlay(p));
        ClientPlayNetworking.registerGlobalReceiver(StopS2C.TYPE, (p, ctx) -> ClientHandlers.onStop());
        ClientPlayNetworking.registerGlobalReceiver(AccountS2C.TYPE, (p, ctx) -> ClientHandlers.onAccount(p));

        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientHooks.tick());
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> ClientHooks.renderHud(graphics));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientHooks::disconnect));
    }
}
//?}
