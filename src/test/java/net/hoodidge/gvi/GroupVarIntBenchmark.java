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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.List;

import com.google.caliper.Param;
import com.google.caliper.SimpleBenchmark;

import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;

public class GroupVarIntBenchmark extends SimpleBenchmark
{
    private static final int[] VALUES = new int[10000];
    static
    {
        Random random = new Random();
        for (int i = 0; i < VALUES.length; i++)
            VALUES[i] = random.nextInt();
    };

    private ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private BinaryEncoder enc = new BinaryEncoder(baos);
    private BinaryDecoder dec = null;
    private DecoderFactory fact = new DecoderFactory();

    private ByteBuffer gvibuffer = null;

    private void avroEncode() throws Exception
    {
        baos.reset();
        for (int k = 0; k < VALUES.length; k++)
            enc.writeInt(VALUES[k]);
    }

    public void timeAvroEncode(int reps) throws Exception
    {
        for (int i = 0; i < reps; i++)
        {
            avroEncode();
        }
    }

    public void timeAvroDecode(int reps) throws Exception
    {
        avroEncode();
        byte[] buff = baos.toByteArray();
        int[] out = new int[VALUES.length];
        for (int i = 0; i < reps; i++)
        {
            dec = fact.createBinaryDecoder(buff, dec);
            for (int k = 0; k < VALUES.length; k++)
                out[k] = dec.readInt();
        }
    }

    private void gviEncode()
    {
        gvibuffer = GroupVarInt.encode(VALUES, VALUES.length, gvibuffer);
    }

    public void timeGroupVarIntEncode(int reps)
    {
        for (int i = 0; i < reps; i++)
        {
            gviEncode();
        }
    }

    public void timeGroupVarIntDecode(int reps)
    {
        gviEncode();
        for (int i = 0; i < reps; i++)
        {
            GroupVarInt.decode(gvibuffer);
        }
    }
}
