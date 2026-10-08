package ca.spottedleaf.starlight.common.light;

final class Shapes {

    static VoxelShape empty() {
        return null;
    }

    static boolean faceShapeOccludes(final VoxelShape first, final VoxelShape second) {
        return false;
    }

    private Shapes() {}
}

final class VoxelShape {
    private VoxelShape() {}
}
