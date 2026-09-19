# Minecraft integration dependencies

Checked: **2026-09-19**. Target: **Minecraft Java Edition 26.2**. The possible 1.21.x port remains undecided and is not implied by these dependencies. These are verified upstream compatibility facts; they do not establish that Famulus has passed an in-game test. See `TESTING.md` for project test results.

## Initial version pins

| Component | Pin | Evidence |
| --- | --- | --- |
| Minecraft Java Edition | `26.2` | [Baritone release explicitly supporting 26.2](https://github.com/cabaletta/baritone/releases/tag/v1.19.0) |
| Java language/runtime | `25` | [Official Fabric example build for 26.2](https://raw.githubusercontent.com/FabricMC/fabric-example-mod/26.2/build.gradle), [Baritone v1.19.0 properties](https://raw.githubusercontent.com/cabaletta/baritone/v1.19.0/gradle.properties) |
| Gradle | `9.5.1` | [Official example wrapper](https://raw.githubusercontent.com/FabricMC/fabric-example-mod/26.2/gradle/wrapper/gradle-wrapper.properties) |
| Fabric Loom | `1.17.21` | [Published stable Maven artifact](https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.17.21/) |
| Fabric Loader | `0.19.5` | [Official example 26.2 properties](https://raw.githubusercontent.com/FabricMC/fabric-example-mod/26.2/gradle.properties) |
| Fabric API | `0.160.0+26.2` | [Official example 26.2 properties](https://raw.githubusercontent.com/FabricMC/fabric-example-mod/26.2/gradle.properties) |
| Baritone | `1.19.0`, Fabric **API** distribution | [Official release](https://github.com/cabaletta/baritone/releases/tag/v1.19.0), [release assets](https://github.com/cabaletta/baritone/releases/expanded_assets/v1.19.0) |

The official Fabric 26.2 example currently uses `1.17-SNAPSHOT`; this project chooses the published `1.17.21` release for a fixed dependency. Fabric's [26.2 announcement](https://fabricmc.net/2026/06/15/262.html) recommends the Loom 1.17 and Gradle 9.5.1 families. Later toolchain releases exist; upgrading is separate from preserving the 26.2 target.

Use the `net.fabricmc.fabric-loom` plugin. Minecraft 26.x is unobfuscated: dependencies use ordinary `implementation` / `compileOnly` / `runtimeOnly` configurations, with the ordinary `jar` task. Do not copy old Yarn mappings, `modImplementation`, or `remapJar` conventions into this build. See the [Fabric 26.1 migration announcement](https://fabricmc.net/2026/03/14/261.html) and the [26.2 example build](https://raw.githubusercontent.com/FabricMC/fabric-example-mod/26.2/build.gradle).

## Exact Baritone distribution

Use [baritone-api-fabric-1.19.0.jar](https://github.com/cabaletta/baritone/releases/download/v1.19.0/baritone-api-fabric-1.19.0.jar), released 2026-08-31.

SHA-256, as published on the [official release assets page](https://github.com/cabaletta/baritone/releases/expanded_assets/v1.19.0):

```text
eca6e2fdf43c6657fe9fea10a8f9a7572d78dc91ad389b998b808bd28fa5b5d1
```

The word **API** does not mean interfaces only. This distribution is a complete Fabric mod, containing the execution implementation and preserved public API names. Upstream specifically recommends the API distribution when other mods integrate with Baritone. The standalone distribution obfuscates those public names, so it is unsuitable for Famulus's direct API calls. The unoptimized distribution is intended for debugging. The similarly named jar without `-fabric` is a different launchwrapper distribution. See [upstream installation and artifact explanations](https://raw.githubusercontent.com/cabaletta/baritone/v1.19.0/SETUP.md).

Install **one** matching Baritone Fabric API jar into the same instance's `mods` directory as Famulus and Fabric API. For development, put that jar on the compilation and runtime classpaths; keep it external to Famulus's distributable. Do not install API and standalone variants simultaneously. The [upstream Fabric manifest](https://raw.githubusercontent.com/cabaletta/baritone/v1.19.0/fabric/src/main/resources/fabric.mod.json) identifies the mod as `baritone`, requires Fabric Loader `>=0.19.3`, and lists Minecraft `26.2`. The [Fabric packaging build](https://raw.githubusercontent.com/cabaletta/baritone/v1.19.0/fabric/build.gradle) includes the common implementation outputs.

Baritone's [v1.19.0 license file](https://raw.githubusercontent.com/cabaletta/baritone/v1.19.0/LICENSE) is LGPL version 3, and its source headers allow later LGPL versions. Its Fabric manifest labels it `LGPL-3.0`. Preserve the upstream license and attribution if redistributing it. This project uses Baritone as a separate dependency; it does not copy its implementation source. The source and release links above provide the provenance for this dependency.

## Relevant API contracts

All Minecraft and Baritone execution calls must remain on the client game thread. Future model/network calls must run separately, handing validated decisions back to that thread.

| Purpose | 26.2 / Baritone 1.19.0 API | Source |
| --- | --- | --- |
| Obtain Baritone | `BaritoneAPI.getProvider().getPrimaryBaritone()` | [API entrypoint](https://raw.githubusercontent.com/cabaletta/baritone/v1.19.0/src/api/java/baritone/api/BaritoneAPI.java) and [upstream usage example](https://raw.githubusercontent.com/cabaletta/baritone/v1.19.0/README.md) |
| Start gathering mineable blocks | `getMineProcess().mineByName(int quantity, String... blocks)` | [IMineProcess](https://raw.githubusercontent.com/cabaletta/baritone/v1.19.0/src/api/java/baritone/api/process/IMineProcess.java) |
| Check mining process | `getMineProcess().isActive()` | [IBaritoneProcess](https://raw.githubusercontent.com/cabaletta/baritone/v1.19.0/src/api/java/baritone/api/process/IBaritoneProcess.java) |
| Cancel execution | `getPathingBehavior().cancelEverything()` | [IPathingBehavior](https://raw.githubusercontent.com/cabaletta/baritone/v1.19.0/src/api/java/baritone/api/behavior/IPathingBehavior.java) |
| Observe inventory | `player.getInventory().getNonEquipmentItems()` | [26.2 MineProcess usage](https://raw.githubusercontent.com/cabaletta/baritone/v1.19.0/src/main/java/baritone/process/MineProcess.java) |
| Tick observation/control | `ClientTickEvents.END_CLIENT_TICK.register(client -> ...)` | [Fabric ClientTickEvents](https://raw.githubusercontent.com/FabricMC/fabric/26.2/fabric-lifecycle-events-v1/src/client/java/net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents.java) |
| Register client commands | `ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> ...)` | [Fabric callback](https://raw.githubusercontent.com/FabricMC/fabric/26.2/fabric-command-api-v2/src/client/java/net/fabricmc/fabric/api/client/command/v2/ClientCommandRegistrationCallback.java) |
| Construct client command arguments | `ClientCommands.literal(...)`, `ClientCommands.argument(...)` | [Fabric ClientCommands](https://raw.githubusercontent.com/FabricMC/fabric/26.2/fabric-command-api-v2/src/client/java/net/fabricmc/fabric/api/client/command/v2/ClientCommands.java) |
| Require user-entered command | `.requires(FabricClientCommandSource::attended)` | [Fabric command source](https://raw.githubusercontent.com/FabricMC/fabric/26.2/fabric-command-api-v2/src/client/java/net/fabricmc/fabric/api/client/command/v2/FabricClientCommandSource.java) |

The command helper is `ClientCommands`, not the older `ClientCommandManager`. The attended condition prevents server-provided clickable text from initiating a gather task without the command being entered by the user. Tick callbacks receive `net.minecraft.client.Minecraft`.

Baritone's `quantity` is the desired **total current inventory count** matching its block/drop filter. It is not a count of newly mined blocks. Zero disables the quantity stop. If a task promises N additional items, its executor needs an initial inventory baseline and must verify the requested item independently. Different blocks can drop a different item or require a particular tool. See [MineProcess](https://raw.githubusercontent.com/cabaletta/baritone/v1.19.0/src/main/java/baritone/process/MineProcess.java), especially `onTick`, `mineByName`, and `mine`.

`isActive() == false` is not evidence of success: mining also stops after a missing target or path failure. Verify inventory before emitting `SUCCESS`. A cancellation stops the controlling processes, but an unsafe-to-interrupt movement can finish before pathing stops. Upstream explicitly discourages using `forceCancel()`. See [pathing cancellation contract](https://raw.githubusercontent.com/cabaletta/baritone/v1.19.0/src/api/java/baritone/api/behavior/IPathingBehavior.java).

## Baritone shutdown behavior

Observed 2026-09-19 during client acceptance: Baritone 1.19.0 does not shut down its worker thread
pool when the client stops. Those threads are non-daemon, so the JVM cannot exit and Minecraft's
`ClientShutdownWatchdog` kills the process with status 248. This is invisible during normal desktop
play and fatal to any automated client run that expects a clean exit. `BUGS.md` records the thread
dump evidence and `scripts/run-client-gametest.sh` classifies it. Re-check this when the pinned
Baritone version changes.

## Local installation observations

Read-only inspection on 2026-09-19 found `/home/htfi/.minecraft/versions/26.2/26.2.jar` (39,193,383 bytes) and its version metadata. The metadata identifies the released `26.2`, requires Java 25, and refers to asset index `32`. The local asset index directory contained `1.21.json` and `26.json`, but no `32.json`; the presence of the client jar therefore does not establish that an offline 26.2 development launch has all assets.

Other version directories present were `1.21.11`, `26.1.2`, and `26.2-snapshot-4`. No PrismLauncher installation was found at the standard native or Flatpak locations checked. No account, authentication, or launcher credential files were opened. These observations are environment-specific and should be checked again before using local assets. No existing game installation was modified by this inspection.

## Future 1.21.x port

Keep the decision/control core independent of Minecraft types. A future explicitly selected 1.21.x version needs its own adapter and build configuration: versions before 26.1 used an obfuscated game and typically Java 21, and cannot be declared compatible simply by widening the current mod's version range. The [Fabric 26.1 announcement](https://fabricmc.net/2026/03/14/261.html) explains this boundary. Do not lower the active target to solve a dependency or environment problem.
