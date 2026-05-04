/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package ac.boar.anticheat.util.geyser;

import ac.boar.anticheat.util.MathUtil;
import org.cloudburstmc.protocol.common.util.Preconditions;

// Geyser chunk section
public record BoarChunkSection(BlockStorage[] storage) {
    public BoarChunkSection(int initialBlockId) {
        this(new BlockStorage[]{new BlockStorage(initialBlockId), new BlockStorage(initialBlockId)});
    }

    public int getFullBlock(int x, int y, int z, int layer) {
        if (layer < 0 || layer >= this.storage.length) {
            return Integer.MIN_VALUE;
        }

        checkBounds(x, y, z);
        return this.storage[layer].getFullBlock(MathUtil.blockPosition(x, y, z));
    }

    public void setFullBlock(int x, int y, int z, int layer, int block) {
        if (layer < 0 || layer >= this.storage.length) {
            return;
        }

        checkBounds(x, y, z);
        this.storage[layer].setFullBlock(MathUtil.blockPosition(x, y, z), block);
    }

    private static void checkBounds(int x, int y, int z) {
        Preconditions.checkArgument(x >= 0 && x < 16, "x (%s) is not between 0 and 15", x);
        Preconditions.checkArgument(y >= 0 && y < 16, "y (%s) is not between 0 and 15", y);
        Preconditions.checkArgument(z >= 0 && z < 16, "z (%s) is not between 0 and 15", z);
    }
}
