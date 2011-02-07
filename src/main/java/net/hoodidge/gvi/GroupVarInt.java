/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.hoodidge.gvi;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Encode and decode integers using zigzag group varint encoding.
 */
public final class GroupVarInt
{
    // 4 tags of 2 bits each in 1 tag byte
    // 1 tag byte plus 1-4 bytes per 4 values
    private static final int MAX_BYTES_PER_GROUP = 17;

    private static final int[] EMPTY = new int[0];

    private static final ThreadLocal<int[]> GROUP_OUT = new ThreadLocal<int[]>()
    {
        @Override protected int[] initialValue()
        {
            return new int[4];
        }
    };

    private static final ThreadLocal<byte[]> GROUP_IN = new ThreadLocal<byte[]>()
    {
        @Override protected byte[] initialValue()
        {
            return new byte[MAX_BYTES_PER_GROUP];
        }
    };

    // a lookup table from tags to possible layouts for the group: each layout
    // contains 4 x 2-bit lengths
    private static final int[][] LENGTHS = new int[256][4];
    // the reverse of the above table: tag byte from 4 x 2-bit lengths
    private static final byte[][][][] REVERSE = new byte[4][4][4][4];
    // the offsets from the beginning of the group (including the tag)
    // to the position we should mask at for a particular value length
    private static final int[][] OFFSETS = new int[256][4];
    static
    {
        for (int i = 0; i < 256; i++)
        {
            // the length of each value
            int[] layout = LENGTHS[i];
            int[] offsets = OFFSETS[i];
            int offset = 1;
            for (int k = 0; k < 4; k++)
            {
                int maskpos = (3 - k) * 2;
                // grab the 2 bit length
                int masked = i & (0x03 << maskpos);
                int length = masked >>> maskpos;
                assert 0 <= length && length < 4 : length;
                layout[k] = length;

                // offset for value of length 'length' in position 'k'
                offsets[k] = offset;
                offset += 1 + length;
            }
            // reverse lookup
            REVERSE[layout[0]][layout[1]][layout[2]][layout[3]] = b(i);
        }
    };

    private static int completeGroups(int count)
    {
        // complete groups (with length prepended)
        return (++count) >> 2;
    }

    /** The maximum size in bytes of 'count' integers. */
    public static int maxSize(int count)
    {
        int groups = completeGroups(count);
        // partial group
        if ((count & 0x1) == 0)
            groups++;
        return groups * MAX_BYTES_PER_GROUP;
    }

    /**
     * @return Encodes 'length' values into a new or recycled buffer.
     */
    public static ByteBuffer encode(int[] values, int length, ByteBuffer reuse)
    {
        // 'length' will be encoded as the first value in the array.
        int maxSize = maxSize(length);
        ByteBuffer buff = (reuse == null || reuse.capacity() < maxSize) ?
            ByteBuffer.allocate(maxSize) : reuse;
        buff.clear();

        // encode first group with length
        switch (length)
        {
            case 0: encodeGroup(buff, length, 0, 0, 0); break;
            case 1: encodeGroup(buff, length, values[0], 0, 0); break;
            case 2: encodeGroup(buff, length, values[0], values[1], 0); break;
            default: encodeGroup(buff, length, values[0], values[1], values[2]); break;
        }

        // encode complete groups, which will start with the 4th input value
        int idx = 3;
        int groups = completeGroups(length);
        for (int group = 1; group < groups; group++)
            encodeGroup(buff, values[idx++], values[idx++], values[idx++], values[idx++]);

        // encode the last group as 0 padded
        switch (length - idx)
        {
            case 3: encodeGroup(buff, values[idx], values[idx++], values[idx++], 0);
            case 2: encodeGroup(buff, values[idx], values[idx++], 0, 0);
            case 1: encodeGroup(buff, values[idx], 0, 0, 0);
        }

        // finish and return the buff
        buff.limit(buff.position()).rewind();
        return buff;
    }

    /** Encode a group of values, adjusting the position of the buffer. */
    private static void encodeGroup(ByteBuffer buff, int v1, int v2, int v3, int v4)
    {
        int tagidx = buff.position();
        buff.position(tagidx + 1);
        // relative puts for values
        byte tag = REVERSE[encodeValue(buff, v1)]
                          [encodeValue(buff, v2)]
                          [encodeValue(buff, v3)]
                          [encodeValue(buff, v4)];
        // absolute put for the tag
        buff.put(tagidx, tag);
    }

    /** Encode a single value, and return the size, leaving the buffer positioned for next. */
    private static int encodeValue(ByteBuffer buff, int v)
    {
        // encode to zigzag
        v = zig(v);
        int m;
        // encode msb first for maskable decoding
        int start = buff.position();
        if ((m = v >>> 24) != 0)
            buff.put(b(m));
        if ((m = v >>> 16) != 0)
            buff.put(b(m));
        if ((m = v >>> 8) != 0)
            buff.put(b(m));
        buff.put(b(v));
        return buff.position() - start - 1;
    }

    /**
     * @return The integers represented by the given buffer.
     */
    public static int[] decode(ByteBuffer buff)
    {
        if (buff.remaining() == 0)
            return EMPTY;
        int[] out;

        // decode first group to get the length
        int[] tempout = GROUP_OUT.get();
        decodeGroup(buff, tempout, 0);
        // copy valid values from temp
        int length = tempout[0];
        switch (length)
        {
            case 0: out = EMPTY; break;
            case 1:
                out = new int[1];
                out[0] = tempout[1];
                break;
            case 2:
                out = new int[2];
                System.arraycopy(tempout, 1, out, 0, 2);
                break;
            default:
                out = new int[length];
                System.arraycopy(tempout, 1, out, 0, 3);
                break;
        }

        // decode complete groups
        int idx = 3;
        int groups = (1 + length) >> 2; // div by four
        for (int group = 1; group < groups; group++, idx += 4)
            decodeGroup(buff, out, idx);

        // decode partial group, dropping padding
        int partials = length - idx;
        if (partials > 0)
        {
            decodeGroup(buff, tempout, 0);
            System.arraycopy(tempout, 0, out, idx, partials);
        }

        buff.rewind();
        return out;
    }

    private static void decodeGroup(ByteBuffer buff, int[] array, int offset)
    {
        int tag = 0xFF & buff.get();
        array[offset] = decodeValue(buff, LENGTHS[tag][0], OFFSETS[tag][0]);
        array[offset+1] = decodeValue(buff, LENGTHS[tag][1], OFFSETS[tag][1]);
        array[offset+2] = decodeValue(buff, LENGTHS[tag][2], OFFSETS[tag][2]);
        array[offset+3] = decodeValue(buff, LENGTHS[tag][3], OFFSETS[tag][3]);
    }

    private static int decodeValue(ByteBuffer buff, int length, int offset)
    {
        int v = 0xFF & buff.get();
        switch (length)
        {
            // NB: falling through
            case 3: v = (v << 8) | (0xFF & buff.get());
            case 2: v = (v << 8) | (0xFF & buff.get());
            case 1: v = (v << 8) | (0xFF & buff.get());
        }
        // decode from zigzag
        return zag(v);
    }

    private static final byte b(int integer) { return (byte)(integer & 0xFF); }

    /** From two's complement to zigzag. */
    private static final int zig(int n)
    {
        return (n << 1) ^ (n >> 31);
    }

    /** From zigzag to two's complement. */
    private static final int zag(int n)
    {
        return (n >>> 1) ^ -(n & 1);
    }

    private GroupVarInt(){}
}
