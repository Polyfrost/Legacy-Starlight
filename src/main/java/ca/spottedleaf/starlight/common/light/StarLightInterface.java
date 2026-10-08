package ca.spottedleaf.starlight.common.light;

import ca.spottedleaf.starlight.common.chunk.ExtendedChunk;
import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkNibbleStorage;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.WorldChunkSection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StarLightInterface {

    private static final Logger LOGGER = LogManager.getLogger("Starlight");

    public interface LightGate {
        void lightPending(World world, int chunkX, int chunkZ);

        void lightReady(World world, int chunkX, int chunkZ);
    }

    public static LightGate gate = CeleritasGate.create();
    public static boolean asyncClient = gate != null;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor((final Runnable run) -> {
        final Thread thread = new Thread(run, "Starlight Light Worker");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    private final World world;
    private final boolean async;
    private final SkyStarLightEngine skyEngine;
    private final BlockStarLightEngine blockEngine;

    private final Map<Long, Set<BlockPos>> changedBlocks = new LinkedHashMap<>();

    private final Map<Long, WorldChunk> trackedChunks = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<WorldChunk> litChunks = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<int[]> updatedSections = new ConcurrentLinkedQueue<>();

    public StarLightInterface(final World world) {
        this.world = world;
        this.async = world.isClient && asyncClient;
        this.skyEngine = world.dimension.hasNoSky() ? null : new SkyStarLightEngine(world);
        this.blockEngine = new BlockStarLightEngine(world);
    }

    private static long key(final int chunkX, final int chunkZ) {
        return ((long)chunkZ << 32) | (chunkX & 0xFFFFFFFFL);
    }

    public boolean isAsync() {
        return this.async;
    }

    public WorldChunk getTrackedChunk(final int chunkX, final int chunkZ) {
        return this.trackedChunks.get(key(chunkX, chunkZ));
    }

    public void chunkUnloaded(final int chunkX, final int chunkZ) {
        this.trackedChunks.remove(key(chunkX, chunkZ));
    }

    public static int getSkyLightValue(final WorldChunk chunk, final int x, int y, final int z) {
        int sectionY = y >> 4;
        if (sectionY > 16) {
            return 15;
        }
        if (sectionY < -1) {
            sectionY = -1;
            y = -16;
        }

        final SWMRNibbleArray[] nibbles = ((ExtendedChunk)chunk).getSkyNibbles();
        final SWMRNibbleArray immediate = nibbles[sectionY + 1];

        if (!immediate.isNullNibbleVisible()) {
            return immediate.getVisible(x, y, z);
        }

        final boolean[] emptinessMap = ((ExtendedChunk)chunk).getSkyEmptinessMap();

        if (emptinessMap == null) {
            return 15;
        }

        int highestNonEmpty = -2;
        for (int currY = 15; currY >= 0; --currY) {
            if (!emptinessMap[currY]) {
                highestNonEmpty = currY;
                break;
            }
        }

        if (sectionY > highestNonEmpty) {
            return 15;
        }

        // this nibble is going to depend solely on the skylight data above it
        for (int currY = sectionY + 1; currY <= 16; ++currY) {
            final SWMRNibbleArray nibble = nibbles[currY + 1];
            if (!nibble.isNullNibbleVisible()) {
                return nibble.getVisible(x, 0, z);
            }
        }

        return 15;
    }

    public static int getBlockLightValue(final WorldChunk chunk, final int x, final int y, final int z) {
        final int sectionY = y >> 4;
        if (sectionY < -1 || sectionY > 16) {
            return 0;
        }
        return ((ExtendedChunk)chunk).getBlockNibbles()[sectionY + 1].getVisible(x, y, z);
    }

    public static void syncToVanilla(final WorldChunk chunk) {
        if (!((ExtendedChunk)chunk).isStarlightLit()) {
            return;
        }

        for (int sectionY = 0; sectionY < 16; ++sectionY) {
            syncSection(chunk, sectionY);
        }
    }

    public static void syncSection(final WorldChunk chunk, final int sectionY) {
        final WorldChunkSection section = chunk.getSections()[sectionY];
        if (section == null) {
            return;
        }

        ((ExtendedChunk)chunk).getBlockNibbles()[sectionY + 1].copyVisibleTo(section.getBlockLightStorage().getData());

        final ChunkNibbleStorage sky = section.getSkyLightStorage();
        if (sky == null) {
            return;
        }

        final SWMRNibbleArray skyNibble = ((ExtendedChunk)chunk).getSkyNibbles()[sectionY + 1];
        if (!skyNibble.isNullNibbleVisible()) {
            skyNibble.copyVisibleTo(sky.getData());
            return;
        }

        for (int z = 0; z < 16; ++z) {
            for (int x = 0; x < 16; ++x) {
                final int level = getSkyLightValue(chunk, x, sectionY << 4, z);
                for (int y = 0; y < 16; ++y) {
                    sky.set(x, y, z, level);
                }
            }
        }
    }

    public static void syncChangedSection(final WorldChunk chunk, final int sectionY) {
        syncSection(chunk, sectionY);
        final SWMRNibbleArray[] skyNibbles = ((ExtendedChunk)chunk).getSkyNibbles();
        for (int below = sectionY - 1; below >= 0 && skyNibbles[below + 1].isNullNibbleVisible(); --below) {
            syncSection(chunk, below);
        }
    }

    public void blockChange(final BlockPos pos) {
        if (pos.getY() < 0 || pos.getY() > 255) {
            return;
        }
        this.changedBlocks.computeIfAbsent(key(pos.getX() >> 4, pos.getZ() >> 4), (final Long k) -> new HashSet<>())
                .add(new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
    }

    public void lightChunk(final WorldChunk chunk) {
        if (!this.async) {
            this.lightChunkNow(chunk);
            if (this.world.isClient) {
                syncToVanilla(chunk);
            }
            return;
        }

        final long key = key(chunk.chunkX, chunk.chunkZ);
        this.trackedChunks.put(key, chunk);
        if (gate != null && !((ExtendedChunk)chunk).isStarlightLit()) {
            gate.lightPending(this.world, chunk.chunkX, chunk.chunkZ);
        }

        WORKER.execute(() -> {
            if (this.trackedChunks.get(key) != chunk) {
                return;
            }
            try {
                this.lightChunkNow(chunk);
            } catch (final Throwable thr) {
                LOGGER.error("Failed to light chunk " + chunk.chunkX + ", " + chunk.chunkZ, thr);
            }
            this.litChunks.add(chunk);
        });
    }

    private void lightChunkNow(final WorldChunk chunk) {
        final Boolean[] emptySections = StarLightEngine.getEmptySectionsForChunk(chunk);
        if (this.skyEngine != null) {
            this.skyEngine.light(this.world, chunk, emptySections.clone());
        }
        this.blockEngine.light(this.world, chunk, emptySections);
        ((ExtendedChunk)chunk).setStarlightLit(true);
    }

    public void onLightUpdate(final int sectionX, final int sectionY, final int sectionZ) {
        if (!this.world.isClient || sectionY < 0 || sectionY > 15) {
            return;
        }
        if (this.async) {
            this.updatedSections.add(new int[] { sectionX, sectionY, sectionZ });
        } else {
            this.applyLightUpdate(sectionX, sectionY, sectionZ);
        }
    }

    private void applyLightUpdate(final int sectionX, final int sectionY, final int sectionZ) {
        final WorldChunk chunk = ((ExtendedWorld)this.world).getAnyChunkImmediately(sectionX, sectionZ);
        if (chunk != null) {
            syncChangedSection(chunk, sectionY);
        }
        final int x = sectionX << 4;
        final int y = sectionY << 4;
        final int z = sectionZ << 4;
        this.world.notifyRegionChanged(x + 1, y + 1, z + 1, x + 14, y + 14, z + 14);
    }

    private static Boolean[] getEmptinessChanges(final WorldChunk chunk) {
        final boolean[] known = ((ExtendedChunk)chunk).getBlockEmptinessMap();
        final Boolean[] current = StarLightEngine.getEmptySectionsForChunk(chunk);
        if (known == null) {
            return current;
        }

        boolean changed = false;
        for (int i = 0; i < current.length; ++i) {
            if (known[i] == current[i].booleanValue()) {
                current[i] = null;
            } else {
                changed = true;
            }
        }

        return changed ? current : null;
    }

    public void propagateChanges() {
        if (!this.changedBlocks.isEmpty()) {
            final List<Map.Entry<Long, Set<BlockPos>>> tasks = new ArrayList<>(this.changedBlocks.entrySet());
            this.changedBlocks.clear();

            if (this.async) {
                WORKER.execute(() -> {
                    try {
                        this.propagateChangesNow(tasks);
                    } catch (final Throwable thr) {
                        LOGGER.error("Failed to propagate light changes", thr);
                    }
                });
            } else {
                this.propagateChangesNow(tasks);
            }
        }

        if (this.async) {
            this.applyWorkerResults();
        }
    }

    private void propagateChangesNow(final List<Map.Entry<Long, Set<BlockPos>>> tasks) {
        for (final Map.Entry<Long, Set<BlockPos>> task : tasks) {
            final int chunkX = (int)task.getKey().longValue();
            final int chunkZ = (int)(task.getKey().longValue() >>> 32);

            final WorldChunk chunk = ((ExtendedWorld)this.world).getAnyChunkImmediately(chunkX, chunkZ);
            if (chunk == null || !((ExtendedChunk)chunk).isStarlightLit()) {
                continue;
            }

            final Boolean[] emptinessChanges = getEmptinessChanges(chunk);

            if (this.skyEngine != null) {
                this.skyEngine.blocksChangedInChunk(this.world, chunkX, chunkZ, task.getValue(), emptinessChanges == null ? null : emptinessChanges.clone());
            }
            this.blockEngine.blocksChangedInChunk(this.world, chunkX, chunkZ, task.getValue(), emptinessChanges);
        }
    }

    private void applyWorkerResults() {
        WorldChunk chunk;
        while ((chunk = this.litChunks.poll()) != null) {
            if (this.trackedChunks.get(key(chunk.chunkX, chunk.chunkZ)) != chunk) {
                continue;
            }
            syncToVanilla(chunk);
            if (gate != null) {
                gate.lightReady(this.world, chunk.chunkX, chunk.chunkZ);
            }
        }

        final Set<Long> seen = new HashSet<>();
        final List<int[]> sections = new ArrayList<>();
        int[] section;
        while ((section = this.updatedSections.poll()) != null) {
            if (seen.add(((long)section[1] << 56) | ((section[0] & 0xFFFFFFFL) << 28) | (section[2] & 0xFFFFFFFL))) {
                sections.add(section);
            }
        }
        for (final int[] updated : sections) {
            this.applyLightUpdate(updated[0], updated[1], updated[2]);
        }
    }

    public void awaitWorker() {
        if (!this.async) {
            return;
        }
        try {
            WORKER.submit(() -> {}).get();
        } catch (final InterruptedException | ExecutionException ex) {
            throw new RuntimeException(ex);
        }
        this.applyWorkerResults();
    }
}
