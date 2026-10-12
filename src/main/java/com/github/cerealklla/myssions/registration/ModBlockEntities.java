package com.github.cerealklla.myssions.registration;

import com.github.cerealklla.myssions.MyssionsMod;
import com.github.cerealklla.myssions.marker.IncorporealMarkerBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    private ModBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MyssionsMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IncorporealMarkerBlockEntity>> INCORPOREAL_MARKER_TYPE =
            BLOCK_ENTITIES.register("incorporeal_marker",
                    () -> new BlockEntityType<>(IncorporealMarkerBlockEntity::new, ModBlocks.GROUND_MARKER.get(), ModBlocks.WALL_MARKER.get()));
}
