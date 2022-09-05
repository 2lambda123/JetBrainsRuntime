/*
 * Copyright 2000-2023 JetBrains s.r.o.
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardOpenOption.*;
import static java.util.regex.Pattern.compile;

/**
 * Codegen script for {@code jetbrains.api} module.
 * It produces "main" {@link com.jetbrains.JBR} class from template by
 * inspecting interfaces and implementation code and inserting
 * static utility methods for public services as well as some metadata
 * needed by JBR at runtime.
 */
public class Gensrc {

    private static Path srcroot, src, gensrc;
    private static String apiVersion;
    private static JBRModules modules;

    /**
     * <ul>
     *     <li>$0 - absolute path to {@code JetBrainsRuntime/src} dir</li>
     *     <li>$1 - absolute path to jbr-api output dir ({@code JetBrainsRuntime/build/<conf>/jbr-api})</li>
     *     <li>$2 - {@code JBR} part of API version</li>
     * </ul>
     */
    public static void main(String[] args) throws IOException {
        srcroot = Path.of(args[0]);
        Path module = srcroot.resolve("jetbrains.api");
        src = module.resolve("src");
        Path output = Path.of(args[1]);
        gensrc = output.resolve("gensrc");
        Files.createDirectories(gensrc);

        Properties props = new Properties();
        props.load(Files.newInputStream(module.resolve("version.properties")));
        apiVersion = args[2] + "." + props.getProperty("VERSION");
        Files.writeString(output.resolve("jbr-api.version"), apiVersion,
                CREATE, WRITE, TRUNCATE_EXISTING);

        modules = new JBRModules();
        generateFiles();
    }

    private static void generateFiles() throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    Path rel = src.relativize(file);
                    Path output = gensrc.resolve(rel);
                    Files.createDirectories(output.getParent());
                    String content = generateContent(file.getFileName().toString(), Files.readString(file));
                    Files.writeString(output, content, CREATE, WRITE, TRUNCATE_EXISTING);
                    return FileVisitResult.CONTINUE;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
    }

    private static String generateContent(String fileName, String content) throws IOException {
        return switch (fileName) {
            case "JBR.java" -> JBR.generate(content);
            default -> generate(content);
        };
    }

    private static String generate(String content) throws IOException {
        Pattern pattern = compile("/\\*CONST ((?:[a-zA-Z0-9]+\\.)+)([a-zA-Z0-9_*]+)\\s*\\*/");
        for (;;) {
            Matcher matcher = pattern.matcher(content);
            if (!matcher.find()) return content;
            String placeholder = matcher.group(0), file = matcher.group(1), name = matcher.group(2);
            file = file.substring(0, file.length() - 1).replace('.', '/') + ".java";
            List<String> statements = new ArrayList<>();
            for (Path module : modules.paths) {
                Path f = module.resolve("share/classes").resolve(file);
                if (Files.exists(f)) {
                    Pattern namePattern = compile(name.replaceAll("\\*", "\\\\w+"));
                    Pattern statementPattern = compile(
                            "((?:(?:MODS)  ){2,3})([a-zA-Z0-9]+)  (FIELD(?:, FIELD)*);"
                                    .replaceAll("MODS", "public|protected|private|static|final")
                                    .replaceAll("FIELD", "\\\\w+ = [\\\\w\"']+ ")
                                    .replaceAll("  ", "\\\\s+")
                                    .replaceAll(" ", "\\\\s*")
                    );
                    Matcher statementMatcher = statementPattern.matcher(Files.readString(f));
                    while (statementMatcher.find()) {
                        String mods = statementMatcher.group(1);
                        if (!mods.contains("static") || !mods.contains("final")) continue;
                        for (String s : statementMatcher.group(3).split(",")) {
                            s = s.strip();
                            String nm = s.substring(0, s.indexOf('=')).strip();
                            if (!namePattern.matcher(nm).matches()) continue;
                            statements.add("public static final " + statementMatcher.group(2) + " " + s + ";");
                        }
                    }
                    break;
                }
            }
            if (statements.isEmpty()) throw new RuntimeException("Constant not found: " + placeholder);
            content = replaceTemplate(content, placeholder, statements, true);
        }
    }

    private static String findRegex(String src, Pattern regex) {
        Matcher matcher = regex.matcher(src);
        if (!matcher.find()) throw new IllegalArgumentException("Regex not found: " + regex.pattern());
        return matcher.group(1);
    }

    private static String replaceTemplate(String src, String placeholder, Iterable<String> statements, boolean compact) {
        int placeholderIndex = src.indexOf(placeholder);
        int indent = 0;
        while (placeholderIndex - indent >= 1 && src.charAt(placeholderIndex - indent - 1) == ' ') indent++;
        int nextLineIndex = src.indexOf('\n', placeholderIndex + placeholder.length()) + 1;
        if (nextLineIndex == 0) nextLineIndex = placeholderIndex + placeholder.length();
        String before = src.substring(0, placeholderIndex - indent), after = src.substring(nextLineIndex);
        StringBuilder sb = new StringBuilder(before);
        boolean firstStatement = true;
        for (String s : statements) {
            if (!firstStatement && !compact) sb.append('\n');
            sb.append(s.indent(indent));
            firstStatement = false;
        }
        sb.append(after);
        return sb.toString();
    }

    /**
     * Code for generating {@link com.jetbrains.JBR} class.
     */
    private static class JBR {

        private static String generate(String content) {
            Service[] interfaces = findPublicServiceInterfaces();
            List<String> statements = new ArrayList<>();
            for (Service i : interfaces) statements.add(generateMethodsForService(i));
            content = replaceTemplate(content, "/*GENERATED_METHODS*/", statements, false);
            content = content.replace("/*KNOWN_SERVICES*/",
                    modules.services.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", ")));
            content = content.replace("/*KNOWN_PROXIES*/",
                    modules.proxies.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", ")));
            content = content.replace("/*API_VERSION*/", apiVersion);
            return content;
        }

        private static Service[] findPublicServiceInterfaces() {
            Pattern javadocPattern = Pattern.compile("/\\*\\*((?:.|\n)*?)\\s*\\*/");
            Pattern deprecatedPattern = Pattern.compile("@Deprecated( *\\(.*?forRemoval *= *true.*?\\))?");
            return modules.services.stream()
                    .map(fullName -> {
                        if (fullName.indexOf('$') != -1) return null; // Only top level services can be public
                        Path path = src.resolve(fullName.replace('.', '/') + ".java");
                        if (!Files.exists(path)) return null;
                        String name = fullName.substring(fullName.lastIndexOf('.') + 1);
                        try {
                            String content = Files.readString(path);
                            int indexOfDeclaration = content.indexOf("public interface " + name);
                            if (indexOfDeclaration == -1) return null;
                            Matcher javadocMatcher = javadocPattern.matcher(content.substring(0, indexOfDeclaration));
                            String javadoc;
                            int javadocEnd;
                            if (javadocMatcher.find()) {
                                javadoc = javadocMatcher.group(1);
                                javadocEnd = javadocMatcher.end();
                            } else {
                                javadoc = "";
                                javadocEnd = 0;
                            }
                            Matcher deprecatedMatcher = deprecatedPattern.matcher(content.substring(javadocEnd, indexOfDeclaration));
                            Status status;
                            if (!deprecatedMatcher.find()) status = Status.NORMAL;
                            else if (deprecatedMatcher.group(1) == null) status = Status.DEPRECATED;
                            else status = Status.FOR_REMOVAL;
                            return new Service(name, javadoc, status, content.contains("__Fallback"));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .filter(Objects::nonNull).toArray(Service[]::new);
        }

        private static String generateMethodsForService(Service service) {
            return """
                    private static class $__Holder {<DEPRECATED>
                        private static final $ INSTANCE = getService($.class, <FALLBACK>);
                    }
                    /**
                     * @return true if current runtime has implementation for all methods in {@link $}
                     * and its dependencies (can fully implement given service).
                     * @see #get$()
                     */<DEPRECATED>
                    public static boolean is$Supported() {
                        return $__Holder.INSTANCE != null;
                    }
                    /**<JAVADOC>
                     * @return full implementation of {@link $} service if any, or {@code null} otherwise
                     */<DEPRECATED>
                    public static $ get$() {
                        return $__Holder.INSTANCE;
                    }
                    """
                    .replace("<FALLBACK>", service.hasFallback ? "$.__Fallback::new" : "null")
                    .replaceAll("\\$", service.name)
                    .replace("<JAVADOC>", service.javadoc)
                    .replaceAll("<DEPRECATED>", service.status.text);
        }

        private enum Status {
            NORMAL(""),
            DEPRECATED("\n@Deprecated"),
            FOR_REMOVAL("\n@Deprecated(forRemoval=true)\n@SuppressWarnings(\"removal\")");

            private final String text;
            Status(String text) { this.text = text; }
        }

        private record Service(String name, String javadoc, Status status, boolean hasFallback) {}
    }

    /**
     * Finds and analyzes JBR API implementation modules and collects proxy definitions.
     */
    private static class JBRModules {

        private final Path[] paths;
        private final Set<String> proxies = new HashSet<>(), services = new HashSet<>();

        private JBRModules() throws IOException {
            String[] moduleNames = findJBRApiModules();
            paths = findPotentialJBRApiContributorModules();
            for (String moduleName : moduleNames) {
                Path module = findJBRApiModuleFile(moduleName, paths);
                findInModule(Files.readString(module));
            }
        }

        private void findInModule(String content) {
            Pattern servicePattern = compile("(service|proxy|twoWayProxy)\\s*\\(([^,)]+)");
            Matcher matcher = servicePattern.matcher(content);
            while (matcher.find()) {
                String type = matcher.group(1);
                String interfaceName = extractFromStringLiteral(matcher.group(2));
                if (type.equals("service")) services.add(interfaceName);
                else proxies.add(interfaceName);
            }
        }

        private static String extractFromStringLiteral(String value) {
            value = value.strip();
            return value.substring(1, value.length() - 1);
        }

        private static Path findJBRApiModuleFile(String module, Path[] potentialPaths) throws FileNotFoundException {
            for (Path p : potentialPaths) {
                Path m = p.resolve("share/classes").resolve(module + ".java");
                if (Files.exists(m)) return m;
            }
            throw new FileNotFoundException("JBR API module file not found: " + module);
        }

        private static String[] findJBRApiModules() throws IOException {
            String bootstrap = Files.readString(
                    srcroot.resolve("java.base/share/classes/com/jetbrains/bootstrap/JBRApiBootstrap.java"));
            Pattern modulePattern = compile("\"([^\"]+)");
            return Stream.of(findRegex(bootstrap, compile("MODULES *=([^;]+)")).split(","))
                    .map(m -> findRegex(m, modulePattern).replace('.', '/')).toArray(String[]::new);
        }

        private static Path[] findPotentialJBRApiContributorModules() throws IOException {
            return Files.list(srcroot)
                    .filter(p -> Files.exists(p.resolve("share/classes/com/jetbrains"))).toArray(Path[]::new);
        }
    }
}
