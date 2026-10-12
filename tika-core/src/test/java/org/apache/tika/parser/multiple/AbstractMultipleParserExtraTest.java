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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.apache.tika.parser.DummyParser;
import org.apache.tika.parser.ErrorParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.multiple.AbstractMultipleParser.MetadataPolicy;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Additional tests for {@link AbstractMultipleParser}: supported types,
 * accessors, the metadata merge policies, failure propagation and the
 * per-parser {@link ContentHandlerFactory} entry point.
 */
public class AbstractMultipleParserExtraTest {

    private static final Set<MediaType> OCTET = Collections.singleton(MediaType.OCTET_STREAM);
    private static final byte[] DATA = new byte[]{0, 1, 2, 3, 4};

    // ------------------------------------------------------------------
    // Test helpers
    // ------------------------------------------------------------------

    /** Adds fixed metadata, optionally then fails; counts how often it was called. */
    private static class ScriptedParser implements Parser {
        private static final long serialVersionUID = 1L;
        private final Map<String, String> meta;
        private final Exception failure;
        private final String text;
        int calls = 0;

        ScriptedParser(Map<String, String> meta, Exception failure, String text) {
            this.meta = meta;
            this.failure = failure;
            this.text = text;
        }

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return OCTET;
        }

        @Override
        public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                          ParseContext context) throws IOException, SAXException, TikaException {
            calls++;
            for (Map.Entry<String, String> e : meta.entrySet()) {
                metadata.add(e.getKey(), e.getValue());
            }
            if (text != null) {
                XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
                xhtml.startDocument();
                xhtml.characters(text.toCharArray(), 0, text.length());
                xhtml.endDocument();
            }
            if (failure instanceof IOException) {
                throw (IOException) failure;
            } else if (failure instanceof SAXException) {
                throw (SAXException) failure;
            } else if (failure instanceof TikaException) {
                throw (TikaException) failure;
            } else if (failure instanceof RuntimeException) {
                throw (RuntimeException) failure;
            }
        }
    }

    /** Subclass that always asks to stop after the first parser, and logs the hooks. */
    private static class StopImmediatelyParser extends AbstractMultipleParser {
        private static final long serialVersionUID = 1L;
        final List<Parser> prepared = new ArrayList<>();
        final List<Parser> completed = new ArrayList<>();

        StopImmediatelyParser(MetadataPolicy policy, Parser... parsers) {
            super(MediaTypeRegistry.getDefaultRegistry(), policy, parsers);
        }

        @Override
        protected void parserPrepare(Parser parser, Metadata metadata, ParseContext context) {
            prepared.add(parser);
        }

        @Override
        protected boolean parserCompleted(Parser parser, Metadata metadata,
                                          ContentHandler handler, ParseContext context,
                                          Exception exception) {
            completed.add(parser);
            return false;
        }
    }

    private static Map<String, String> map(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Metadata md(String... kv) {
        Metadata m = new Metadata();
        for (int i = 0; i < kv.length; i += 2) {
            m.add(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Metadata run(Parser p) throws Exception {
        Metadata metadata = new Metadata();
        p.parse(TikaInputStream.get(DATA), new BodyContentHandler(), metadata, new ParseContext());
        return metadata;
    }

    // ------------------------------------------------------------------
    // Supported types / accessors
    // ------------------------------------------------------------------

    @Test
    public void testSupportedTypesIsUnionOfAllParsers() {
        Set<MediaType> a = new HashSet<>(Arrays.asList(MediaType.OCTET_STREAM,
            MediaType.TEXT_PLAIN));
        Set<MediaType> b = new HashSet<>(Arrays.asList(MediaType.TEXT_PLAIN,
            MediaType.APPLICATION_XML));
        Parser p = new FallbackParser(MediaTypeRegistry.getDefaultRegistry(),
            MetadataPolicy.FIRST_WINS,
            new DummyParser(a, new HashMap<>(), null),
            new DummyParser(b, new HashMap<>(), null));

        Set<MediaType> offered = p.getSupportedTypes(new ParseContext());
        assertEquals(3, offered.size());
        assertTrue(offered.contains(MediaType.OCTET_STREAM));
        assertTrue(offered.contains(MediaType.TEXT_PLAIN));
        assertTrue(offered.contains(MediaType.APPLICATION_XML));
    }

    @Test
    public void testGetAllParsersKeepsOrderAndIsUnmodifiable() {
        Parser first = new ErrorParser();
        Parser second = new DummyParser(OCTET, new HashMap<>(), null);
        AbstractMultipleParser p = new StopImmediatelyParser(MetadataPolicy.FIRST_WINS, first,
            second);

        List<Parser> all = p.getAllParsers();
        assertEquals(2, all.size());
        assertSame(first, all.get(0));
        assertSame(second, all.get(1));
        assertThrows(UnsupportedOperationException.class, () -> all.add(first));
        assertThrows(UnsupportedOperationException.class, () -> all.remove(0));
    }

    @Test
    public void testSupplementingParserRejectsDiscardAll() {
        assertThrows(IllegalArgumentException.class,
            () -> new SupplementingParser(MediaTypeRegistry.getDefaultRegistry(),
                MetadataPolicy.DISCARD_ALL, new ErrorParser()));
        for (MetadataPolicy ok : SupplementingParser.allowedPolicies) {
            assertEquals(ok, new SupplementingParser(MediaTypeRegistry.getDefaultRegistry(), ok)
                .getMetadataPolicy());
        }
        assertEquals(3, SupplementingParser.allowedPolicies.size());
        assertEquals(4, FallbackParser.allowedPolicies.size());
    }

    // ------------------------------------------------------------------
    // mergeMetadata
    // ------------------------------------------------------------------

    @Test
    public void testMergeFirstWinsKeepsOldOnClash() {
        Metadata merged = AbstractMultipleParser.mergeMetadata(
            md("a", "new", "c", "new-only"), md("a", "old", "b", "old-only"),
            MetadataPolicy.FIRST_WINS);
        assertArrayEquals(new String[]{"old"}, merged.getValues("a"));
        assertArrayEquals(new String[]{"old-only"}, merged.getValues("b"));
        assertArrayEquals(new String[]{"new-only"}, merged.getValues("c"));
        assertEquals(3, merged.size());
    }

    @Test
    public void testMergeKeepAllCombinesUniqueValuesOldFirst() {
        Metadata merged = AbstractMultipleParser.mergeMetadata(
            md("a", "2", "a", "3"), md("a", "1", "a", "2"),
            MetadataPolicy.KEEP_ALL);
        assertArrayEquals(new String[]{"1", "2", "3"}, merged.getValues("a"));
    }

    @Test
    public void testMergeNeverTouchesTrackingKeys() {
        for (MetadataPolicy policy : Arrays.asList(MetadataPolicy.FIRST_WINS,
            MetadataPolicy.LAST_WINS, MetadataPolicy.KEEP_ALL)) {
            Metadata old = new Metadata();
            old.add(TikaCoreProperties.TIKA_PARSED_BY, "OldParser");
            old.add(TikaCoreProperties.EMBEDDED_EXCEPTION, "old exception");
            Metadata fresh = new Metadata();
            fresh.add(TikaCoreProperties.TIKA_PARSED_BY, "NewParser");
            fresh.add(TikaCoreProperties.EMBEDDED_EXCEPTION, "new exception");

            Metadata merged = AbstractMultipleParser.mergeMetadata(fresh, old, policy);

            assertArrayEquals(new String[]{"NewParser"},
                merged.getValues(TikaCoreProperties.TIKA_PARSED_BY), policy.name());
            assertArrayEquals(new String[]{"new exception"},
                merged.getValues(TikaCoreProperties.EMBEDDED_EXCEPTION), policy.name());
        }
    }

    // ------------------------------------------------------------------
    // parse(): policies end to end
    // ------------------------------------------------------------------

    @Test
    public void testFallbackStopsAtFirstSuccess() throws Exception {
        ScriptedParser first = new ScriptedParser(map("a", "1"), null, null);
        ScriptedParser second = new ScriptedParser(map("a", "2"), null, null);
        Metadata m = run(new FallbackParser(MediaTypeRegistry.getDefaultRegistry(),
            MetadataPolicy.LAST_WINS, first, second));
        assertEquals(1, first.calls);
        assertEquals(0, second.calls);
        assertArrayEquals(new String[]{"1"}, m.getValues("a"));
        assertArrayEquals(new String[]{ScriptedParser.class.getName()},
            m.getValues(TikaCoreProperties.TIKA_PARSED_BY));
    }

    @Test
    public void testFallbackDiscardAllDropsMetadataFromFailedParser() throws Exception {
        ScriptedParser partial = new ScriptedParser(map("x", "from-failed"),
            new TikaException("late failure"), null);
        ScriptedParser good = new ScriptedParser(map("y", "from-good"), null, null);
        Metadata m = run(new FallbackParser(MediaTypeRegistry.getDefaultRegistry(),
            MetadataPolicy.DISCARD_ALL, partial, good));
        assertNull(m.get("x"));
        assertEquals("from-good", m.get("y"));
        // ...but the record of the failure must survive the discard
        assertNotNull(m.get(TikaCoreProperties.EMBEDDED_EXCEPTION));
    }

    // ------------------------------------------------------------------
    // parse(): hooks and failure propagation
    // ------------------------------------------------------------------

    @Test
    public void testRuntimeExceptionWrappedInTikaException() {
        IllegalStateException boom = new IllegalStateException("runtime boom");
        ScriptedParser failing = new ScriptedParser(map(), boom, null);
        StopImmediatelyParser p = new StopImmediatelyParser(MetadataPolicy.FIRST_WINS, failing);
        TikaException thrown = assertThrows(TikaException.class, () -> run(p));
        assertTrue(thrown.getMessage().startsWith("Unexpected RuntimeException from "),
            thrown.getMessage());
        assertSame(boom, thrown.getCause());
    }

    // ------------------------------------------------------------------
    // parse() with a ContentHandlerFactory (deprecated API)
    // ------------------------------------------------------------------

    @Test
    @SuppressWarnings("deprecation")
    public void testContentHandlerFactoryGivesOneHandlerPerParser() throws Exception {
        Parser p = new SupplementingParser(MediaTypeRegistry.getDefaultRegistry(),
            MetadataPolicy.FIRST_WINS,
            new ScriptedParser(map(), null, "text-one"),
            new ScriptedParser(map(), null, "text-two"));
        final List<BodyContentHandler> handlers = new ArrayList<>();
        ContentHandlerFactory factory = () -> {
            BodyContentHandler h = new BodyContentHandler();
            handlers.add(h);
            return h;
        };

        ((AbstractMultipleParser) p).parse(TikaInputStream.get(DATA), factory, new Metadata(),
            new ParseContext());

        assertEquals(2, handlers.size());
        assertTrue(handlers.get(0).toString().contains("text-one"));
        assertFalse(handlers.get(0).toString().contains("text-two"));
        assertTrue(handlers.get(1).toString().contains("text-two"));
        assertFalse(handlers.get(1).toString().contains("text-one"));
    }

}
