/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.encoder;

import org.grails.buffer.CodecPrintWriter;
import org.grails.buffer.FastStringPrintWriter;
import org.grails.buffer.FastStringWriter;
import org.grails.buffer.GrailsPrintWriter;
import org.grails.buffer.StreamByteBuffer;
import org.grails.buffer.StreamCharBuffer;
import org.grails.charsequences.CharSequences;
import org.grails.encoder.impl.BasicCodecLookup;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class JavaCallerPin {

    @Test
    public void testJavaInteroperability() throws IOException {
        FastStringWriter writer = new FastStringWriter();
        writer.write("hello");
        assertEquals("hello", writer.toString());

        FastStringPrintWriter printWriter = new FastStringPrintWriter();
        printWriter.print("world");
        assertEquals("world", printWriter.toString());

        StreamCharBuffer charBuffer = new StreamCharBuffer();
        charBuffer.getWriter().write("buffer");
        assertEquals("buffer", charBuffer.toString());

        StreamByteBuffer byteBuffer = new StreamByteBuffer();
        assertNotNull(byteBuffer.getOutputStream());

        CodecIdentifier identifier = new DefaultCodecIdentifier("testCodec");
        assertEquals("testCodec", identifier.getCodecName());

        BasicCodecLookup lookup = new BasicCodecLookup();
        assertNotNull(lookup);

        CharSequence cs = CharSequences.createCharSequence("test".toCharArray());
        assertEquals(4, cs.length());
    }
}
