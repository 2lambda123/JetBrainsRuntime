/*
 * Copyright (c) 2002, 2007, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package build.tools.generatenimbus;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

/**
 * Generates the various Java artifacts based on a SynthModel.
 * <p/>
 * Generated source files are split up among two different locations. There are those source files that are meant to be
 * edited (generally, only the LookAndFeel class itself) and those that are autogenerated (everything else).
 * <p/>
 * All autogenerated files are placed in "buildPackageRoot" and are package private. A LAF author (one who has access to
 * the generated sources) will be able to access any of the generated classes. Those referencing the library, however,
 * will only be able to access the main LookAndFeel class itself (since everything else is package private).
 *
 * @author  Richard Bair
 * @author  Jasper Potts
 */
public class Generator {
    private static Generator instance;

    /** A map of variables that are used for variable substitution in the template files. */
    private Map<String, String> variables;
    private boolean full = false;
    private File buildPackageRoot;
    private String packageNamePrefix;
    private String lafName;
    private SynthModel model;

    /**
     * MAIN APPLICATION
     * <p/>
     * This is for using the generator as part of the java build process
     *
     * @param args The commandline arguments
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0 || (args.length % 2) != 0) {
            System.out.println("Usage: generator [-options]\n" +
                    "    -full <true|false>     True if we should build the whole LAF or false for building just states and painters.\n" +
                    "    -skinFile <value>      Path to the skin.laf file for the LAF to be generated from.\n" +
                    "    -buildDir <value>      The directory beneath which the build-controlled artifacts (such as the Painters) should\n" +
                    "                           be placed. This is the root directory beneath which the necessary packages and source\n" +
                    "                           files will be created.\n" +
                    "    -resourcesDir <value>  The resources directory containing templates and images.\n" +
                    "    -packagePrefix <value> The package name associated with this synth look and feel. For example,\n" +
                    "                           \"org.mypackage.mylaf\"\n" +
                    "    -lafName <value>       The name of the laf, such as \"MyLAF\".\n");
        } else {
            boolean full = false;
            File skinFile = new File(System.getProperty("user.dir"));
            File buildDir = new File(System.getProperty("user.dir"));
            File resourcesDir = new File(System.getProperty("user.dir"));
            String packagePrefix = "org.mypackage.mylaf";
            String lafName = "MyLAF";
            for (int i = 0; i < args.length; i += 2) {
                String key = args[i].trim().toLowerCase();
                String value = args[i + 1].trim();
                if ("-full".equals(key)) {
                    full = Boolean.parseBoolean(value);
                } else if ("-skinfile".equals(key)) {
                    skinFile = new File(value);
                } else if ("-builddir".equals(key)) {
                    buildDir = new File(value);
                } else if ("-resourcesdir".equals(key)) {
                    resourcesDir = new File(value);
                } else if ("-packageprefix".equals(key)) {
                    packagePrefix = value;
                } else if ("-lafname".equals(key)) {
                    lafName = value;
                }
            }
            System.out.println("### GENERATING LAF CODE ################################");
            System.out.println("   full          :" + full);
            System.out.println("   skinFile      :" + skinFile.getAbsolutePath());
            System.out.println("   buildDir      :" + buildDir.getAbsolutePath());
            System.out.println("   resourcesDir  :" + resourcesDir.getAbsolutePath());
            System.out.println("   packagePrefix :" +packagePrefix);
            System.out.println("   lafName       :" +lafName);

            JAXBContext ctx = JAXBContext.newInstance("build.tools.generatenimbus");
            Unmarshaller u = ctx.createUnmarshaller();
            SynthModel model = (SynthModel) u.unmarshal(skinFile);
            Generator.init(full, buildDir, packagePrefix, lafName, model);
            Generator.getInstance().generate();
        }
    }

    /**
     * Creates a new Generator, capable of outputting the source code artifacts related to a given SynthModel. It is
     * capable of generating the one-time artifacts in addition to the regeneration of build-controlled artifacts.
     *
     * @param full              True if we should build the whole LAF or false for building just states and painters.
     * @param buildDir          The directory beneath which the build-controlled artifacts (such as the Painters) should
     *                          be placed. This is the root directory beneath which the necessary packages and source
     *                          files will be created.
     * @param srcDir            The directory beneath which the normal user-controlled artifacts (such as the core
     *                          LookAndFeel file) should be placed. These are one-time generated files. This is the root
     *                          directory beneath which the necessary packages and source files will be created.
     * @param packageNamePrefix The package name associated with this synth look and feel. For example,
     *                          org.mypackage.mylaf
     * @param lafName           The name of the laf, such as MyLAF.
     * @param model             The actual SynthModel to base these generated files on.
     */
    private Generator(boolean full, File buildDir,
            String packageNamePrefix, String lafName, SynthModel model) {
        this.full = full;
        //validate the input variables
        if (packageNamePrefix == null) {
            throw new IllegalArgumentException("You must specify a package name prefix");
        }
        if (buildDir == null) {
            throw new IllegalArgumentException("You must specify the build directory");
        }
        if (model == null) {
            throw new IllegalArgumentException("You must specify the SynthModel");
        }
        if (lafName == null) {
            throw new IllegalArgumentException("You must specify the name of the look and feel");
        }

        //construct the map which is used to do variable substitution of the template
        //files
        variables = new HashMap<String, String>();
        variables.put("PACKAGE", packageNamePrefix);
        variables.put("LAF_NAME", lafName);

        //generate and save references to the package-root directories.
        //(That is, given the buildDir and srcDir, generate references to the
        //org.mypackage.mylaf subdirectories)
        buildPackageRoot = new File(buildDir, packageNamePrefix.replaceAll("\\.", "\\/"));
        buildPackageRoot.mkdirs();

        //save the variables
        this.packageNamePrefix = packageNamePrefix;
        this.lafName = lafName;
        this.model = model;
    }

    public static void init(boolean full, File buildDir,
            String packageNamePrefix, String lafName, SynthModel model) {
        instance = new Generator(full, buildDir, packageNamePrefix, lafName, model);
        model.initStyles();
    }

    public static Generator getInstance() {
        return instance;
    }

    public static Map<String, String> getVariables() {
        return new HashMap<String, String>(instance.variables);
    }

    public void generate() {
        if (full) {
            //create the LookAndFeel file
            writeSrcFileImpl("LookAndFeel", variables, lafName + "LookAndFeel");

            writeSrcFileImpl("AbstractRegionPainter", variables);
            writeSrcFileImpl("BlendingMode", variables);
            writeSrcFileImpl("SynthPainterImpl", variables);
            writeSrcFileImpl("IconImpl", variables, lafName + "Icon.java");
            writeSrcFileImpl("StyleImpl", variables, lafName + "Style.java");
            writeSrcFileImpl("Effect", variables);
            writeSrcFileImpl("EffectUtils", variables);
            writeSrcFileImpl("ShadowEffect", variables);
            writeSrcFileImpl("DropShadowEffect", variables);
            writeSrcFileImpl("InnerShadowEffect", variables);
            writeSrcFileImpl("InnerGlowEffect", variables);
            writeSrcFileImpl("OuterGlowEffect", variables);
            writeSrcFileImpl("State", variables);
            writeSrcFileImpl("ImageCache", variables);
            writeSrcFileImpl("ImageScalingHelper", variables);
        }
        //next, populate the first set of ui defaults based on what is in the
        //various palettes of the synth model
        StringBuilder defBuffer = new StringBuilder();
        StringBuilder styleBuffer = new StringBuilder();
        model.write(defBuffer, styleBuffer, packageNamePrefix);

        Map<String, String> vars = getVariables();
        vars.put("UI_DEFAULT_INIT", defBuffer.toString());
        vars.put("STYLE_INIT", styleBuffer.toString());
        writeSrcFile("Defaults", vars, lafName + "Defaults");
    }

    private void writeSrcFileImpl(String name, Map<String, String> variables) {
        writeSrcFileImpl(name, variables, name);
    }

    private void writeSrcFileImpl(String templateName,
            Map<String, String> variables, String outputName) {
        PrintWriter out = null;
        try {
            InputStream stream = getClass().getResourceAsStream(
                    "resources/" + templateName + ".template");
            TemplateReader in = new TemplateReader(variables, stream);

            out = new PrintWriter(new File(buildPackageRoot, outputName + ".java"));
            String line = in.readLine();
            while (line != null) {
                out.println(line);
                line = in.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("IOException in writer", e);
        } finally {
            if (out != null) out.close();
        }
    }

    public static void writeSrcFile(String templateName,
            Map<String, String> variables, String outputName) {
        instance.writeSrcFileImpl(templateName, variables, outputName);
    }

    /** A BufferedReader implementation that automatically performs
     * string replacements as needed.
     */
    private static final class TemplateReader extends BufferedReader {
        private Map<String, String> variables;

        TemplateReader(Map<String, String> variables, InputStream template) {
            super(new InputStreamReader(template));
            this.variables = variables;
        }

        @Override public String readLine() throws IOException {
            return substituteVariables(super.readLine());
        }

        private String substituteVariables(String input) {
            if (input == null) return null;
            for (Map.Entry<String, String> variable : variables.entrySet()) {
                input = input.replace("${" + variable.getKey() + "}", variable.getValue());
            }
            return input;
        }
    }
}
