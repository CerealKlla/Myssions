package com.github.cerealklla.myssions.bridge;

import com.github.cerealklla.lyfe.api.Lyfe;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;

/**
 * Real live bug found 2026-11-16 ("Fresh Catch," a gather-milestone quest for Cod): every caught
 * fish carries a per-catch weight/custom-name component, so it never stacks, and a player actively
 * fishing almost always keeps catches in Lyfe's Catch Bag rather than letting 10 individually-
 * unstackable fish eat up flat inventory slots -- {@code quest.QuestEngine}'s own gather/buy/plot-
 * upgrade item counting only ever scanned {@code player.getInventory()} directly, so it silently
 * never saw fish kept there at all. Optional soft dependency, same isolation convention as every
 * other bridge in this suite -- every call site elsewhere in Myssions must check {@link #isLoaded()}
 * itself before touching this class.
 */
public final class LyfeCatchBagBridge {

    private LyfeCatchBagBridge() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("lyfe");
    }

    public static int countInCatchBags(ServerPlayer player, Item item) {
        return Lyfe.countFishInCatchBags(player, item);
    }

    public static int removeFromCatchBags(ServerPlayer player, Item item, int amount) {
        return Lyfe.removeFishFromCatchBags(player, item, amount);
    }
}
