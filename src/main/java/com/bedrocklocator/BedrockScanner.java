package com.bedrocklocator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Scans the actual Nether bedrock ceiling from the live game world.
 * Returns the lowest bedrock y-level for each column in the scan area.
 * Returns -1 for columns that are not yet loaded.
 */
public class BedrockScanner {

    public static final int NETHER_CEILING_MIN = 122;
    public static final int NETHER_CEILING_MAX = 127;

    /**
     * Scan a grid of (radius*2+1) x (radius*2+1) columns centred on (cx, cz).
     * Each cell = lowest bedrock y in that column (123-127), or 0 if unloaded.
     */
    public static int[][] scan(int cx, int cz, int radius) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return new int[0][0];

        int size = radius * 2 + 1;
        int[][] result = new int[size][size];

        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                int bx = cx - radius + dx;
                int bz = cz - radius + dz;
                result[dx][dz] = lowestBedrockY(level, bx, bz);
            }
        }
        return result;
    }

    /** Returns the lowest bedrock y in the ceiling column, 0 if unloaded/none found */
    private static int lowestBedrockY(ClientLevel level, int bx, int bz) {
        for (int y = NETHER_CEILING_MIN; y <= NETHER_CEILING_MAX; y++) {
            BlockState bs = level.getBlockState(new BlockPos(bx, y, bz));
            if (bs.is(Blocks.BEDROCK)) return y;
        }
        // If nothing found at low range, check if y=127 is bedrock (fully solid column)
        BlockState top = level.getBlockState(new BlockPos(bx, 127, bz));
        return top.is(Blocks.BEDROCK) ? 127 : 0;
    }

    /** True if the player is currently in the Nether */
    public static boolean isInNether() {
        var level = Minecraft.getInstance().level;
        if (level == null) return false;
        return level.dimensionType().hasCeiling();
    }
}
