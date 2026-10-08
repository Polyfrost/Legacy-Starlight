package ca.spottedleaf.starlight.common.light;

import net.minecraft.world.World;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

final class CeleritasGate implements StarLightInterface.LightGate {

    private static final int FLAG_HAS_LIGHT_DATA = 2;

    private final Class<?> holder;
    private final MethodHandle getTracker;
    private final MethodHandle statusAdded;
    private final MethodHandle statusRemoved;

    private CeleritasGate() throws ReflectiveOperationException {
        final ClassLoader loader = CeleritasGate.class.getClassLoader();
        this.holder = Class.forName("org.embeddedt.embeddium.impl.render.chunk.map.ChunkTrackerHolder", false, loader);
        final Class<?> tracker = Class.forName("org.embeddedt.embeddium.impl.render.chunk.map.ChunkTracker", false, loader);
        final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        final MethodType status = MethodType.methodType(void.class, int.class, int.class, int.class);
        this.getTracker = lookup.findVirtual(this.holder, "sodium$getTracker", MethodType.methodType(tracker));
        this.statusAdded = lookup.findVirtual(tracker, "onChunkStatusAdded", status);
        this.statusRemoved = lookup.findVirtual(tracker, "onChunkStatusRemoved", status);
    }

    static StarLightInterface.LightGate create() {
        try {
            return new CeleritasGate();
        } catch (final ReflectiveOperationException | LinkageError ex) {
            return null;
        }
    }

    private void setLight(final MethodHandle method, final World world, final int chunkX, final int chunkZ) {
        if (!this.holder.isInstance(world)) {
            return;
        }
        try {
            method.invoke(this.getTracker.invoke(world), chunkX, chunkZ, FLAG_HAS_LIGHT_DATA);
        } catch (final Throwable thr) {
            throw new RuntimeException(thr);
        }
    }

    @Override
    public void lightPending(final World world, final int chunkX, final int chunkZ) {
        this.setLight(this.statusRemoved, world, chunkX, chunkZ);
    }

    @Override
    public void lightReady(final World world, final int chunkX, final int chunkZ) {
        this.setLight(this.statusAdded, world, chunkX, chunkZ);
    }
}
