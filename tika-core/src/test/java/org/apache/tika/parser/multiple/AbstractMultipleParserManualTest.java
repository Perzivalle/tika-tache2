/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.multiple;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.ErrorParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.multiple.AbstractMultipleParser.MetadataPolicy;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Manual tests written to kill the mutants of {@link AbstractMultipleParser} that survived
 * the PIT run (see the report, section 6).
 */
public class AbstractMultipleParserManualTest {

    /** Reads the whole stream and stores the number of bytes read under the given key. */
    private static class CountingParser implements Parser {
        private static final long serialVersionUID = 1L;
        private final String key;

        CountingParser(String key) {
            this.key = key;
        }

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return java.util.Collections.emptySet();
        }

        @Override
        public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                          ParseContext context) throws IOException, SAXException, TikaException {
            int total = 0;
            byte[] buffer = new byte[512];
            int n;
            while ((n = tis.read(buffer)) != -1) {
                total += n;
            }
            metadata.add(key, Integer.toString(total));
        }
    }

    /** Supplementing-style parser (always continues) which logs what the hooks received. */
    private static class HookLoggingParser extends AbstractMultipleParser {
        private static final long serialVersionUID = 1L;
        final List<Parser> prepared = new ArrayList<>();
        final List<String> exceptionSeenOnCompletion = new ArrayList<>();

        HookLoggingParser(Parser... parsers) {
            super(MediaTypeRegistry.getDefaultRegistry(), MetadataPolicy.LAST_WINS, parsers);
        }

        @Override
        protected void parserPrepare(Parser parser, Metadata metadata, ParseContext context) {
            prepared.add(parser);
            metadata.add("prepared-by-hook", "yes");
        }

        @Override
        protected boolean parserCompleted(Parser parser, Metadata metadata,
                                          ContentHandler handler, ParseContext context,
                                          Exception exception) {
            exceptionSeenOnCompletion.add(metadata.get(TikaCoreProperties.EMBEDDED_EXCEPTION));
            return true;
        }
    }

    private static TikaInputStream streamOf(int size) {
        byte[] data = new byte[size];
        Arrays.fill(data, (byte) 7);
        // a plain InputStream, so that rewinding has to be set up by the parser itself
        return TikaInputStream.get(new ByteArrayInputStream(data));
    }

    /**
     * Targets the surviving mutants "removed call to TikaInputStream::rewind" and
     * "removed call to TikaInputStream::enableRewind" in {@code parse}.
     * Both parsers read the stream to its end; the second one only sees the same bytes
     * if the stream was made rewindable and was rewound between the two parsers.
     */
    @Test
    public void testStreamIsRewoundBetweenParsers() throws Exception {
        AbstractMultipleParser parser = new HookLoggingParser(
            new CountingParser("first-count"), new CountingParser("second-count"));
        Metadata metadata = new Metadata();

        parser.parse(streamOf(3000), new BodyContentHandler(), metadata, new ParseContext());

        assertEquals("3000", metadata.get("first-count"));
        assertEquals("3000", metadata.get("second-count"));
    }

    /**
     * Targets "removed call to AbstractMultipleParser::parserPrepare".
     * The hook must be called once per parser, in order, and what it writes into the
     * metadata must reach the final result.
     */
    @Test
    public void testParserPrepareHookIsCalledForEachParser() throws Exception {
        Parser first = new CountingParser("a");
        Parser second = new CountingParser("b");
        HookLoggingParser parser = new HookLoggingParser(first, second);
        Metadata metadata = new Metadata();

        parser.parse(streamOf(10), new BodyContentHandler(), metadata, new ParseContext());

        assertEquals(Arrays.asList(first, second), parser.prepared);
        assertEquals("yes", metadata.get("prepared-by-hook"));
    }

    /**
     * Targets "removed call to ParserUtils::recordParserFailure" for the working copy of the
     * metadata (the second call in the catch block of {@code parse}).
     * The implementation's {@code parserCompleted} hook is handed that working copy and
     * must be able to see the failure there.
     */
    @Test
    public void testFailureIsVisibleToParserCompletedHook() throws Exception {
        HookLoggingParser parser = new HookLoggingParser(new ErrorParser());

        parser.parse(streamOf(10), new BodyContentHandler(), new Metadata(), new ParseContext());

        assertEquals(1, parser.exceptionSeenOnCompletion.size());
        assertNotNull(parser.exceptionSeenOnCompletion.get(0));
        assertTrue(parser.exceptionSeenOnCompletion.get(0).contains("Parse error"));
    }

    /**
     * Targets "replaced return value with null" in {@code getMediaTypeRegistry()},
     * which was not covered by any test.
     */
    @Test
    public void testGetMediaTypeRegistryReturnsTheConfiguredRegistry() {
        MediaTypeRegistry registry = new MediaTypeRegistry();
        AbstractMultipleParser parser = new HookLoggingParser();
        parser.setMediaTypeRegistry(registry);

        assertSame(registry, parser.getMediaTypeRegistry());
        assertArrayEquals(new Parser[0], parser.getAllParsers().toArray());
    }
}
