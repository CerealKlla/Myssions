package com.github.cerealklla.myssions.registration;

import com.github.cerealklla.myssions.MyssionsMod;
import com.github.cerealklla.myssions.marker.GroundMarkerBlock;
import com.github.cerealklla.myssions.marker.WallMarkerBlock;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * This mod's first real (non-ghost) blocks -- the "X marks the spot" incorporeal markers (design
 * request 2026-10-12, see {@code marker.GroundMarkerBlock}'s own doc). Deliberately no {@code
 * BlockItem}/default item for either -- never player-placed or obtainable; the only way one ever
 * appears in the world is {@code api.Myssions#placeGroundMarker}/{@code #placeWallMarker}.
 */
public final class ModBlocks {

    private ModBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MyssionsMod.MODID);

    public static final DeferredBlock<GroundMarkerBlock> GROUND_MARKER = BLOCKS.register(
            "ground_marker",
            id -> new GroundMarkerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .instabreak()
                    .sound(SoundType.WOOL)
                    .noLootTable()
                    .setId(ResourceKey.create(Registries.BLOCK, id))));

    public static final DeferredBlock<WallMarkerBlock> WALL_MARKER = BLOCKS.register(
            "wall_marker",
            id -> new WallMarkerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .instabreak()
                    .sound(SoundType.WOOL)
                    .noLootTable()
                    .setId(ResourceKey.create(Registries.BLOCK, id))));
}
