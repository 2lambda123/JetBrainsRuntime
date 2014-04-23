/*
 * Copyright (c) 2002, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 4165985
 * @summary Determine the end of the first sentence using BreakIterator.
 * If the first sentence of "method" is parsed correctly, the test passes.
 * Correct Answer: "The class is empty (i.e. it has no members)."
 * Wrong Answer: "The class is empty (i.e."
 * @author jamieh
 * @library ../lib/
 * @build JavadocTester
 * @build TestBreakIterator
 * @run main TestBreakIterator
 */

public class TestBreakIterator extends JavadocTester {

    private static final String BUG_ID = "4165985";
    private static final String[][] TEST = {
        {BUG_ID + "/pkg/BreakIteratorTest.html",
            "The class is empty (i.e. it has no members)."}};
    private static final String[] ARGS =
        new String[] {
            "-d", BUG_ID, "-sourcepath", SRC_DIR,
            "-breakiterator", "pkg"};

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestBreakIterator tester = new TestBreakIterator();
        tester.run(ARGS, TEST, NO_TEST);
        tester.printSummary();
    }

    /**
     * {@inheritDoc}
     */
    public String getBugId() {
        return BUG_ID;
    }

    /**
     * {@inheritDoc}
     */
    public String getBugName() {
        return getClass().getName();
    }
}
