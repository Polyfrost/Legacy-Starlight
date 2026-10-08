package ca.spottedleaf.starlight.mixin.client;

import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import net.minecraft.client.world.chunk.ClientChunkCache;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheMixin {

    @Shadow
    private World world;

    @Inject(method = "unloadChunk", at = @At("HEAD"))
    private void stopLighting(final int chunkX, final int chunkZ, final CallbackInfo ci) {
        ((ExtendedWorld)this.world).getLightEngine().chunkUnloaded(chunkX, chunkZ);
    }
}
