package net.highwaybuilder;

import net.highwaybuilder.config.Config;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * Describes the highway in a coordinate system that works for straight and diagonal directions.
 *
 * <p>For a block at (x, z) relative to the start block:
 * <ul>
 *   <li>{@code along   = x*dx + z*dz} - how far down the highway the block is</li>
 *   <li>{@code lateral = -x*dz + z*dx} - how far to the side (positive = to the right)</li>
 * </ul>
 * For cardinal directions both are plain block offsets. For diagonals one block step forward
 * changes {@code along} by 2, and every lateral value is one "staircase" row of the highway.
 */
public final class HighwayGeometry {
    public final int dx, dz;
    public final boolean diagonal;
    public final BlockPos origin;

    public HighwayGeometry(int dx, int dz, BlockPos origin) {
        this.dx = dx;
        this.dz = dz;
        this.diagonal = dx != 0 && dz != 0;
        this.origin = origin;
    }

    /** Lowest lateral value that belongs to the walkable lane (negative = left side). */
    public int left() {
        int w = Config.WIDTH.get();
        if (diagonal) return Math.max(2, w) - 1;
        return (w - 1) / 2;
    }

    /** Highest lateral value that belongs to the walkable lane. */
    public int right() {
        int w = Config.WIDTH.get();
        if (diagonal) return Math.max(2, w) - 1;
        return w - 1 - (w - 1) / 2;
    }

    public boolean inLane(int lateral) {
        return lateral >= -left() && lateral <= right();
    }

    public boolean isRailing(int lateral) {
        return lateral == -left() - 1 || lateral == right() + 1;
    }

    public int along(int x, int z) {
        return (x - origin.getX()) * dx + (z - origin.getZ()) * dz;
    }

    public int lateral(int x, int z) {
        return -(x - origin.getX()) * dz + (z - origin.getZ()) * dx;
    }

    public double alongExact(double x, double z) {
        double rx = x - (origin.getX() + 0.5), rz = z - (origin.getZ() + 0.5);
        return rx * dx + rz * dz;
    }

    public double lateralExact(double x, double z) {
        double rx = x - (origin.getX() + 0.5), rz = z - (origin.getZ() + 0.5);
        return -rx * dz + rz * dx;
    }

    /** Lateral value of the lane's center line. */
    public double centerLateral() {
        return (right() - left()) / 2.0;
    }

    /** "along" units per block walked. */
    public int step() {
        return diagonal ? 2 : 1;
    }

    /** Minecraft yaw that faces down the highway. */
    public float yaw() {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    public String name() {
        String ns = dz < 0 ? "North" : dz > 0 ? "South" : "";
        String ew = dx > 0 ? "East" : dx < 0 ? "West" : "";
        if (!ns.isEmpty() && !ew.isEmpty()) return ns + "-" + ew;
        return ns + ew;
    }

    /** Picks a direction {dx, dz} from the player's yaw. */
    public static int[] snap(float yaw, Config.DirectionMode mode) {
        float y = MathHelper.wrapDegrees(yaw);
        float target = switch (mode) {
            case CARDINAL -> Math.round(y / 90f) * 90f;
            case DIAGONAL -> (float) Math.floor(y / 90f) * 90f + 45f;
            case AUTO -> Math.round(y / 45f) * 45f;
        };
        double rad = Math.toRadians(target);
        int dx = (int) Math.round(-Math.sin(rad));
        int dz = (int) Math.round(Math.cos(rad));
        return new int[]{dx, dz};
    }
}
