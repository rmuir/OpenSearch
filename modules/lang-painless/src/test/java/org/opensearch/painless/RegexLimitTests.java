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
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import org.opensearch.common.breaker.CircuitBreakingException;
import org.opensearch.common.settings.Settings;

import java.util.Collections;

public class RegexLimitTests extends ScriptTestCase {
    // This regex has backtracking due to .*?
    private final String pattern = "/abc.*?def/";
    private final String charSequence = "'abcdodef'";
    private final String splitCharSequence = "'0-abc-1-def-X-abc-2-def-Y-abc-3-def-Z-abc'";
    private final String regexCircuitMessage = "[scripting] Regular expression considered too many characters";

    public void testRegexInject_Matcher() {
        String[] scripts = new String[]{pattern + ".matcher(" + charSequence + ").matches()",
            "Matcher m = " + pattern + ".matcher(" + charSequence + "); m.matches()"};
        for (String script : scripts) {
            setRegexLimitFactor(2);
            assertEquals(Boolean.TRUE, exec(script));

            // Backtracking means the regular expression will fail with limit factor 1 (don't consider more than each char once)
            setRegexLimitFactor(1);
            CircuitBreakingException cbe = expectScriptThrows(CircuitBreakingException.class, () -> exec(script));
            assertTrue(cbe.getMessage().contains(regexCircuitMessage));
        }
    }

    public void testRegexInjectUnlimited_Matcher() {
        String[] scripts = new String[]{pattern + ".matcher(" + charSequence + ").matches()",
            "Matcher m = " + pattern + ".matcher(" + charSequence + "); m.matches()"};
        for (String script : scripts) {
            setRegexEnabled();
            assertEquals(Boolean.TRUE, exec(script));
        }
    }

    public void testRegexInject_Def_Matcher() {
        String[] scripts = new String[]{"def p = " + pattern + "; p.matcher(" + charSequence + ").matches()",
            "def p = " + pattern + "; def m = p.matcher(" + charSequence + "); m.matches()"};
        for (String script : scripts) {
            setRegexLimitFactor(2);
            assertEquals(Boolean.TRUE, exec(script));

            setRegexLimitFactor(1);
            CircuitBreakingException cbe = expectScriptThrows(CircuitBreakingException.class, () -> exec(script));
            assertTrue(cbe.getMessage().contains(regexCircuitMessage));
        }
    }

    public void testMethodRegexInject_Ref_Matcher() {
        String script =
            "boolean isMatch(Function func) { func.apply(" + charSequence +").matches(); } " +
                "Pattern pattern = " + pattern + ";" +
                "isMatch(pattern::matcher)";
        setRegexLimitFactor(2);
        assertEquals(Boolean.TRUE, exec(script));

        setRegexLimitFactor(1);
        CircuitBreakingException cbe = expectScriptThrows(CircuitBreakingException.class, () -> exec(script));
        assertTrue(cbe.getMessage().contains(regexCircuitMessage));
    }

    public void testRegexInject_DefMethodRef_Matcher() {
        String script =
            "boolean isMatch(Function func) { func.apply(" + charSequence +").matches(); } " +
                "def pattern = " + pattern + ";" +
                "isMatch(pattern::matcher)";
        setRegexLimitFactor(2);
        assertEquals(Boolean.TRUE, exec(script));

        setRegexLimitFactor(1);
        CircuitBreakingException cbe = expectScriptThrows(CircuitBreakingException.class, () -> exec(script));
        assertTrue(cbe.getMessage().contains(regexCircuitMessage));
    }

    public void testRegexInject_SplitLimit() {
        String[] scripts = new String[]{pattern + ".split(" + splitCharSequence + ", 2)",
            "Pattern p = " + pattern + "; p.split(" + splitCharSequence + ", 2)"};
        for (String script : scripts) {
            setRegexLimitFactor(2);
            assertArrayEquals(new String[]{"0-", "-X-abc-2-def-Y-abc-3-def-Z-abc"}, (String[])exec(script));

            setRegexLimitFactor(1);
            CircuitBreakingException cbe = expectScriptThrows(CircuitBreakingException.class, () -> exec(script));
            assertTrue(cbe.getMessage().contains(regexCircuitMessage));
        }
    }

    public void testRegexInjectUnlimited_SplitLimit() {
        String[] scripts = new String[]{pattern + ".split(" + splitCharSequence + ", 2)",
            "Pattern p = " + pattern + "; p.split(" + splitCharSequence + ", 2)"};
        for (String script : scripts) {
            setRegexEnabled();
            assertArrayEquals(new String[]{"0-", "-X-abc-2-def-Y-abc-3-def-Z-abc"}, (String[])exec(script));
        }
    }

    public void testRegexInject_Def_SplitLimit() {
        String script = "def p = " + pattern + "; p.split(" + splitCharSequence + ", 2)";
        setRegexLimitFactor(2);
        assertArrayEquals(new String[]{"0-", "-X-abc-2-def-Y-abc-3-def-Z-abc"}, (String[])exec(script));

        setRegexLimitFactor(1);
        CircuitBreakingException cbe = expectScriptThrows(CircuitBreakingException.class, () -> exec(script));
        assertTrue(cbe.getMessage().contains(regexCircuitMessage));
    }

    public void testRegexInject_Ref_SplitLimit() {
        String script =
            "String[] splitLimit(BiFunction func) { func.apply(" + splitCharSequence + ", 2); } " +
                "Pattern pattern = " + pattern + ";" +
                "splitLimit(pattern::split)";
        setRegexLimitFactor(2);
        assertArrayEquals(new String[]{"0-", "-X-abc-2-def-Y-abc-3-def-Z-abc"}, (String[])exec(script));

        setRegexLimitFactor(1);
        CircuitBreakingException cbe = expectScriptThrows(CircuitBreakingException.class, () -> exec(script));
        assertTrue(cbe.getMessage().contains(regexCircuitMessage));
    }

    public void testRegexInject_DefMethodRef_SplitLimit() {
        String script =
            "String[] splitLimit(BiFunction func) { func.apply(" + splitCharSequence + ", 2); } " +
                "def pattern = " + pattern + ";" +
                "splitLimit(pattern::split)";
        setRegexLimitFactor(2);
        assertArrayEquals(new String[]{"0-", "-X-abc-2-def-Y-abc-3-def-Z-abc"}, (String[])exec(script));

        setRegexLimitFactor(1);
        CircuitBreakingException cbe = expectScriptThrows(CircuitBreakingException.class, () -> exec(script));
        assertTrue(cbe.getMessage().contains(regexCircuitMessage));
    }

    public void testRegexInject_Split() {
        String[] scripts = new String[]{pattern + ".split(" + splitCharSequence + ")",
            "Pattern p = " + pattern + "; p.split(" + splitCharSequence + ")"};
        for (String script : scripts) {
            setRegexLimitFactor(2);
            assertArrayEquals(new String[]{"0-", "-X-", "-Y-", "-Z-abc"}, (String[])exec(script));

            setRegexLimitFactor(1);
            CircuitBreakingException cbe = expectScriptThrows(CircuitBreakingException.class, () -> exec(script));
            assertTrue(cbe.getMessage().contains(regexCircuitMessage));
        }
    }

    public void testRegexInjectUnlimited_Split() {
        String[] scripts = new String[]{pattern + ".split(" + splitCharSequence + ")",
            "Pattern p = " + pattern + "; p.split(" + splitCharSequence + ")"};
        for (String script : scripts) {
            setRegexEnabled();
            assertArrayEquals(new String[]{"0-", "-X-", "-Y-", "-Z-abc"}, (String[])exec(script));
        }
    }

    public void testRegexInject_Def_Split() {
        String script = "def p = " + pattern + "; p.split(" + splitCharSequence + ")";
        setRegexLimitFactor(2);
        assertArrayEquals(new String[]{"0-", "-X-", "-Y-", "-Z-abc"}, (String[])exec(script));

        setRegexLimitFactor(1);
        CircuitBreakingException cbe = expectScriptThrows(CircuitBreakingException.class, () -> exec(script));
        assertTrue(cbe.getMessage().contains(regexCircuitMessage));
    }

    public void testRegexInject_Ref_Split() {
        String script =
            "String[] split(Function func) { func.apply(" + splitCharSequence + "); } " +
                "Pattern pattern = " + pattern + ";" +
                "split(pattern::split)";
        setRegexLimitFactor(2);
        assertArrayEquals(new String[]{"0-", "-X-", "-Y-", "-Z-abc"}, (String[])exec(script));

        setRegexLimitFactor(1);
        CircuitBreakingException cbe = expectScriptThrows(CircuitBreakingException.class, () -> exec(script));
        assertTrue(cbe.getMessage().contains(regexCircuitMessage));
    }

    public void testRegexInject_DefMethodRef_Split() {
        String script =
            "String[] split(Function func) { func.apply(" + splitCharSequence +"); } " +
                "def pattern = " + pattern + ";" +
                "split(pattern::split)";
        setRegexLimitFactor(2);
        assertArrayEquals(new String[]{"0-", "-X-", "-Y-", "-Z-abc"}, (String[])exec(script));

        setRegexLimitFactor(1);
        CircuitBreakingException cbe = expectScriptThrows(CircuitBreakingException.class, () -> exec(script));
        assertTrue(cbe.getMessage().contains(regexCircuitMessage));
    }

    public void testRegexInject_SplitAsStream() {
        String[] scripts = new String[]{pattern + ".splitAsStream(" + splitCharSequence + ").toArray(String[]::new)",
            "Pattern p = " + pattern + "; p.splitAsStream(" + splitCharSequence + ").toArray(String[]::new)"};
        for (String script : scripts) {
            setRegexLimitFactor(2);
            assertArrayEquals(new String[]{"0-", "-X-", "-Y-", "-Z-abc"}, (String[]) exec(script));

            setRegexLimitFactor(1);
            CircuitBreakingException cbe = expectScriptThrows(CircuitBreakingException.class, () -> exec(script));
            assertTrue(cbe.getMessage().contains(regexCircuitMessage));
        }
    }

    public void testRegexInjectUnlimited_SplitAsStream() {
        String[] scripts = new String[]{pattern + ".splitAsStream(" + splitCharSequence + ").toArray(String[]::new)",
            "Pattern p = " + pattern + "; p.splitAsStream(" + splitCharSequence + ").toArray(String[]::new)"};
        for (String script : scripts) {
            setRegexEnabled();
            assertArrayEquals(new String[]{"0-", "-X-", "-Y-", "-Z-abc"}, (String[]) exec(script));
        }
    }

    public void testRegexInject_Def_SplitAsStream() {
        String script = "def p = " + pattern + "; p.splitAsStream(" + splitCharSequence + ").toArray(String[]::new)";
        setRegexLimitFactor(2);
        assertArrayEquals(new String[]{"0-", "-X-", "-Y-", "-Z-abc"}, (String[]) exec(script));

        setRegexLimitFactor(1);
        CircuitBreakingException cbe = expectScriptThrows(CircuitBreakingException.class, () -> exec(script));
        assertTrue(cbe.getMessage().contains(regexCircuitMessage));
    }

    public void testRegexInject_Ref_SplitAsStream() {
        String script =
            "Stream splitStream(Function func) { func.apply(" + splitCharSequence +"); } " +
                "Pattern pattern = " + pattern + ";" +
                "splitStream(pattern::splitAsStream).toArray(String[]::new)";
        setRegexLimitFactor(2);
        assertArrayEquals(new String[]{"0-", "-X-", "-Y-", "-Z-abc"}, (String[]) exec(script));

        setRegexLimitFactor(1);
        CircuitBreakingException cbe = expectScriptThrows(CircuitBreakingException.class, () -> exec(script));
        assertTrue(cbe.getMessage().contains(regexCircuitMessage));
    }

    public void testRegexInject_DefMethodRef_SplitAsStream() {
        String script =
            "Stream splitStream(Function func) { func.apply(" + splitCharSequence +"); } " +
                "def pattern = " + pattern + ";" +
                "splitStream(pattern::splitAsStream).toArray(String[]::new)";
        setRegexLimitFactor(2);
        assertArrayEquals(new String[]{"0-", "-X-", "-Y-", "-Z-abc"}, (String[]) exec(script));

        setRegexLimitFactor(1);
        CircuitBreakingException cbe = expectScriptThrows(CircuitBreakingException.class, () -> exec(script));
        assertTrue(cbe.getMessage().contains(regexCircuitMessage));
    }

    public void testRegexInjectFindOperator() {
        String script = "if (" + charSequence + " =~ " + pattern + ") { return 100; } return 200";
        setRegexLimitFactor(2);
        assertEquals(Integer.valueOf(100), (Integer) exec(script));

        setRegexLimitFactor(1);
        CircuitBreakingException cbe = expectScriptThrows(CircuitBreakingException.class, () -> exec(script));
        assertTrue(cbe.getMessage().contains(regexCircuitMessage));
    }

    public void testRegexInjectMatchOperator() {
        String script = "if (" + charSequence + " ==~ " + pattern + ") { return 100; } return 200";
        setRegexLimitFactor(2);
        assertEquals(Integer.valueOf(100), (Integer) exec(script));

        setRegexLimitFactor(1);
        CircuitBreakingException cbe = expectScriptThrows(CircuitBreakingException.class, () -> exec(script));
        assertTrue(cbe.getMessage().contains(regexCircuitMessage));
    }

    public void testSnippetRegex() {
        String charSequence = String.join("", Collections.nCopies(100, "abcdef123456"));
        String script = "if ('" + charSequence + "' ==~ " + pattern + ") { return 100; } return 200";

        setRegexLimitFactor(1);
        CircuitBreakingException cbe = expectScriptThrows(CircuitBreakingException.class, () -> exec(script));
        assertTrue(cbe.getMessage().contains(regexCircuitMessage));
        assertTrue(cbe.getMessage().contains(charSequence.subSequence(0, 61) + "..."));
    }

    private void setRegexLimitFactor(int factor) {
        Settings settings = Settings.builder().put(CompilerSettings.REGEX_LIMIT_FACTOR.getKey(), factor).build();
        scriptEngine = new PainlessScriptEngine(settings, scriptContexts());
    }

    private void setRegexEnabled() {
        Settings settings = Settings.builder().put(CompilerSettings.REGEX_ENABLED.getKey(), "true").build();
        scriptEngine = new PainlessScriptEngine(settings, scriptContexts());
    }
}
