package top.nalanquede.jukeboxmuse.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import top.nalanquede.jukeboxmuse.common.Platform;

/**
 * /jukeboxmuse (alias /muse) status | skip | clear | debug &lt;platform&gt; &lt;keyword&gt;. Logging in is done from the
 * account page of the music screen (K).
 */
public final class MuseCommands {
    private MuseCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> debug = Commands.literal("debug").requires(src -> src.hasPermission(2));
        for (Platform platform : Platform.values()) {
            debug.then(Commands.literal(platform.id())
                    .then(Commands.argument("keyword", StringArgumentType.greedyString())
                            .executes(ctx -> run(ctx, s -> s.debugSearch(ctx.getSource(), platform, StringArgumentType.getString(ctx, "keyword"))))));
        }
        var root = dispatcher.register(Commands.literal("jukeboxmuse")
                .then(Commands.literal("status").executes(ctx -> run(ctx, s -> {
                    String line = s.statusLine();
                    ctx.getSource().sendSuccess(() -> Component.literal("[点歌] " + line), false);
                })))
                .then(Commands.literal("skip").executes(ctx -> run(ctx, s -> s.skip(ctx.getSource()))))
                .then(Commands.literal("clear").requires(src -> src.hasPermission(2))
                        .executes(ctx -> run(ctx, s -> s.clear(ctx.getSource()))))
                .then(debug));
        // Short alias
        dispatcher.register(Commands.literal("muse").redirect(root));
    }

    private interface Action {
        void run(MusicService service);
    }

    private static int run(CommandContext<CommandSourceStack> ctx, Action action) {
        MusicService service = MusicService.get();
        if (service == null) {
            ctx.getSource().sendFailure(Component.literal("点歌服务未启动"));
            return 0;
        }
        action.run(service);
        return 1;
    }
}
