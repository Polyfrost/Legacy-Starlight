package ca.spottedleaf.starlight.mixin.client;

import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldChunk.class)
public abstract class WorldChunkClientMixin {

    @Shadow
    @Final
    private World world;

    @Inject(method = "update", at = @At("TAIL"))
    private void lightOnChunkData(final byte[] data, final int sections, final boolean full, final CallbackInfo ci) {
        ((ExtendedWorld)this.world).getLightEngine().lightChunk((WorldChunk)(Object)this);
    }
}
