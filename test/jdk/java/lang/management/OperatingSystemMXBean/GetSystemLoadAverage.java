/*
 * Copyright (c) 2005, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     6336608 6511738 6367473
 * @summary Basic unit test of OperatingSystemMXBean.getSystemLoadAverage()
 * @author  Mandy Chung
 *
 * @library /test/lib
 *
 * @run testng GetSystemLoadAverage
 */

/*
 * This test tests the load average on linux and solaris. On Windows,
 * getSystemLoadAverage() returns -1.
 *
 * Usage: GetSystemLoadAverage ["-1.0"]
 * Arguments:
 *   o If no argument is specified, the test will verify the system load
 *     average with the /usr/bin/uptime command.
 *   o Otherwise, the input argument must be "-1.0" indicating the
 *     expected system load average.  This would only be the case when
 *     running on Windows.
 */

import jdk.test.lib.Platform;
import org.testng.annotations.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

public class GetSystemLoadAverage {

    private static OperatingSystemMXBean mbean =
        ManagementFactory.getOperatingSystemMXBean();

    // The system load average may be changing due to other jobs running.
    // Allow some delta.
    private static final double DELTA = 0.05;

    private static final int MAX_RETRIES = 5;
    private static final int WAIT_TIME_MS = 5000;

    @Test(timeOut = (300 + WAIT_TIME_MS) * MAX_RETRIES)
    void testSystemLoadAvg() throws Exception {
        for (int i = 1; i <= MAX_RETRIES; i++) {
            try {
                System.out.println(String.format("Run %d: TestSystemLoadAvg", i));
                if (!Platform.isWindows()) {
                    // On Linux or Mac
                    checkLoadAvg();
                } else {
                    // On Windows, the system load average is expected to be -1.0
                    double loadavg = mbean.getSystemLoadAverage();
                    if (loadavg != -1.0) {
                        throw new RuntimeException("Expected load average : -1.0" +
                                " but getSystemLoadAverage returned: " + loadavg);
                    }
                }
                System.out.println(String.format("Run %d: TestSystemLoadAvg test passed", i));
                return;
            } catch (Exception e) {
                System.out.println(
                        String.format("TEST FAILED: TestSystemLoadAvg test " + "failed %d runs",
                                i));
                if (i == MAX_RETRIES) {
                    throw e;
                }

                System.out.println("Wait for 5 seconds");
                Thread.sleep(WAIT_TIME_MS);
            }
        }
    }

    private static String LOAD_AVERAGE_TEXT
            = System.getProperty("os.name").contains("OS X")
                ? "load averages:"
                : "load average:";

    private static void checkLoadAvg() throws Exception {
        // Obtain load average from OS command
        ProcessBuilder pb = new ProcessBuilder("/usr/bin/uptime");
        Process p = pb.start();
        String output = commandOutput(p);

        // obtain load average from OperatingSystemMXBean
        double loadavg = mbean.getSystemLoadAverage();

        // verify if two values are close
        output = output.substring(output.lastIndexOf(LOAD_AVERAGE_TEXT) +
                                  LOAD_AVERAGE_TEXT.length() + 1);
        System.out.println("Load average returned from uptime = " + output);
        System.out.println("getSystemLoadAverage() returned " + loadavg);

        String[] lavg = System.getProperty("os.name").contains("OS X")
                ? output.split(" ")
                : output.split(",");
        double expected = Double.parseDouble(lavg[0]);
        double lowRange = expected * (1 - DELTA);
        double highRange = expected * (1 + DELTA);

        if (loadavg < lowRange || loadavg >  highRange) {
            throw new RuntimeException("Expected load average : " +
                    expected +
                    " but getSystemLoadAverage returned: " +
                    loadavg);
        }
    }

    private static String commandOutput(Reader r) throws Exception {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = r.read()) > 0) {
            if (c != '\r') {
                sb.append((char) c);
            }
        }
        return sb.toString();
    }

    private static String commandOutput(Process p) throws Exception {
        Reader r = new InputStreamReader(p.getInputStream(),"UTF-8");
        String output = commandOutput(r);
        p.waitFor();
        p.exitValue();
        return output;
    }
}
