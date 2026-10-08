package ca.spottedleaf.starlight.mixin.common;

import ca.spottedleaf.starlight.common.light.StarLightInterface;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.storage.AnvilChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilChunkStorage.class)
public abstract class AnvilChunkStorageMixin {

    @Inject(method = "writeChunkToNbt", at = @At("HEAD"))
    private void syncLight(final WorldChunk chunk, final World world, final NbtCompound nbt, final CallbackInfo ci) {
        StarLightInterface.syncToVanilla(chunk);
    }
}
