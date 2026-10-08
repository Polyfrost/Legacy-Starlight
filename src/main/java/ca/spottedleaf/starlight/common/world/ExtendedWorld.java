package ca.spottedleaf.starlight.common.world;

import ca.spottedleaf.starlight.common.light.StarLightInterface;
import net.minecraft.world.chunk.WorldChunk;

public interface ExtendedWorld {

    public WorldChunk getAnyChunkImmediately(final int chunkX, final int chunkZ);

    public void onLightUpdate(final int sectionX, final int sectionY, final int sectionZ);

    public StarLightInterface getLightEngine();
}
