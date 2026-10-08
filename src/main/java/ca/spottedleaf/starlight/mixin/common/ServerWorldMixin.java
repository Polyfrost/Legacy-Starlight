package ca.spottedleaf.starlight.mixin.common;

import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void propagateLightChanges(final CallbackInfo ci) {
        ((ExtendedWorld)this).getLightEngine().propagateChanges();
    }
}
