package dev.famulus.fabric;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.item.Items;

/**
 * Measures whether exploring makes a resource reachable that gathering alone cannot find.
 *
 * <p>A superflat world contains no sand at any distance, so sand is placed deliberately, far enough
 * away that it is outside the loaded area when the test begins. That is the only way to pose the
 * question honestly: "cannot find it" must mean "it is too far", not "it does not exist".
 *
 * <p>The control matters as much as the treatment. First a gather is attempted with a short budget
 * and must fail, proving the sand really is out of reach. Only then is exploring given a chance.
 *
 * <p>Opt-in, because it is slow and diagnostic:
 * <pre>FAMULUS_PROBE_EXPLORE=1 GRADLE_USER_HOME=.cache/gradle ./gradlew :fabric:runClientGameTest</pre>
 */
@SuppressWarnings("UnstableApiUsage")
public final class ExploreProbeGameTest implements FabricClientGameTest {
    private static final int GROUND_Y = -61;
    /** Far enough to be outside the loaded area at spawn, near enough to reach in a few minutes. */
    private static final int SAND_DISTANCE = 260;
    private static final int CONTROL_TICKS = 600;
    private static final int EXPLORE_TICKS = 1200;
    private static final int GATHER_TICKS = 1200;

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!"1".equals(System.getenv("FAMULUS_PROBE_EXPLORE"))) {
            return;
        }
        try (TestSingleplayerContext world = context.worldBuilder()
                .adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            world.getConnection().waitForChunksDownload();
            world.getServer().runCommand("tp @a 0.5 " + (GROUND_Y + 1) + " 0.5 0 0");
            world.getServer().runCommand("clear @a");
            world.getServer().runCommand("give @a minecraft:diamond_shovel 1");
            world.getServer().runCommand("gamerule randomTickSpeed 0");
            // A patch big enough that arriving anywhere near it is enough to gather from it.
            world.getServer().runCommand("fill " + SAND_DISTANCE + " " + (GROUND_Y + 1) + " "
                    + SAND_DISTANCE + " " + (SAND_DISTANCE + 15) + " " + (GROUND_Y + 1) + " "
                    + (SAND_DISTANCE + 15) + " minecraft:sand");
            context.waitTick();
            world.getConnection().waitForChunksRender();

            // Control: the sand must be genuinely out of reach to begin with.
            command(context, "/famulus queue minecraft:sand=8");
            int controlCount = waitForSand(context, CONTROL_TICKS);
            command(context, "/famulus stop");
            context.waitTick();
            System.out.println("[FamulusExplore] control sand after "
                    + CONTROL_TICKS + " ticks: " + controlCount);
            context.takeScreenshot("famulus-explore-control");
            if (controlCount >= 8) {
                throw new AssertionError("The sand was reachable without exploring at "
                        + SAND_DISTANCE + " blocks, so this probe proves nothing. Move it further.");
            }

            // Treatment: explore, then try again.
            // Exploring never finishes on its own; it runs until stopped. Bound it by time, which
            // is exactly what FamulusAgent does with exploreTimeoutMillis.
            //
            // Distance travelled is recorded because without it a zero result cannot be read: it
            // would not say whether exploring never engaged, or engaged and was simply too slow.
            context.runOnClient(client -> EXPLORER.start(client));
            context.waitTicks(20);
            boolean engaged = context.computeOnClient(client -> EXPLORER.isActive());
            System.out.println("[FamulusExplore] explore process active after 1s: " + engaged);
            double travelled = 0;
            for (int elapsed = 0; elapsed < EXPLORE_TICKS; elapsed += 100) {
                context.waitTicks(100);
                travelled = context.computeOnClient(ExploreProbeGameTest::distanceToSand);
                System.out.println("[FamulusExplore] after " + (elapsed + 100) / 20 + "s: "
                        + Math.round(travelled) + " blocks from the sand, active="
                        + context.computeOnClient(client -> EXPLORER.isActive()));
            }
            context.runOnClient(client -> EXPLORER.cancel());
            System.out.println("[FamulusExplore] closed to " + Math.round(travelled)
                    + " blocks from the sand");
            context.waitTick();
            context.takeScreenshot("famulus-explore-after-exploring");

            command(context, "/famulus queue minecraft:sand=8");
            int afterExploring = waitForSand(context, GATHER_TICKS);
            command(context, "/famulus stop");
            context.waitTick();
            context.takeScreenshot("famulus-explore-result");
            System.out.println("[FamulusExplore] RESULT control=" + controlCount
                    + " afterExploring=" + afterExploring + " distance=" + SAND_DISTANCE);
        }
    }

    private static final BaritoneExplorer EXPLORER = new BaritoneExplorer();

    private static int waitForSand(ClientGameTestContext context, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            if (context.computeOnClient(ExploreProbeGameTest::sandCount) >= 8) {
                break;
            }
            context.waitTick();
        }
        return context.computeOnClient(ExploreProbeGameTest::sandCount);
    }

    /**
     * Distance to the sand, not to spawn. Distance from spawn says only that the player went
     * somewhere, and exploring is undirected, so somewhere is usually the wrong way.
     */
    private static double distanceToSand(Minecraft client) {
        if (client.player == null) {
            return Double.MAX_VALUE;
        }
        double dx = client.player.getX() - (SAND_DISTANCE + 7.5);
        double dz = client.player.getZ() - (SAND_DISTANCE + 7.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static int sandCount(Minecraft client) {
        return client.player == null ? 0 : client.player.getInventory().countItem(Items.SAND);
    }

    private static void command(ClientGameTestContext context, String command) {
        context.getInput().pressKey(options -> options.keyChat);
        context.getInput().typeChars(command);
        context.getInput().holdKeyFor(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN, 0);
        context.waitTick();
    }
}
