/*
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 */

package net.hoodidge.gvi;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

public class GroupVarIntTest
{
    private void assertEquals(int[] one, int[] two)
    {
        assertTrue(Arrays.toString(one) + " vs " + Arrays.toString(two),
                   Arrays.equals(one, two));
    }

    @Test
    public void testOneGroup()
    {
        int[] array = new int[]{1,1,1};
        ByteBuffer output = GroupVarInt.encode(array, array.length, null);
        assertEquals(array, GroupVarInt.decode(output));
    }

    @Test
    public void testMaxValueOneGroup()
    {
        int[] array = new int[]{Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE};
        ByteBuffer output = GroupVarInt.encode(array, array.length, null);
        assertEquals(array, GroupVarInt.decode(output));
    }

    @Test
    public void testPartialGroup()
    {
        int[] array = new int[]{1,1,1,1};
        ByteBuffer output = GroupVarInt.encode(array, array.length, null);
        assertEquals(array, GroupVarInt.decode(output));
    }

    @Test
    public void testPowersOfTwo()
    {
        int[] array = new int[32];
        for (int i = 0; i < array.length; i++)
            array[i] = (1 << i) - 1;
        ByteBuffer output = GroupVarInt.encode(array, array.length, null);
        assertEquals(array, GroupVarInt.decode(output));
    }

    @Test
    public void testPowersOfTwoNegative()
    {
        int[] array = new int[32];
        for (int i = 0; i < array.length; i++)
            array[i] = -((1 << i) - 1);
        ByteBuffer output = GroupVarInt.encode(array, array.length, null);
        assertEquals(array, GroupVarInt.decode(output));
    }
}
