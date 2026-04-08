package com.bedrocklocator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

public class BedrockScanner {
    public static final int CEILING_MAX = 127;
    public static final int CEILING_MIN = 122;

    public static int[][] scan(int cx, int cz, int radius) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return new int[0][0];
        int size = radius * 2 + 1;
        int[][] result = new int[size][size];
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                int bx = cx - radius + dx, bz = cz - radius + dz;
                result[dx][dz] = lowestBedrock(level, bx, bz);
            }
        }
        return result;
    }

    private static int lowestBedrock(ClientLevel level, int bx, int bz) {
        for (int y = CEILING_MIN; y <= CEILING_MAX; y++) {
            if (level.getBlockState(new BlockPos(bx, y, bz)).is(Blocks.BEDROCK)) return y;
        }
        return level.getBlockState(new BlockPos(bx, 127, bz)).is(Blocks.BEDROCK) ? 127 : 0;
    }

    public static boolean isInNether() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return false;
        return level.dimensionType().hasCeiling();
    }
}
