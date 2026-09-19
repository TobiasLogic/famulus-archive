package dev.famulus.fabric;

import dev.famulus.core.WorldSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Count the 36 main/hotbar slots, matching Baritone's quantity check. */
public final class MinecraftObserver {
    private ClientLevel previousLevel;
    private long worldGeneration;

    public WorldSnapshot observe(Minecraft client, String itemId) {
        if (client.level != previousLevel) {
            previousLevel = client.level;
            worldGeneration++;
        }
        if (client.level == null || client.player == null) {
            return new WorldSnapshot(false, false, "disconnected", 0, false);
        }
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
        int count = 0;
        boolean space = false;
        for (ItemStack stack : client.player.getInventory().getNonEquipmentItems()) {
            if (stack.isEmpty()) space = true;
            else if (stack.is(item)) {
                count += stack.getCount();
                if (stack.getCount() < stack.getMaxStackSize()) space = true;
            }
        }
        String worldKey = worldGeneration + ":" + client.level.dimension().identifier();
        return new WorldSnapshot(true, client.player.isAlive() && !client.player.isRemoved(),
                worldKey, count, space);
    }
}
