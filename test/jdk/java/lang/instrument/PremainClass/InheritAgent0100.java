/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6289149 8165276
 * @summary test config (0,1,0,0): 1-arg premain method in superclass of agent class must be rejected
 * @library /test/lib
 * @library /test
 * @modules java.instrument
 * @build jdk.java.lang.instrument.PremainClass.InheritAgent0100
 * @run driver jdk.test.lib.util.JavaAgentBuilder
 *             InheritAgent0100 InheritAgent0100.jar
 * @run main/othervm jdk.java.lang.instrument.NegativeAgentRunner InheritAgent0100 NoSuchMethodException
 */

public class InheritAgent0100 extends InheritAgent0100Super {

     // This agent does NOT have a single argument premain() method.
     // This agent does NOT have a double argument premain() method.

}

class InheritAgent0100Super {
    // This agent class has a single argument premain() method which should NOT be called.
    public static void premain (String agentArgs) {
        System.out.println("Hello from Single-Arg InheritAgent0100Super!");
        throw new Error("ERROR: THIS AGENT SHOULD NOT HAVE BEEN CALLED.");
    }

    // This agent class does NOT have a double argument premain() method.
}
