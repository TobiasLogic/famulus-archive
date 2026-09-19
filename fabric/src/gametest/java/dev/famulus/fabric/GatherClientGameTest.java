package dev.famulus.fabric;

import baritone.api.BaritoneAPI;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Exercises the shipped command and Baritone execution in a newly created test world. */
@SuppressWarnings("UnstableApiUsage")
public final class GatherClientGameTest implements FabricClientGameTest {
    private static final int TARGET = 32;
    private static final int GATHER_TIMEOUT_TICKS = 6000;

    @Override
    public void runTest(ClientGameTestContext context) {
        // Fabric's consistent defaults create a fresh flat world in the Gradle test run directory.
        try (TestSingleplayerContext world = context.worldBuilder()
                .adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            world.getConnection().waitForChunksDownload();
            world.getServer().runCommand("tp @a 0.5 -60 0.5 0 0");
            world.getServer().runCommand("clear @a");
            world.getServer().runCommand("give @a minecraft:diamond_axe 1");
            world.getServer().runCommand("fill 2 -60 2 9 -60 5 minecraft:oak_log");
            context.waitTick();
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> require(oakCount(client) == 0, "Fixture must start with no oak logs"));

            command(context, "/famulus gather minecraft:oak_log 32");
            context.waitFor(client -> isMining(), 200);
            context.takeScreenshot("famulus-gather-running");
            context.waitFor(client -> oakCount(client) >= TARGET && !isMining(), GATHER_TIMEOUT_TICKS);
            context.runOnClient(client -> require(oakCount(client) == TARGET,
                    "Gather must collect the 32 fixture logs and release Baritone"));
            command(context, "/famulus status");
            context.takeScreenshot("famulus-gather-completed");
            context.getInput().pressKey(options -> options.keyInventory);
            context.waitTick();
            context.takeScreenshot("famulus-inventory-32-oak-logs");
            context.setScreen(() -> null);

            // An available log must remain untouched when the requested inventory total is satisfied.
            BlockPos sentinel = new BlockPos(2, -60, 0);
            world.getServer().runCommand("setblock 2 -60 0 minecraft:oak_log");
            context.waitTick();
            world.getConnection().waitForClientboundPackets();
            command(context, "/famulus gather minecraft:oak_log 32");
            for (int tick = 0; tick < 40; tick++) {
                context.runOnClient(client -> {
                    require(!isMining(), "An already satisfied request must not start mining");
                    require(oakCount(client) == TARGET, "An already satisfied request must preserve inventory");
                    require(client.level.getBlockState(sentinel).is(Blocks.OAK_LOG),
                            "An already satisfied request must leave the sentinel log untouched");
                });
                context.waitTick();
            }
            context.takeScreenshot("famulus-already-satisfied");

            // Start another real mining task, then use the public stop command and check it stays stopped.
            world.getServer().runCommand("fill 2 -60 2 9 -60 9 minecraft:oak_log");
            context.waitTick();
            world.getConnection().waitForClientboundPackets();
            command(context, "/famulus gather minecraft:oak_log 96");
            context.waitFor(client -> isMining(), 200);
            command(context, "/famulus status");
            command(context, "/famulus stop");
            context.waitFor(client -> !isMining(), 200);
            for (int tick = 0; tick < 20; tick++) {
                context.runOnClient(client -> require(!isMining(), "Stop must leave Baritone inactive"));
                context.waitTick();
            }
            command(context, "/famulus status");
            context.takeScreenshot("famulus-stopped");
        }
    }

    private static void command(ClientGameTestContext context, String command) {
        context.getInput().pressKey(options -> options.keyChat);
        context.getInput().typeChars(command);
        context.getInput().holdKeyFor(InputConstants.KEY_RETURN, 0);
        context.waitTick();
    }

    private static int oakCount(Minecraft client) {
        return client.player.getInventory().countItem(Items.OAK_LOG);
    }

    private static boolean isMining() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().isActive();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
