/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
 *
 */
// this file is auto-generated by ./CDSMHTest_generate.sh. Do not edit manually.

/*
 * @test
 * @summary Run the MethodHandlesPermuteArgumentsTest.java test in dynamic CDS archive mode.
 * @requires vm.cds & vm.compMode != "Xcomp"
 * @comment Some of the tests run excessively slowly with -Xcomp. The original
 *          tests aren't executed with -Xcomp in the CI pipeline, so let's exclude
 *          the generated tests from -Xcomp execution as well.
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 *          /test/hotspot/jtreg/runtime/cds/appcds/dynamicArchive
 *          /test/hotspot/jtreg/runtime/cds/appcds/dynamicArchive/test-classes
 * @compile ../../../../../../../jdk/java/lang/invoke/MethodHandlesTest.java
 *        ../../../../../../../lib/jdk/test/lib/Utils.java
 *        ../../../../../../../jdk/java/lang/invoke/MethodHandlesPermuteArgumentsTest.java
 *        ../../../../../../../jdk/java/lang/invoke/remote/RemoteExample.java
 *        ../../../../../../../jdk/java/lang/invoke/common/test/java/lang/invoke/lib/CodeCacheOverflowProcessor.java
 *        ../test-classes/TestMHApp.java
 * @build sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run junit/othervm/timeout=480 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. MethodHandlesPermuteArgumentsTest
 */

import org.junit.Test;

import java.io.File;
import jdk.test.lib.Platform;

public class MethodHandlesPermuteArgumentsTest extends DynamicArchiveTestBase {
    @Test
    public void test() throws Exception {
        runTest(MethodHandlesPermuteArgumentsTest::testImpl);
    }

    private static final String classDir = System.getProperty("test.classes");
    private static final String mainClass = "TestMHApp";
    private static final String javaClassPath = System.getProperty("java.class.path");
    private static final String ps = System.getProperty("path.separator");
    private static final String testPackageName = "test.java.lang.invoke";
    private static final String testClassName = "MethodHandlesPermuteArgumentsTest";

    static void testImpl() throws Exception {
        String topArchiveName = getNewArchiveName();
        String appJar = JarBuilder.build("MH", new File(classDir), null);
        // Disable VerifyDpendencies when running with debug build because
        // the test requires a lot more time to execute with the option enabled.
        String verifyOpt =
            Platform.isDebugBuild() ? "-XX:-VerifyDependencies" : "-showversion";

        String[] classPaths = javaClassPath.split(File.pathSeparator);
        String junitJar = null;
        for (String path : classPaths) {
            if (path.endsWith("junit.jar")) {
                junitJar = path;
                break;
            }
        }

        dumpAndRun(topArchiveName, "-Xlog:cds,cds+dynamic=debug,class+load=trace",
            "-cp", appJar + ps + junitJar, verifyOpt,
            mainClass, testPackageName + "." + testClassName);
    }
}
