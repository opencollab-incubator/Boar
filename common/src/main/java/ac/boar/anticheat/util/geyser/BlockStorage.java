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

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import lombok.Getter;

@Getter
public class BlockStorage {

    public static final int SIZE = 4096;

    private final IntList palette;
    private BitArray bitArray;

    public BlockStorage(int airBlockId) {
        this(airBlockId, BitArrayVersion.V2);
    }

    public BlockStorage(int airBlockId, BitArrayVersion version) {
        this.bitArray = version.createArray(SIZE);
        this.palette = new IntArrayList(16);
        this.palette.add(airBlockId); // Air is at the start of every palette and controls what the default block is in second-layer non-air block spaces.
    }

    public BlockStorage(BitArray bitArray, IntList palette) {
        this.palette = palette;
        this.bitArray = bitArray;
    }

    public BlockStorage copy() {
        return new BlockStorage(this.bitArray.copy(), new IntArrayList(this.palette));
    }

    public int getFullBlock(int index) {
        return this.palette.getInt(this.bitArray.get(index));
    }

    public void setFullBlock(int index, int runtimeId) {
        int idx = this.idFor(runtimeId);
        this.bitArray.set(index, idx);
    }

    private void onResize(BitArrayVersion version) {
        BitArray newBitArray = version.createArray(SIZE);

        for (int i = 0; i < SIZE; i++) {
            newBitArray.set(i, this.bitArray.get(i));
        }
        this.bitArray = newBitArray;
    }

    public int idFor(int runtimeId) { // Set to public so we can reuse the palette ID for biomes
        int index = this.palette.indexOf(runtimeId);
        if (index != -1) {
            return index;
        }

        index = this.palette.size();
        this.palette.add(runtimeId);
        BitArrayVersion version = this.bitArray.version();
        if (index > version.getMaxEntryValue()) {
            BitArrayVersion next = version.next();
            if (next != null) {
                this.onResize(next);
            }
        }
        return index;
    }
}
