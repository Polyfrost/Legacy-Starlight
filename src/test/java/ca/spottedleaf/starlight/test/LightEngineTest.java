package ca.spottedleaf.starlight.test;

import ca.spottedleaf.starlight.common.light.StarLightInterface;
import ca.spottedleaf.starlight.common.world.ExtendedWorld;
import net.minecraft.Bootstrap;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.mob.MobCategory;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.WorldData;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.WorldChunkSection;
import net.minecraft.world.dimension.OverworldDimension;
import net.minecraft.world.gen.WorldGeneratorType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.MixinEnvironment;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class LightEngineTest {

    private static final int MIN_BLOCK = -16;
    private static final int SIZE = 48;
    private static final int MIN_Y = -16;
    private static final int HEIGHT = 288;

    @BeforeAll
    public static void setup() {
        Bootstrap.init();
    }

    @Test
    public void mixinsApply() {
        MixinEnvironment.getCurrentEnvironment().audit();
    }

    @Test
    public void matchesReference() {
        StarLightInterface.asyncClient = false;
        this.matchesReference(new TestWorld());
    }

    @Test
    public void matchesReferenceOffThread() {
        final List<String> gateEvents = Collections.synchronizedList(new ArrayList<>());
        StarLightInterface.asyncClient = true;
        StarLightInterface.gate = new StarLightInterface.LightGate() {
            @Override
            public void lightPending(final World world, final int chunkX, final int chunkZ) {
                gateEvents.add("pending " + chunkX + "," + chunkZ);
            }

            @Override
            public void lightReady(final World world, final int chunkX, final int chunkZ) {
                Assertions.assertTrue(gateEvents.contains("pending " + chunkX + "," + chunkZ), "ready without pending");
                gateEvents.add("ready " + chunkX + "," + chunkZ);
            }
        };
        try {
            final TestWorld world = new TestWorld();
            Assertions.assertTrue(((ExtendedWorld)(Object)world).getLightEngine().isAsync());
            this.matchesReference(world);
        } finally {
            StarLightInterface.asyncClient = false;
            StarLightInterface.gate = null;
        }
        Assertions.assertEquals(9, gateEvents.stream().filter(event -> event.startsWith("pending")).count(), gateEvents.toString());
        Assertions.assertEquals(10, gateEvents.stream().filter(event -> event.startsWith("ready")).count(), gateEvents.toString());
    }

    private void matchesReference(final TestWorld world) {
        final Random random = new Random(42L);
        final StarLightInterface lightEngine = ((ExtendedWorld)(Object)world).getLightEngine();

        final BlockState[] palette = new BlockState[] {
                Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), Blocks.STONE.defaultState(),
                Blocks.GLASS.defaultState(), Blocks.LEAVES.defaultState(), Blocks.WATER.defaultState(), Blocks.ICE.defaultState(),
                Blocks.GLOWSTONE.defaultState(), Blocks.TORCH.defaultState(), Blocks.LIT_REDSTONE_ORE.defaultState()
        };

        final List<WorldChunk> chunks = new ArrayList<>();
        for (int chunkX = -1; chunkX <= 1; ++chunkX) {
            for (int chunkZ = -1; chunkZ <= 1; ++chunkZ) {
                final WorldChunk chunk = new WorldChunk(world, chunkX, chunkZ);
                chunks.add(chunk);
                final int baseX = chunkX << 4;
                final int baseZ = chunkZ << 4;

                for (int x = 0; x < 16; ++x) {
                    for (int z = 0; z < 16; ++z) {
                        for (int y = 48 + random.nextInt(24); y >= 0; --y) {
                            chunk.setBlockState(new BlockPos(baseX + x, y, baseZ + z), Blocks.STONE.defaultState());
                        }
                    }
                }
                for (int i = 0; i < 12; ++i) {
                    final BlockState state = palette[random.nextInt(palette.length)];
                    final int x0 = random.nextInt(16), z0 = random.nextInt(16), y0 = random.nextInt(130);
                    final int x1 = Math.min(15, x0 + random.nextInt(8)), z1 = Math.min(15, z0 + random.nextInt(8)), y1 = y0 + random.nextInt(4);
                    for (int x = x0; x <= x1; ++x) {
                        for (int z = z0; z <= z1; ++z) {
                            for (int y = y0; y <= y1; ++y) {
                                chunk.setBlockState(new BlockPos(baseX + x, y, baseZ + z), state);
                            }
                        }
                    }
                }
                for (int i = 0; i < 20; ++i) {
                    chunk.setBlockState(new BlockPos(baseX + random.nextInt(16), random.nextInt(130), baseZ + random.nextInt(16)), palette[8 + random.nextInt(3)]);
                }
            }
        }

        Assertions.assertEquals(15, chunks.get(0).getLight(LightType.SKY, new BlockPos(0, 200, 0)));
        Assertions.assertEquals(0, chunks.get(0).getLight(LightType.BLOCK, new BlockPos(0, 200, 0)));

        Collections.shuffle(chunks, random);
        for (final WorldChunk chunk : chunks) {
            world.chunks.put(key(chunk.chunkX, chunk.chunkZ), chunk);
            lightEngine.lightChunk(chunk);
        }
        lightEngine.awaitWorker();
        verify(world, "initial light");

        for (int i = 1; i <= 600; ++i) {
            final int y = random.nextInt(5) == 0 ? random.nextInt(256) : random.nextInt(140);
            world.setBlockState(new BlockPos(MIN_BLOCK + random.nextInt(SIZE), y, MIN_BLOCK + random.nextInt(SIZE)), palette[random.nextInt(palette.length)], 2);
            if (random.nextInt(4) == 0) {
                lightEngine.propagateChanges();
            }
            if (i % 200 == 0) {
                lightEngine.propagateChanges();
                lightEngine.awaitWorker();
                verify(world, "after " + i + " block changes");
            }
        }

        final WorldChunk center = world.chunks.get(key(0, 0));
        for (int i = 0; i < 200; ++i) {
            center.setBlockState(new BlockPos(random.nextInt(16), random.nextInt(140), random.nextInt(16)), palette[random.nextInt(palette.length)]);
        }
        lightEngine.lightChunk(center);
        lightEngine.awaitWorker();
        verify(world, "relit chunk");
    }

    private static long key(final int chunkX, final int chunkZ) {
        return ((long)chunkZ << 32) | (chunkX & 0xFFFFFFFFL);
    }

    private static int index(final int x, final int y, final int z) {
        return (x - MIN_BLOCK) + SIZE * ((z - MIN_BLOCK) + SIZE * (y - MIN_Y));
    }

    private static void verify(final TestWorld world, final String stage) {
        final int[] opacity = new int[SIZE * SIZE * HEIGHT];
        final int[] emission = new int[SIZE * SIZE * HEIGHT];
        for (int x = MIN_BLOCK; x < MIN_BLOCK + SIZE; ++x) {
            for (int z = MIN_BLOCK; z < MIN_BLOCK + SIZE; ++z) {
                int height = 0;
                for (int y = 0; y < 256; ++y) {
                    final BlockState state = world.getBlockState(new BlockPos(x, y, z));
                    opacity[index(x, y, z)] = state.getBlock().getOpacity();
                    emission[index(x, y, z)] = state.getBlock().getLight();
                    if (state.getBlock().getOpacity() != 0) {
                        height = y + 1;
                    }
                }
                Assertions.assertEquals(height, world.getChunkAt(x >> 4, z >> 4).getHeight(x & 15, z & 15), stage + ": height map at " + x + "," + z);
            }
        }

        final int[] sky = new int[opacity.length];
        final int[] block = new int[opacity.length];
        for (int x = MIN_BLOCK; x < MIN_BLOCK + SIZE; ++x) {
            for (int z = MIN_BLOCK; z < MIN_BLOCK + SIZE; ++z) {
                for (int y = MIN_Y + HEIGHT - 1; y >= MIN_Y && opacity[index(x, y, z)] == 0; --y) {
                    sky[index(x, y, z)] = 15;
                }
            }
        }
        System.arraycopy(emission, 0, block, 0, block.length);
        flood(sky, opacity);
        flood(block, opacity);

        final List<String> errors = new ArrayList<>();
        int dimSky = 0;
        int litBlock = 0;
        for (int x = MIN_BLOCK; x < MIN_BLOCK + SIZE; ++x) {
            for (int z = MIN_BLOCK; z < MIN_BLOCK + SIZE; ++z) {
                final WorldChunk chunk = world.getChunkAt(x >> 4, z >> 4);
                for (int y = 0; y < 256; ++y) {
                    final BlockPos pos = new BlockPos(x, y, z);
                    final int gotSky = chunk.getLight(LightType.SKY, pos);
                    final int gotBlock = chunk.getLight(LightType.BLOCK, pos);
                    final WorldChunkSection section = chunk.getSections()[y >> 4];
                    if (section != null && (section.getSkyLight(x & 15, y & 15, z & 15) != gotSky || section.getBlockLight(x & 15, y & 15, z & 15) != gotBlock) && errors.size() < 20) {
                        errors.add(pos + " vanilla storage sky " + section.getSkyLight(x & 15, y & 15, z & 15) + " (engine " + gotSky + ") block " + section.getBlockLight(x & 15, y & 15, z & 15) + " (engine " + gotBlock + ")");
                    }
                    if (gotSky > 0 && gotSky < 15) {
                        ++dimSky;
                    }
                    if (gotBlock > 0) {
                        ++litBlock;
                    }
                    if ((gotSky != sky[index(x, y, z)] || gotBlock != block[index(x, y, z)]) && errors.size() < 20) {
                        errors.add(pos + " sky " + gotSky + " (expected " + sky[index(x, y, z)] + ") block " + gotBlock + " (expected " + block[index(x, y, z)] + ")");
                    }
                }
            }
        }
        Assertions.assertTrue(errors.isEmpty(), stage + ": " + errors);
        Assertions.assertTrue(dimSky > 10000 && litBlock > 10000, stage + ": " + dimSky + " dim sky blocks, " + litBlock + " lit blocks");
    }

    private static void flood(final int[] levels, final int[] opacity) {
        final List<ArrayDeque<Integer>> queues = new ArrayList<>();
        for (int level = 0; level <= 15; ++level) {
            queues.add(new ArrayDeque<>());
        }
        for (int i = 0; i < levels.length; ++i) {
            if (levels[i] > 1) {
                queues.get(levels[i]).add(i);
            }
        }
        final int[] offsets = new int[] { 1, -1, SIZE, -SIZE, SIZE * SIZE, -SIZE * SIZE };
        for (int level = 15; level > 1; --level) {
            Integer boxed;
            while ((boxed = queues.get(level).poll()) != null) {
                final int i = boxed;
                if (levels[i] != level) {
                    continue;
                }
                final int x = i % SIZE, z = (i / SIZE) % SIZE, y = i / (SIZE * SIZE);
                for (int direction = 0; direction < 6; ++direction) {
                    if ((direction == 0 && x == SIZE - 1) || (direction == 1 && x == 0) || (direction == 2 && z == SIZE - 1)
                            || (direction == 3 && z == 0) || (direction == 4 && y == HEIGHT - 1) || (direction == 5 && y == 0)) {
                        continue;
                    }
                    final int neighbour = i + offsets[direction];
                    final int target = level - Math.max(1, opacity[neighbour]);
                    if (target > levels[neighbour]) {
                        levels[neighbour] = target;
                        queues.get(target).add(neighbour);
                    }
                }
            }
        }
    }

    private static final class TestWorld extends World {

        final Map<Long, WorldChunk> chunks = new HashMap<>();
        final WorldChunk empty;

        TestWorld() {
            super(null, new WorldData(new WorldSettings(0L, WorldSettings.GameMode.SURVIVAL, false, false, WorldGeneratorType.DEFAULT), "test"),
                    new OverworldDimension(), new Profiler(), true);
            this.empty = new EmptyChunk(this, 0, 0);
            this.chunkSource = this.createChunkCache();
        }

        @Override
        protected ChunkSource createChunkCache() {
            return new ChunkSource() {
                @Override
                public boolean hasChunk(final int chunkX, final int chunkZ) {
                    return TestWorld.this.chunks.containsKey(key(chunkX, chunkZ));
                }

                @Override
                public WorldChunk getChunk(final int chunkX, final int chunkZ) {
                    final WorldChunk chunk = TestWorld.this.chunks.get(key(chunkX, chunkZ));
                    return chunk == null ? TestWorld.this.empty : chunk;
                }

                @Override
                public WorldChunk getChunk(final BlockPos pos) {
                    return this.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
                }

                @Override
                public void populateChunk(final ChunkSource source, final int chunkX, final int chunkZ) {}

                @Override
                public boolean populateChunkAfterTerrain(final ChunkSource source, final WorldChunk chunk, final int chunkX, final int chunkZ) {
                    return false;
                }

                @Override
                public boolean save(final boolean saveEntities, final ProgressListener listener) {
                    return true;
                }

                @Override
                public boolean tick() {
                    return false;
                }

                @Override
                public boolean shouldSave() {
                    return false;
                }

                @Override
                public String getDebugInfo() {
                    return "test";
                }

                @Override
                public List<Biome.SpawnEntry> getSpawnEntries(final MobCategory category, final BlockPos pos) {
                    return null;
                }

                @Override
                public BlockPos findNearestStructure(final World world, final String type, final BlockPos pos) {
                    return null;
                }

                @Override
                public int size() {
                    return TestWorld.this.chunks.size();
                }

                @Override
                public void placeStructures(final WorldChunk chunk, final int chunkX, final int chunkZ) {}

                @Override
                public void flush() {}
            };
        }

        @Override
        protected int getChunkViewDistance() {
            return 0;
        }
    }
}
