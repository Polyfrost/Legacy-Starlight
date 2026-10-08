package ca.spottedleaf.starlight.mixin.common;

import ca.spottedleaf.starlight.common.light.StarLightInterface;
import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(World.class)
public abstract class WorldMixin implements ExtendedWorld {

    @Shadow
    protected ChunkSource chunkSource;

    @Unique
    private volatile StarLightInterface lightEngine;

    @Override
    public StarLightInterface getLightEngine() {
        if (this.lightEngine == null) {
            this.lightEngine = new StarLightInterface((World)(Object)this);
        }
        return this.lightEngine;
    }

    @Override
    public WorldChunk getAnyChunkImmediately(final int chunkX, final int chunkZ) {
        final StarLightInterface lightEngine = this.lightEngine;
        if (lightEngine != null && lightEngine.isAsync()) {
            return lightEngine.getTrackedChunk(chunkX, chunkZ);
        }
        final ChunkSource source = this.chunkSource;
        if (source == null || !source.hasChunk(chunkX, chunkZ)) {
            return null;
        }
        final WorldChunk chunk = source.getChunk(chunkX, chunkZ);
        return chunk instanceof EmptyChunk ? null : chunk;
    }

    @Override
    public void onLightUpdate(final int sectionX, final int sectionY, final int sectionZ) {
        this.getLightEngine().onLightUpdate(sectionX, sectionY, sectionZ);
    }

    /**
     * @reason Queue the change for starlight. Changes are propagated at the end of the tick
     * @author Spottedleaf
     */
    @Overwrite
    public boolean checkLight(final BlockPos pos) {
        this.getLightEngine().blockChange(pos);
        return true;
    }

    /**
     * @reason The vanilla light engine is replaced by starlight
     * @author Spottedleaf
     */
    @Overwrite
    public boolean updateLight(final LightType type, final BlockPos pos) {
        return true;
    }

    @Redirect(
            method = "purgeTickingChunks",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;checkLight(Lnet/minecraft/util/math/BlockPos;)Z")
    )
    private boolean skipRandomLightCheck(final World world, final BlockPos pos) {
        return false;
    }
}
