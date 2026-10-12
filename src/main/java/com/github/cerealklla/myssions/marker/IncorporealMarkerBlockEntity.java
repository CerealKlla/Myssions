package com.github.cerealklla.myssions.marker;

import java.util.Optional;

import com.github.cerealklla.myssions.registration.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Holds the one piece of data a placed marker carries -- an opaque {@code tag} string, entirely
 * uninterpreted by this mod, that the placer (whatever mod/code called {@code api.Myssions
 * #placeGroundMarker}/{@code #placeWallMarker}) can use to identify which real-world thing this
 * particular marker is for (a quest id, a loot table id, whatever) when {@code marker.MarkerEvents}
 * fires its trigger. See {@code marker.GroundMarkerBlock}'s own doc for the full mechanic.
 */
public final class IncorporealMarkerBlockEntity extends BlockEntity {

    private Optional<String> tag = Optional.empty();

    public IncorporealMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INCORPOREAL_MARKER_TYPE.get(), pos, state);
    }

    public Optional<String> tag() {
        return tag;
    }

    public void setTag(String newTag) {
        this.tag = Optional.ofNullable(newTag);
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("Tag", com.mojang.serialization.Codec.STRING, tag.orElse(null));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tag = input.read("Tag", com.mojang.serialization.Codec.STRING);
    }
}
