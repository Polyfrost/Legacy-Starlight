package ca.spottedleaf.starlight.mixin.common;

import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import ca.spottedleaf.starlight.common.light.StarLightEngine;
import ca.spottedleaf.starlight.common.light.StarLightInterface;
import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.WorldChunkSection;
import net.minecraft.block.state.BlockState;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldChunk.class)
public abstract class WorldChunkMixin implements ExtendedChunk {

    @Shadow
    @Final
    private WorldChunkSection[] sections;

    @Shadow
    @Final
    private int[] precipitationHeight;

    @Shadow
    @Final
    private World world;

    @Shadow
    @Final
    private int[] heightMap;

    @Shadow
    private boolean terrainPopulated;

    @Shadow
    private boolean lightPopulated;

    @Shadow
    private boolean dirty;

    @Shadow
    private int lowestHeight;

    @Shadow
    public abstract int getHighestSectionOffset();

    @Shadow
    private int getOpacityAt(final int localX, final int y, final int localZ) {
        throw new AssertionError();
    }

    @Unique
    private volatile SWMRNibbleArray[] blockNibbles;

    @Unique
    private volatile SWMRNibbleArray[] skyNibbles;

    @Inject(method = "<init>(Lnet/minecraft/world/World;II)V", at = @At("RETURN"))
    private void initNibbles(final CallbackInfo ci) {
        this.blockNibbles = StarLightEngine.getFilledEmptyLight();
        this.skyNibbles = StarLightEngine.getFilledEmptyLight();
    }

    @Unique
    private volatile boolean[] skyEmptinessMap;

    @Unique
    private volatile boolean[] blockEmptinessMap;

    @Unique
    private volatile boolean starlightLit;

    @Override
    public SWMRNibbleArray[] getBlockNibbles() {
        return this.blockNibbles;
    }

    @Override
    public void setBlockNibbles(final SWMRNibbleArray[] nibbles) {
        this.blockNibbles = nibbles;
    }

    @Override
    public SWMRNibbleArray[] getSkyNibbles() {
        return this.skyNibbles;
    }

    @Override
    public void setSkyNibbles(final SWMRNibbleArray[] nibbles) {
        this.skyNibbles = nibbles;
    }

    @Override
    public boolean[] getSkyEmptinessMap() {
        return this.skyEmptinessMap;
    }

    @Override
    public void setSkyEmptinessMap(final boolean[] emptinessMap) {
        this.skyEmptinessMap = emptinessMap;
    }

    @Override
    public boolean[] getBlockEmptinessMap() {
        return this.blockEmptinessMap;
    }

    @Override
    public void setBlockEmptinessMap(final boolean[] emptinessMap) {
        this.blockEmptinessMap = emptinessMap;
    }

    @Override
    public boolean isStarlightLit() {
        return this.starlightLit;
    }

    @Override
    public void setStarlightLit(final boolean lit) {
        this.starlightLit = lit;
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void lightOnLoad(final CallbackInfo ci) {
        if (this.world.isClient || (Object)this instanceof EmptyChunk) {
            return;
        }
        ((ExtendedWorld)this.world).getLightEngine().lightChunk((WorldChunk)(Object)this);
        this.lightPopulated = true;
    }

    @Unique
    private boolean createsSection;

    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void checkCreatesSection(final BlockPos pos, final BlockState state, final CallbackInfoReturnable<BlockState> cir) {
        final int y = pos.getY();
        this.createsSection = this.world.isClient && y >= 0 && y < 256 && this.sections[y >> 4] == null;
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void syncCreatedSection(final BlockPos pos, final BlockState state, final CallbackInfoReturnable<BlockState> cir) {
        if (this.createsSection) {
            this.createsSection = false;
            if (this.starlightLit) {
                StarLightInterface.syncSection((WorldChunk)(Object)this, pos.getY() >> 4);
            }
        }
    }

    /**
     * @reason Only the height map half of this is still needed, the sky light is handled by starlight
     * @author Spottedleaf
     */
    @Overwrite
    public void populateHeightMap() {
        final int top = this.getHighestSectionOffset() + 16;
        this.lowestHeight = Integer.MAX_VALUE;

        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                this.precipitationHeight[x + (z << 4)] = -999;

                for (int y = top; y > 0; --y) {
                    if (this.getOpacityAt(x, y - 1, z) != 0) {
                        this.heightMap[z << 4 | x] = y;
                        if (y < this.lowestHeight) {
                            this.lowestHeight = y;
                        }
                        break;
                    }
                }
            }
        }

        this.dirty = true;
    }

    /**
     * @reason Only the height map half of this is still needed, the sky light is handled by starlight
     * @author Spottedleaf
     */
    @Overwrite
    private void updateHeightMap(final int localX, final int y, final int localZ) {
        final int oldHeight = this.heightMap[localZ << 4 | localX] & 0xFF;
        int height = Math.max(oldHeight, y);

        while (height > 0 && this.getOpacityAt(localX, height - 1, localZ) == 0) {
            --height;
        }

        if (height != oldHeight) {
            this.heightMap[localZ << 4 | localX] = height;
            if (height < this.lowestHeight) {
                this.lowestHeight = height;
            }
            this.dirty = true;
        }
    }

    /**
     * @reason Vanilla sky light gap checks are replaced by starlight
     * @author Spottedleaf
     */
    @Overwrite
    private void recheckGap(final int localX, final int localZ) {}

    /**
     * @reason Read light from starlight
     * @author Spottedleaf
     */
    @Overwrite
    public int getLight(final LightType type, final BlockPos pos) {
        if (type == LightType.SKY) {
            return this.world.dimension.hasNoSky() ? 0 : StarLightInterface.getSkyLightValue((WorldChunk)(Object)this, pos.getX(), pos.getY(), pos.getZ());
        }
        return StarLightInterface.getBlockLightValue((WorldChunk)(Object)this, pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * @reason Read light from starlight
     * @author Spottedleaf
     */
    @Overwrite
    public int getLight(final BlockPos pos, final int ambientDarkness) {
        final int sky = this.world.dimension.hasNoSky() ? 0 : StarLightInterface.getSkyLightValue((WorldChunk)(Object)this, pos.getX(), pos.getY(), pos.getZ()) - ambientDarkness;
        // Don't fetch the block light level if the skylight level is 15, since the value will never be higher.
        if (sky == 15) {
            return 15;
        }
        return Math.max(sky, StarLightInterface.getBlockLightValue((WorldChunk)(Object)this, pos.getX(), pos.getY(), pos.getZ()));
    }

    /**
     * @reason Only starlight writes light. Vanilla would also create an empty section here
     * @author Spottedleaf
     */
    @Overwrite
    public void setLight(final LightType type, final BlockPos pos, final int light) {}

    /**
     * @reason Chunks are lit by starlight when they load
     * @author Spottedleaf
     */
    @Overwrite
    public void populateLight() {
        this.terrainPopulated = true;
        this.lightPopulated = true;
    }

    /**
     * @reason Chunk edges are checked by starlight when a chunk is lit
     * @author Spottedleaf
     */
    @Overwrite
    public void checkBorderLight() {}
}
