package com.github.cerealklla.myssions.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.github.cerealklla.settlemynts.api.Settlemynts;
import com.github.cerealklla.settlemynts.bridge.YconomicsShopBridge;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

/**
 * Resolves what a settlement's own real shops currently list for sale AND actually have in stock --
 * added 2026-10-11 for Myssions' "buy an item from a local shop" quest, which must only name an item
 * a vendor in the issuing plot's own settlement actually sells, and must not even be offered if
 * nothing qualifies. Optional soft dependency, same isolation convention as every other bridge in
 * this mod -- every call site must check {@link #isLoaded()} itself before touching this class.
 *
 * <p><b>Corrected 2026-10-12</b>, real user report: the quest could pick an item that was listed for
 * sale but the vendor had zero physical stock of -- {@link #resolvableSellableItems} now calls
 * Settlemynts' own stock-aware {@code api.Settlemynts#getAllSettlementListingsInStock} instead of
 * the stock-blind {@code #getAllSettlementListings}, so a quest never targets an item the player
 * couldn't actually buy right now.
 */
public final class SettlemyntsShopBridge {

    private SettlemyntsShopBridge() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("settlemynts");
    }

    /** One resolvable real-item listing -- {@code pricePerUnit} is what a vendor actually charges, used to size the "Useful Errand" quest's gold reward around the item's real cost (see {@code NpcQuests#aUsefulErrand}'s own doc). */
    public record ResolvedListing(Identifier itemId, int pricePerUnit) {
    }

    /**
     * Every real item (tag-based "any X" listings are skipped -- they don't name one concrete item
     * to hand over) listed for sale AND currently in stock anywhere in {@code plotId}'s own
     * settlement, paired with its listed price. Empty if Settlemynts isn't loaded, the
     * plot/settlement can't be resolved, or genuinely nothing qualifies.
     */
    public static List<ResolvedListing> resolvableSellableItems(ServerLevel level, UUID plotId) {
        List<ResolvedListing> result = new ArrayList<>();
        SettlemyntsPlotLocationBridge.resolve(level, plotId).ifPresent(loc -> {
            Settlemynts.findPlotAt(level, loc.pos()).ifPresent(handle -> {
                for (YconomicsShopBridge.ShopListingView listing : Settlemynts.getAllSettlementListingsInStock(level, handle.settlementCoreId())) {
                    listing.resource().itemId().ifPresent(itemId -> result.add(new ResolvedListing(itemId, listing.pricePerUnit())));
                }
            });
        });
        return result;
    }
}
