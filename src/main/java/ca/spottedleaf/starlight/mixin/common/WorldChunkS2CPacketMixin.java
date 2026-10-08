package ca.spottedleaf.starlight.mixin.common;

import ca.spottedleaf.starlight.common.light.StarLightInterface;
import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import net.minecraft.network.packet.s2c.play.WorldChunkS2CPacket;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldChunkS2CPacket.class)
public abstract class WorldChunkS2CPacketMixin {

    @Inject(method = "writeChunkData", at = @At("HEAD"))
    private static void syncLight(final WorldChunk chunk, final boolean full, final boolean light, final int sections,
                                  final CallbackInfoReturnable<WorldChunkS2CPacket.ChunkData> cir) {
        ((ExtendedWorld)chunk.getWorld()).getLightEngine().propagateChanges();
        StarLightInterface.syncToVanilla(chunk);
    }
}
