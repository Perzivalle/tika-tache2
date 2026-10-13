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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/**
 * Manual tests written to kill the mutants of {@link Metadata} that survived the PIT run
 * (see the report, section 6): composite {@code set}, and the {@code hashCode} computation.
 */
public class MetadataManualTest {

    /**
     * Targets the surviving mutant "removed call to Metadata::set" in
     * {@code set(Property, String)} (primary property of a composite property).
     * The primary property must receive the value, not only the secondary ones.
     */
    @Test
    public void testSetCompositePropertyWritesPrimaryAndSecondary() {
        Property primary = Property.internalText("manual:primary");
        Property secondary = Property.externalText("manual:secondary");
        Property composite = Property.composite(primary, new Property[]{secondary});

        Metadata m = new Metadata();
        m.set(composite, "value");

        assertEquals("value", m.get(primary));
        assertEquals("value", m.get(secondary));
        assertEquals(2, m.size());
    }

    /**
     * Targets "replaced int return with 0" in {@code hashCode()} and in
     * {@code getMetadataEntryHashCode()}: a non-empty Metadata must not hash to 0.
     * Expected value, computed by hand from the formula
     * {@code hash(key) ^ Arrays.hashCode(values)}: "a".hashCode() = 97,
     * Arrays.hashCode({"1"}) = 31 * 1 + 49 = 80, and 97 ^ 80 = 49.
     */
    @Test
    public void testHashCodeOfSingleEntryIsNotZero() {
        Metadata m = new Metadata();
        m.add("a", "1");
        assertEquals(49, m.hashCode());
    }

    /**
     * Targets "Replaced XOR with AND" in {@code getMetadataEntryHashCode()}.
     * With the same entry, 97 & 80 would give 64 instead of 49, so the exact value tells
     * the two operators apart.
     */
    @Test
    public void testHashCodeCombinesKeyAndValuesWithXor() {
        Metadata m = new Metadata();
        m.add("a", "1");
        assertNotEquals(97 & 80, m.hashCode());
        assertEquals(97 ^ 80, m.hashCode());
    }

    /**
     * Targets "Replaced integer addition with subtraction" in {@code hashCode()}.
     * Entry "a"/"1" hashes to 49 and entry "b"/"2" to 51 ("b" = 98, {"2"} = 31 + 50 = 81,
     * 98 ^ 81 = 51): the sum is 100, whereas a subtraction would give -2 or 2.
     * The hash must not depend on insertion order either.
     */
    @Test
    public void testHashCodeIsSumOfEntryHashCodes() {
        Metadata first = new Metadata();
        first.add("a", "1");
        first.add("b", "2");
        Metadata second = new Metadata();
        second.add("b", "2");
        second.add("a", "1");

        assertEquals(100, first.hashCode());
        assertEquals(100, second.hashCode());
    }
}
