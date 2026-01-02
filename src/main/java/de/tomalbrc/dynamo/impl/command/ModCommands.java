package de.tomalbrc.dynamo.impl.command;

import com.mojang.brigadier.CommandDispatcher;
import de.tomalbrc.dynamo.Dynamo;
import de.tomalbrc.dynamo.impl.world.DynamicWorldContainer;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

public class ModCommands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
            register(dispatcher);
        });
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var rootNode =
                literal(Dynamo.MODID).requires(Permissions.require(Dynamo.MODID + ".command", 2))
                        .executes(ctx -> {
                            var player = ctx.getSource().getPlayer();

                            if (player != null) {
                                for (int i = 0; i < 200; i++) {
                                    Dynamo.spawnFor(player);
                                }
                            }

                            return 0;
                        });

        rootNode.then(literal("clear")
                .executes(ctx -> {
                    var level = ctx.getSource().getLevel();
                    var world = ((DynamicWorldContainer) level).getDynamicWorld();

                    world.getElements().removeIf(x -> {
                        x.remove();
                        return true;
                    });

                    return 0;
                }));

        rootNode.then(literal("stats")
                .executes(ctx -> {
                    var level = ctx.getSource().getLevel();
                    var world = ((DynamicWorldContainer) level).getDynamicWorld();

                    ctx.getSource().sendSuccess(() -> {
                        return Component.literal("Rigid bodies: " + world.getPhysicsSpace().getRigidBodyList().size())
                                .append("\n")
                                .append(Component.literal("PCO: " + world.getPhysicsSpace().getPcoList().size()));
                    }, false);

                    return 0;
                }));


        dispatcher.register(rootNode);
    }
}
