/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.util.*;

/*
 * @test
 * @bug     6911258 6962571 6963622
 * @summary Basic tests of suppressed exceptions
 * @author  Joseph D. Darcy
 */

public class SuppressedExceptions {
    private static String message = "Bad suppressed exception information";

    public static void main(String... args) throws Exception {
        noSelfSuppression();
        basicSupressionTest();
        serializationTest();
        selfReference();
    }

    private static void noSelfSuppression() {
        Throwable throwable = new Throwable();
        try {
            throwable.addSuppressedException(throwable);
            throw new RuntimeException("IllegalArgumentException for self-suppresion not thrown.");
        } catch (IllegalArgumentException iae) {
            ; // Expected
        }
    }

    private static void basicSupressionTest() {
        Throwable throwable = new Throwable();
        RuntimeException suppressed = new RuntimeException("A suppressed exception.");
        AssertionError repressed  = new AssertionError("A repressed error.");

        Throwable[] t0 = throwable.getSuppressedExceptions();
        if (t0.length != 0) {
            throw new RuntimeException(message);
        }
        throwable.printStackTrace();

        throwable.addSuppressedException(suppressed);
        Throwable[] t1 = throwable.getSuppressedExceptions();
        if (t1.length != 1 ||
            t1[0] != suppressed) {throw new RuntimeException(message);
        }
        throwable.printStackTrace();

        throwable.addSuppressedException(repressed);
        Throwable[] t2 = throwable.getSuppressedExceptions();
        if (t2.length != 2 ||
            t2[0] != suppressed ||
            t2[1] != repressed) {
            throw new RuntimeException(message);
        }
        throwable.printStackTrace();
    }

    private static void serializationTest() throws Exception {
        /*
         * Bytes of the serial form of
         *
         * (new Throwable())setStackTrace(new StackTraceElement[0])
         *
         * from JDK 6; suppressedException field will be missing and
         * thus default to null upon deserialization.
         */
        byte[] bytes = {
            (byte)0xac, (byte)0xed, (byte)0x00, (byte)0x05, (byte)0x73, (byte)0x72, (byte)0x00, (byte)0x13,
            (byte)0x6a, (byte)0x61, (byte)0x76, (byte)0x61, (byte)0x2e, (byte)0x6c, (byte)0x61, (byte)0x6e,
            (byte)0x67, (byte)0x2e, (byte)0x54, (byte)0x68, (byte)0x72, (byte)0x6f, (byte)0x77, (byte)0x61,
            (byte)0x62, (byte)0x6c, (byte)0x65, (byte)0xd5, (byte)0xc6, (byte)0x35, (byte)0x27, (byte)0x39,
            (byte)0x77, (byte)0xb8, (byte)0xcb, (byte)0x03, (byte)0x00, (byte)0x03, (byte)0x4c, (byte)0x00,
            (byte)0x05, (byte)0x63, (byte)0x61, (byte)0x75, (byte)0x73, (byte)0x65, (byte)0x74, (byte)0x00,
            (byte)0x15, (byte)0x4c, (byte)0x6a, (byte)0x61, (byte)0x76, (byte)0x61, (byte)0x2f, (byte)0x6c,
            (byte)0x61, (byte)0x6e, (byte)0x67, (byte)0x2f, (byte)0x54, (byte)0x68, (byte)0x72, (byte)0x6f,
            (byte)0x77, (byte)0x61, (byte)0x62, (byte)0x6c, (byte)0x65, (byte)0x3b, (byte)0x4c, (byte)0x00,
            (byte)0x0d, (byte)0x64, (byte)0x65, (byte)0x74, (byte)0x61, (byte)0x69, (byte)0x6c, (byte)0x4d,
            (byte)0x65, (byte)0x73, (byte)0x73, (byte)0x61, (byte)0x67, (byte)0x65, (byte)0x74, (byte)0x00,
            (byte)0x12, (byte)0x4c, (byte)0x6a, (byte)0x61, (byte)0x76, (byte)0x61, (byte)0x2f, (byte)0x6c,
            (byte)0x61, (byte)0x6e, (byte)0x67, (byte)0x2f, (byte)0x53, (byte)0x74, (byte)0x72, (byte)0x69,
            (byte)0x6e, (byte)0x67, (byte)0x3b, (byte)0x5b, (byte)0x00, (byte)0x0a, (byte)0x73, (byte)0x74,
            (byte)0x61, (byte)0x63, (byte)0x6b, (byte)0x54, (byte)0x72, (byte)0x61, (byte)0x63, (byte)0x65,
            (byte)0x74, (byte)0x00, (byte)0x1e, (byte)0x5b, (byte)0x4c, (byte)0x6a, (byte)0x61, (byte)0x76,
            (byte)0x61, (byte)0x2f, (byte)0x6c, (byte)0x61, (byte)0x6e, (byte)0x67, (byte)0x2f, (byte)0x53,
            (byte)0x74, (byte)0x61, (byte)0x63, (byte)0x6b, (byte)0x54, (byte)0x72, (byte)0x61, (byte)0x63,
            (byte)0x65, (byte)0x45, (byte)0x6c, (byte)0x65, (byte)0x6d, (byte)0x65, (byte)0x6e, (byte)0x74,
            (byte)0x3b, (byte)0x78, (byte)0x70, (byte)0x71, (byte)0x00, (byte)0x7e, (byte)0x00, (byte)0x04,
            (byte)0x70, (byte)0x75, (byte)0x72, (byte)0x00, (byte)0x1e, (byte)0x5b, (byte)0x4c, (byte)0x6a,
            (byte)0x61, (byte)0x76, (byte)0x61, (byte)0x2e, (byte)0x6c, (byte)0x61, (byte)0x6e, (byte)0x67,
            (byte)0x2e, (byte)0x53, (byte)0x74, (byte)0x61, (byte)0x63, (byte)0x6b, (byte)0x54, (byte)0x72,
            (byte)0x61, (byte)0x63, (byte)0x65, (byte)0x45, (byte)0x6c, (byte)0x65, (byte)0x6d, (byte)0x65,
            (byte)0x6e, (byte)0x74, (byte)0x3b, (byte)0x02, (byte)0x46, (byte)0x2a, (byte)0x3c, (byte)0x3c,
            (byte)0xfd, (byte)0x22, (byte)0x39, (byte)0x02, (byte)0x00, (byte)0x00, (byte)0x78, (byte)0x70,
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x78, (byte)0xac, (byte)0xed, (byte)0x00,
            (byte)0x05, (byte)0x73, (byte)0x72, (byte)0x00, (byte)0x13, (byte)0x6a, (byte)0x61, (byte)0x76,
            (byte)0x61, (byte)0x2e, (byte)0x6c, (byte)0x61, (byte)0x6e, (byte)0x67, (byte)0x2e, (byte)0x54,
            (byte)0x68, (byte)0x72, (byte)0x6f, (byte)0x77, (byte)0x61, (byte)0x62, (byte)0x6c, (byte)0x65,
            (byte)0xd5, (byte)0xc6, (byte)0x35, (byte)0x27, (byte)0x39, (byte)0x77, (byte)0xb8, (byte)0xcb,
            (byte)0x03, (byte)0x00, (byte)0x03, (byte)0x4c, (byte)0x00, (byte)0x05, (byte)0x63, (byte)0x61,
            (byte)0x75, (byte)0x73, (byte)0x65, (byte)0x74, (byte)0x00, (byte)0x15, (byte)0x4c, (byte)0x6a,
            (byte)0x61, (byte)0x76, (byte)0x61, (byte)0x2f, (byte)0x6c, (byte)0x61, (byte)0x6e, (byte)0x67,
            (byte)0x2f, (byte)0x54, (byte)0x68, (byte)0x72, (byte)0x6f, (byte)0x77, (byte)0x61, (byte)0x62,
            (byte)0x6c, (byte)0x65, (byte)0x3b, (byte)0x4c, (byte)0x00, (byte)0x0d, (byte)0x64, (byte)0x65,
            (byte)0x74, (byte)0x61, (byte)0x69, (byte)0x6c, (byte)0x4d, (byte)0x65, (byte)0x73, (byte)0x73,
            (byte)0x61, (byte)0x67, (byte)0x65, (byte)0x74, (byte)0x00, (byte)0x12, (byte)0x4c, (byte)0x6a,
            (byte)0x61, (byte)0x76, (byte)0x61, (byte)0x2f, (byte)0x6c, (byte)0x6e, (byte)0x67, (byte)0x3b,
            (byte)0x61, (byte)0x6e, (byte)0x67, (byte)0x2f, (byte)0x53, (byte)0x74, (byte)0x72, (byte)0x69,
            (byte)0x5b, (byte)0x00, (byte)0x0a, (byte)0x73, (byte)0x74, (byte)0x61, (byte)0x63, (byte)0x6b,
            (byte)0x54, (byte)0x72, (byte)0x61, (byte)0x63, (byte)0x65, (byte)0x74, (byte)0x00, (byte)0x1e,
            (byte)0x5b, (byte)0x4c, (byte)0x6a, (byte)0x61, (byte)0x76, (byte)0x61, (byte)0x2f, (byte)0x6c,
            (byte)0x61, (byte)0x6e, (byte)0x67, (byte)0x2f, (byte)0x53, (byte)0x74, (byte)0x61, (byte)0x63,
            (byte)0x6b, (byte)0x54, (byte)0x72, (byte)0x61, (byte)0x63, (byte)0x65, (byte)0x45, (byte)0x6c,
            (byte)0x65, (byte)0x6d, (byte)0x65, (byte)0x6e, (byte)0x74, (byte)0x3b, (byte)0x78, (byte)0x70,
            (byte)0x71, (byte)0x00, (byte)0x7e, (byte)0x00, (byte)0x04, (byte)0x70, (byte)0x75, (byte)0x72,
            (byte)0x00, (byte)0x1e, (byte)0x5b, (byte)0x4c, (byte)0x6a, (byte)0x61, (byte)0x76, (byte)0x61,
            (byte)0x2e, (byte)0x6c, (byte)0x61, (byte)0x6e, (byte)0x67, (byte)0x2e, (byte)0x53, (byte)0x74,
            (byte)0x61, (byte)0x63, (byte)0x6b, (byte)0x54, (byte)0x72, (byte)0x61, (byte)0x63, (byte)0x65,
            (byte)0x45, (byte)0x6c, (byte)0x65, (byte)0x6d, (byte)0x65, (byte)0x6e, (byte)0x74, (byte)0x3b,
            (byte)0x02, (byte)0x46, (byte)0x2a, (byte)0x3c, (byte)0x3c, (byte)0xfd, (byte)0x22, (byte)0x39,
            (byte)0x02, (byte)0x00, (byte)0x00, (byte)0x78, (byte)0x70,
        };

        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(bais);

        Object o = ois.readObject();
        Throwable throwable = (Throwable) o;

        System.err.println("TESTING SERIALIZED EXCEPTION");

        Throwable[] t0 = throwable.getSuppressedExceptions();
        if (t0.length != 0) { // Will fail if t0 is null.
            throw new RuntimeException(message);
        }
        throwable.printStackTrace();
    }

    private static void selfReference() {
        Throwable throwable1 = new RuntimeException();
        Throwable throwable2 = new AssertionError();
        throwable1.initCause(throwable2);
        throwable2.initCause(throwable1);

        throwable1.printStackTrace();

        throwable1.addSuppressedException(throwable2);
        throwable2.addSuppressedException(throwable1);

        throwable1.printStackTrace();
    }
}
