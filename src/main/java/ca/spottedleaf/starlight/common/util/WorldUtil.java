package ca.spottedleaf.starlight.common.util;

import net.minecraft.world.World;

public final class WorldUtil {

    // min, max are inclusive

    public static int getMaxSection(final World world) {
        return 15;
    }

    public static int getMinSection(final World world) {
        return 0;
    }

    public static int getMaxLightSection(final World world) {
        return getMaxSection(world) + 1;
    }

    public static int getMinLightSection(final World world) {
        return getMinSection(world) - 1;
    }

    public static int getTotalSections(final World world) {
        return getMaxSection(world) - getMinSection(world) + 1;
    }

    public static int getTotalLightSections(final World world) {
        return getMaxLightSection(world) - getMinLightSection(world) + 1;
    }

    private WorldUtil() {
        throw new RuntimeException();
    }
}
