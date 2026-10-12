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
package org.apache.tika.metadata;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

/**
 * Additional tests for {@link Metadata}, aimed at typed accessors, type checks
 * (PropertyTypeException), equals/hashCode/toString and removal, which the
 * existing tests cover only partially.
 */
public class MetadataExtraTest {

    private static final Property TEXT = Property.internalText("test:text");
    private static final Property INT = Property.internalInteger("test:int");
    private static final Property DATE = Property.internalDate("test:date");
    private static final Property REAL_SEQ = Property.externalRealSeq("test:realseq");
    private static final Property INT_SEQ = Property.internalIntegerSequence("test:intseq");
    private static final Property BOOL_SEQ = Property.externalBooleanSeq("test:boolseq");
    private static final Property TEXT_BAG = Property.internalTextBag("test:textbag");

    private static Calendar utc(int y, int mo, int d, int h, int mi, int s) {
        Calendar c = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(y, mo, d, h, mi, s);
        return c;
    }

    // ---------- getInt ----------

    @Test
    public void testGetIntReturnsNullWhenMissingOrInvalid() {
        Metadata m = new Metadata();
        assertNull(m.getInt(INT));
        m.set(INT, "not-a-number");
        assertNull(m.getInt(INT));
        // too large for an int
        m.set(INT, "5000000000");
        assertNull(m.getInt(INT));
    }

    @Test
    public void testGetIntReturnsNullForWrongPropertyType() {
        Metadata m = new Metadata();
        m.set(TEXT, "12");
        assertNull(m.getInt(TEXT), "text property is not an integer property");
        m.add(INT_SEQ, 12);
        assertNull(m.getInt(INT_SEQ), "sequence property is not SIMPLE");
    }

    // ---------- getLong / set long ----------

    // ---------- boolean ----------

    // ---------- double ----------

    // ---------- sequences ----------

    @Test
    public void testIntSequence() {
        Metadata m = new Metadata();
        m.add(INT_SEQ, 1);
        m.add(INT_SEQ, 2);
        m.add(INT_SEQ, 3);
        assertArrayEquals(new int[]{1, 2, 3}, m.getIntValues(INT_SEQ));
        assertTrue(m.isMultiValued(INT_SEQ));
    }

    @Test
    public void testLongValuesLargerThanInt() {
        Metadata m = new Metadata();
        m.add(REAL_SEQ, "10000000000");
        m.add(REAL_SEQ, "-1");
        assertArrayEquals(new long[]{10_000_000_000L, -1L}, m.getLongValues(REAL_SEQ));
        // an int accessor refuses a sequence of reals
        assertThrows(PropertyTypeException.class, () -> m.getIntValues(REAL_SEQ));

        Metadata n = new Metadata();
        n.add(INT_SEQ, "10000000000");
        assertThrows(NumberFormatException.class, () -> n.getIntValues(INT_SEQ));
    }

    @Test
    public void testSequenceAccessorsRejectWrongTypes() {
        Metadata m = new Metadata();
        // not a sequence
        assertThrows(PropertyTypeException.class, () -> m.getIntValues(INT));
        assertThrows(PropertyTypeException.class, () -> m.getLongValues(INT));
        assertThrows(PropertyTypeException.class, () -> m.getIntValues(TEXT_BAG));
        assertThrows(PropertyTypeException.class, () -> m.getLongValues(TEXT_BAG));
        // a sequence, but not of numbers
        assertThrows(PropertyTypeException.class, () -> m.getIntValues(BOOL_SEQ));
        assertThrows(PropertyTypeException.class, () -> m.getLongValues(BOOL_SEQ));
    }

    // ---------- add(Property, String) multi-value rules ----------

    @Test
    public void testAddSecondValueToSinglePropertyThrows() {
        Metadata m = new Metadata();
        m.add(TEXT, "first");
        assertThrows(PropertyTypeException.class, () -> m.add(TEXT, "second"));
        assertArrayEquals(new String[]{"first"}, m.getValues(TEXT));
        assertFalse(m.isMultiValued(TEXT));
    }

    // ---------- Calendar / Date ----------

    @Test
    public void testSetCalendar() {
        Metadata m = new Metadata();
        Calendar c = utc(2024, Calendar.MARCH, 15, 10, 20, 30);
        m.set(DATE, c);
        assertEquals("2024-03-15T10:20:30Z", m.get(DATE));
        assertEquals(c.getTime(), m.getDate(DATE));
    }

    // ---------- remove / size / names ----------

    @Test
    public void testRemoveByNameAndByProperty() {
        Metadata m = new Metadata();
        m.add("a", "1");
        m.add("a", "2");
        m.set(TEXT, "t");
        assertEquals(2, m.size());

        m.remove("a");
        assertEquals(1, m.size());
        assertEquals(0, m.getValues("a").length);
        assertNull(m.get("a"));

        m.remove(TEXT);
        assertEquals(0, m.size());
        assertEquals(0, m.names().length);
    }

    // ---------- reserved keys ----------

    // ---------- putAll / reconstruct ----------

    // ---------- equals / hashCode / toString ----------

    @Test
    public void testEqualsAndHashCodeForSameContent() {
        Metadata a = new Metadata();
        Metadata b = new Metadata();
        a.add("k", "1");
        a.add("k", "2");
        a.add("j", "v");
        b.add("j", "v");
        b.add("k", "1");
        b.add("k", "2");
        assertEquals(a, b);
        assertEquals(b, a);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a, a);
    }

    @Test
    public void testToString() {
        Metadata m = new Metadata();
        assertEquals("", m.toString());
        m.add("k", "1");
        assertEquals("k=1", m.toString());
        m.add("k", "2");
        assertEquals("k=1 k=2", m.toString());
    }
}
