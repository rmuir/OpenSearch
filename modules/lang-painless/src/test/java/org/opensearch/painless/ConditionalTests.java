/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.painless;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.joining;

public class ConditionalTests extends ScriptTestCase {
    public void testBasic() {
        assertEquals(2, exec("boolean x = true; return x ? 2 : 3;"));
        assertEquals(3, exec("boolean x = false; return x ? 2 : 3;"));
        assertEquals(3, exec("boolean x = false, y = true; return x && y ? 2 : 3;"));
        assertEquals(2, exec("boolean x = true, y = true; return x && y ? 2 : 3;"));
        assertEquals(2, exec("boolean x = true, y = false; return x || y ? 2 : 3;"));
        assertEquals(3, exec("boolean x = false, y = false; return x || y ? 2 : 3;"));
    }

    public void testPrecedence() {
        assertEquals(4, exec("boolean x = false, y = true; return x ? (y ? 2 : 3) : 4;"));
        assertEquals(2, exec("boolean x = true, y = true; return x ? (y ? 2 : 3) : 4;"));
        assertEquals(3, exec("boolean x = true, y = false; return x ? (y ? 2 : 3) : 4;"));
        assertEquals(2, exec("boolean x = true, y = true; return x ? y ? 2 : 3 : 4;"));
        assertEquals(4, exec("boolean x = false, y = true; return x ? y ? 2 : 3 : 4;"));
        assertEquals(3, exec("boolean x = true, y = false; return x ? y ? 2 : 3 : 4;"));
        assertEquals(3, exec("boolean x = false, y = true; return x ? 2 : y ? 3 : 4;"));
        assertEquals(2, exec("boolean x = true, y = false; return x ? 2 : y ? 3 : 4;"));
        assertEquals(4, exec("boolean x = false, y = false; return x ? 2 : y ? 3 : 4;"));
        assertEquals(4, exec("boolean x = false, y = false; return (x ? true : y) ? 3 : 4;"));
        assertEquals(4, exec("boolean x = true, y = false; return (x ? false : y) ? 3 : 4;"));
        assertEquals(3, exec("boolean x = false, y = true; return (x ? false : y) ? 3 : 4;"));
        assertEquals(2, exec("boolean x = true, y = false; return (x ? false : y) ? (x ? 3 : 4) : x ? 2 : 1;"));
        assertEquals(2, exec("boolean x = true, y = false; return (x ? false : y) ? x ? 3 : 4 : x ? 2 : 1;"));
        assertEquals(4, exec("boolean x = false, y = true; return x ? false : y ? x ? 3 : 4 : x ? 2 : 1;"));
    }

    public void testAssignment() {
        assertEquals(4D, exec("boolean x = false; double z = x ? 2 : 4.0F; return z;"));
        assertEquals((byte)7, exec("boolean x = false; int y = 2; byte z = x ? (byte)y : 7; return z;"));
        assertEquals((byte)7, exec("boolean x = false; int y = 2; byte z = (byte)(x ? y : 7); return z;"));
        assertEquals(ArrayList.class, exec("boolean x = false; Object z = x ? new HashMap() : new ArrayList(); return z;").getClass());
    }

    public void testNullArguments() {
        assertEquals(null, exec("boolean b = false, c = true; Object x; Map y; return b && c ? x : y;"));
        assertEquals(HashMap.class,
                exec("boolean b = false, c = true; Object x; Map y = new HashMap(); return b && c ? x : y;").getClass());
    }

    public void testPromotion() {
        assertEquals(false, exec("boolean x = false; boolean y = true; return (x ? 2 : 4.0F) == (y ? 2 : 4.0F);"));
        assertEquals(false, exec("boolean x = false; boolean y = true; " +
            "return (x ? new HashMap() : new ArrayList()) == (y ? new HashMap() : new ArrayList());"));
    }

    public void testIncompatibleAssignment() {
        expectScriptThrows(ClassCastException.class, () -> {
            exec("boolean x = false; byte z = x ? 2 : 4.0F; return z;");
        });

        expectScriptThrows(ClassCastException.class, () -> {
            exec("boolean x = false; Map z = x ? 4 : (byte)7; return z;");
        });

        expectScriptThrows(ClassCastException.class, () -> {
            exec("boolean x = false; Map z = x ? new HashMap() : new ArrayList(); return z;");
        });

        expectScriptThrows(ClassCastException.class, () -> {
            exec("boolean x = false; int y = 2; byte z = x ? y : 7; return z;");
        });
    }

    public void testNested() {
        for (int i = 0; i < 100; i++) {
            String scriptPart = IntStream.range(0, i).mapToObj(j -> "field == '" + j + "' ? '" + j + "' :").collect(joining("\n"));
            assertEquals("z", exec("def field = params.a;\n" +
                "\n" +
                "return (\n" +
                scriptPart +
                "field == '' ? 'unknown' :\n" +
                "field);", Collections.singletonMap("a", "z"), true));
        }
    }
}
