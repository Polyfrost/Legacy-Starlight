package ca.spottedleaf.starlight.mixin.client;

import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void propagateLightChanges(final CallbackInfo ci) {
        ((ExtendedWorld)this).getLightEngine().propagateChanges();
    }
}
