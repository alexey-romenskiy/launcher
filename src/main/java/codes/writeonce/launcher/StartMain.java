package codes.writeonce.launcher;

import codes.writeonce.repository.Repository;
import codes.writeonce.repository.Resource;
import codes.writeonce.templates.Appender;
import codes.writeonce.templates.AppenderAppendable;
import codes.writeonce.templates.CharSequenceAppender;
import codes.writeonce.templates.Resolver;
import codes.writeonce.templates.TemplateParser;
import codes.writeonce.templates.TemplateResultWriter;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static codes.writeonce.launcher.Utils.getCommandPath;
import static codes.writeonce.launcher.Utils.getConfigProperties;
import static codes.writeonce.launcher.Utils.getPid;
import static codes.writeonce.launcher.Utils.getPidPath;
import static codes.writeonce.launcher.Utils.getTmpPath;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.joining;

public class StartMain {

    private static final String ENV_PREFIX = "env.";

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {

        final var profile = args[0];
        final var command = args[1];

        try (var repository = new Repository()) {

            final var pidPath = getPidPath(repository, profile, command);

            if (isAlive(pidPath)) {
                throw new IllegalStateException();
            }

            final var configPath = repository.launcherDir.resolve("profiles").resolve(profile).resolve("conf")
                    .resolve("config.properties");

            final var commandsPath = repository.launcherDir.resolve("profiles").resolve(profile).resolve("commands");
            final var cmdPath = commandsPath.resolve(command);
            final var cmd = Files.readString(cmdPath, UTF_8).trim();

            var configProperties = getConfigProperties(configPath);

            final var cmdConfPath = commandsPath.resolve(command + ".properties");
            if (Files.exists(cmdConfPath)) {
                final var cmdConfigProperties = getConfigProperties(cmdConfPath);
                for (final var name : cmdConfigProperties.stringPropertyNames()) {
                    if (configProperties.getProperty(name) != null) {
                        throw new IllegalArgumentException("Duplicate config property: " + name);
                    }
                }
                configProperties.putAll(cmdConfigProperties);
            }

            sanityCheck(configProperties);

            String commandArguments = null;
            Properties environmentProperties = null;
            Properties systemProperties = null;
            Properties attachmentsProperties = null;
            final var attachments = new HashMap<String, Resource>();
            final var dependencies = new ArrayList<Resource>();

            final var metaResource = repository.resolve(cmd);
            try (var fileInputStream = metaResource.getInputStream();
                 var xzInputStream = new XZCompressorInputStream(fileInputStream);
                 var tarInputStream = new TarArchiveInputStream(xzInputStream, UTF_8.name())) {
                while (true) {
                    final var entry = tarInputStream.getNextTarEntry();
                    if (entry == null) {
                        break;
                    }
                    if (entry.isFile()) {
                        final var entryName = entry.getName();
                        final byte[] bytes = read(tarInputStream, entry);
                        switch (entryName) {
                            case "dependencies" -> {
                                final var content = new String(bytes, UTF_8);
                                final var lines = Stream.of(content.split("\n", -1))
                                        .map(String::trim)
                                        .filter(e -> !e.isEmpty())
                                        .toList();
                                for (final var dependency : lines) {
                                    dependencies.add(repository.resolve(dependency));
                                }
                            }
                            case "commandArguments" -> {
                                if (commandArguments != null) {
                                    throw new IllegalArgumentException();
                                }
                                commandArguments = new String(bytes, UTF_8);
                            }
                            case "environment.properties" -> {
                                if (environmentProperties != null) {
                                    throw new IllegalArgumentException();
                                }
                                environmentProperties = getProperties(bytes);
                            }
                            case "system.properties" -> {
                                if (systemProperties != null) {
                                    throw new IllegalArgumentException();
                                }
                                systemProperties = getProperties(bytes);
                            }
                            case "attachments.properties" -> {
                                if (attachmentsProperties != null) {
                                    throw new IllegalArgumentException();
                                }
                                attachmentsProperties = getProperties(bytes);
                                sanityCheck(attachmentsProperties);
                                configProperties = resolvePropertiesRecursive(
                                        configProperties,
                                        attachmentsProperties,
                                        repository,
                                        attachments
                                );
                            }
                            default -> throw new IllegalArgumentException();
                        }
                    }
                }
            }
            if (commandArguments == null) {
                throw new IllegalArgumentException();
            }
            if (environmentProperties == null) {
                throw new IllegalArgumentException();
            }
            if (systemProperties == null) {
                throw new IllegalArgumentException();
            }
            if (attachmentsProperties == null) {
                throw new IllegalArgumentException();
            }
            systemProperties = resolveProperties(configProperties, systemProperties);
            commandArguments = resolveString(getCommandProperties(configProperties, dependencies, systemProperties,
                            asList(args).subList(2, args.length)),
                    commandArguments);
            environmentProperties = resolveProperties(configProperties, environmentProperties);

            final var javaHome = requireNonNull(configProperties.getProperty("java.home"));
            final var javaCmd = Path.of(javaHome, "bin", "java").toString();

            final var commandPath = getCommandPath(repository, profile, command);
            Files.createDirectories(commandPath);

            final var logPath = commandPath.resolve("log");
            Files.createDirectories(logPath);

            final var workPath = commandPath.resolve("work");
            Files.createDirectories(workPath);

            for (final var resource : attachments.values()) {
                final var cachePath = getCachePath(repository, resource);
                if (!Files.exists(cachePath)) {
                    final var extractor = getExtractor(resource.getPath().getFileName().toString());
                    final var tmpPath = getTmpPath(cachePath);
                    Files.createDirectories(tmpPath);
                    Files.walkFileTree(tmpPath, Collections.emptySet(), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            final var result = super.visitFile(file, attrs);
                            Files.delete(file);
                            return result;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                            final var result = super.postVisitDirectory(dir, exc);
                            Files.delete(dir);
                            return result;
                        }
                    });
                    Files.createDirectories(tmpPath);
                    extractor.extract(resource, tmpPath);
                    Files.move(tmpPath, cachePath, ATOMIC_MOVE, REPLACE_EXISTING);
                }
            }

            for (final var dependency : dependencies) {
                dependency.getCompletableFuture().get();
            }

            Files.createDirectories(pidPath.getParent());

            final Process process;
            if (Files.exists(Path.of("/dev/fd"))) {
                process =
                        commonStart(configProperties, environmentProperties, javaCmd, logPath, workPath, "@/dev/fd/0");
                try (var out = process.getOutputStream();
                     var writer = new OutputStreamWriter(out)) {
                    writer.write(commandArguments);
                }
            } else {
                final var argsPath = commandPath.resolve("args");
                try (var out = Files.newOutputStream(argsPath);
                     var writer = new OutputStreamWriter(out)) {
                    writer.write(commandArguments);
                }
                process = commonStart(configProperties, environmentProperties, javaCmd, logPath, workPath,
                        '@' + argsPath.toString());
                process.getOutputStream().close();
            }

            Files.writeString(pidPath, String.valueOf(process.pid()), UTF_8);
        }
    }

    @Nonnull
    private static Process commonStart(
            @Nonnull Properties configProperties,
            @Nonnull Properties environmentProperties,
            @Nonnull String javaCmd,
            @Nonnull Path logPath,
            @Nonnull Path workPath,
            @Nonnull String args
    ) throws IOException {

        final var time = configProperties.getProperty("time.fileName");
        final var pb = new ProcessBuilder(javaCmd, args);
        final var env = pb.environment();
        for (final var name : environmentProperties.stringPropertyNames()) {
            env.put(name, environmentProperties.getProperty(name));
        }
        pb.redirectOutput(Redirect.appendTo(logPath.resolve("output." + time + ".log").toFile()));
        pb.redirectErrorStream(true);
        pb.directory(workPath.toFile());
        return pb.start();
    }

    private static boolean isAlive(@Nonnull Path pidPath) throws IOException {

        final Long pid = getPid(pidPath);
        if (pid == null) {
            return false;
        }
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    @Nonnull
    private static Extractor getExtractor(@Nonnull String fileName) {

        if (fileName.endsWith(".tar.gz")) {
            return new TarGzExtractor();
        } else if (fileName.endsWith(".tar.xz")) {
            return new TarXzExtractor();
        } else if (fileName.endsWith(".tar.bz2")) {
            return new TarBz2Extractor();
        } else if (fileName.endsWith(".zip")) {
            return new ZipExtractor();
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Nonnull
    private static Path getCachePath(@Nonnull Repository repository, @Nonnull Resource resource) {
        return repository.launcherDir.resolve("cache").resolve(repository.repoPath.relativize(resource.getPath()));
    }

    @Nonnull
    private static Properties getCommandProperties(
            @Nonnull Properties configProperties,
            @Nonnull ArrayList<Resource> dependencies,
            @Nonnull Properties systemProperties,
            @Nonnull List<String> arguments
    ) {
        final var commandProperties = new Properties();
        commandProperties.putAll(configProperties);

        commandProperties.setProperty("systemProperties", systemProperties.stringPropertyNames().stream()
                .sorted()
                .map(n -> mapSystemProperty(systemProperties, n))
                .collect(joining(" ")));

        commandProperties.setProperty("classpath", dependencies.stream()
                .sorted(Comparator.comparing(Resource::getPath))
                .map(e -> escapeJavaArgSpecials(e.getPath().toString()))
                .collect(joining(File.pathSeparator, "\"", "\"")));

        commandProperties.setProperty("arguments", arguments.stream()
                .map(StartMain::escapeJavaArgSpecials)
                .collect(joining(" ")));

        return commandProperties;
    }

    @Nonnull
    private static String mapSystemProperty(Properties systemProperties2, String n) {
        return "\"-D" + escapeJavaArgSpecials(n) + "=" + escapeJavaArgSpecials(systemProperties2.getProperty(n)) + "\"";
    }

    @Nonnull
    private static String escapeJavaArgSpecials(@Nonnull String value) {
        final var builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            final var c = value.charAt(i);
            if (c < 32) {
                escapeCommon(builder, c);
            } else {
                switch (c) {
                    case '\\' -> builder.append("\\\\");
                    case '"' -> builder.append("\\\"");
                    default -> builder.append(c);
                }
            }
        }
        return builder.toString();
    }

    @Nonnull
    private static String escapeEnv(@Nonnull String value) {
        final var builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            final var c = value.charAt(i);
            if (c < 32) {
                escapeCommon(builder, c);
            } else {
                switch (c) {
                    case '\\' -> builder.append("\\\\");
                    case '\'' -> builder.append("\\'");
                    default -> builder.append(c);
                }
            }
        }
        return builder.toString();
    }

    private static void escapeCommon(@Nonnull StringBuilder builder, char c) {
        switch (c) {
            case '\n' -> builder.append("\\n");
            case '\r' -> builder.append("\\r");
            case '\t' -> builder.append("\\t");
            case '\f' -> builder.append("\\f");
            default -> throw new IllegalArgumentException();
        }
    }

    private static void sanityCheck(@Nonnull Properties properties) {
        if (properties.containsKey("systemProperties")) {
            throw new IllegalArgumentException();
        }
        if (properties.containsKey("classpath")) {
            throw new IllegalArgumentException();
        }
        if (properties.containsKey("time.fileName")) {
            throw new IllegalArgumentException();
        }
        if (properties.containsKey("arguments")) {
            throw new IllegalArgumentException();
        }
        for (final var name : properties.stringPropertyNames()) {
            if (name.startsWith(ENV_PREFIX)) {
                throw new IllegalArgumentException();
            }
        }
    }

    @Nonnull
    private static Properties getProperties(@Nonnull byte[] bytes) throws IOException {
        final var properties = new Properties();
        try (var in = new ByteArrayInputStream(bytes)) {
            properties.load(in);
        }
        return properties;
    }

    @Nonnull
    private static Properties resolveProperties(@Nonnull Properties configProperties, @Nonnull Properties properties) {
        final var p = new Properties();
        for (final var name : properties.stringPropertyNames()) {
            p.setProperty(name, resolveString(configProperties, properties.getProperty(name)));
        }
        return p;
    }

    @Nonnull
    private static byte[] read(@Nonnull TarArchiveInputStream tarInputStream, @Nonnull TarArchiveEntry entry)
            throws IOException {

        final var size = entry.getSize();
        final var buffer = new byte[0x1000000];
        final var byteArrayOutputStream = new ByteArrayOutputStream();
        var remained = size;
        while (remained != 0) {
            final var read = tarInputStream.read(buffer, 0, (int) Math.min(buffer.length, remained));
            if (read < 0) {
                throw new IllegalArgumentException();
            }
            remained -= read;
            byteArrayOutputStream.write(buffer, 0, read);
        }
        return byteArrayOutputStream.toByteArray();
    }

    @Nonnull
    private static Properties resolvePropertiesRecursive(
            @Nonnull Properties configProperties,
            @Nonnull Properties attachmentsProperties,
            @Nonnull Repository repository,
            @Nonnull Map<String, Resource> attachments
    ) throws IOException {

        for (final var name : attachmentsProperties.stringPropertyNames()) {
            if (configProperties.containsKey(name)) {
                throw new IllegalArgumentException();
            }
        }

        final var resolver = new Resolver<IOException>() {

            private final Map<String, Appender<IOException>> templates = new HashMap<>();

            private final LinkedHashSet<String> stack = new LinkedHashSet<>();

            @Override
            public void resolve(@Nonnull String name, @Nonnull AppenderAppendable<IOException> listener)
                    throws IOException {
                var template = templates.get(name);
                if (template == null) {
                    template = parse(name);
                    templates.put(name, template);
                }
                listener.append(template);
            }

            @Nonnull
            private Appender<IOException> parse(@Nonnull String name) throws IOException {

                if (name.startsWith(ENV_PREFIX)) {
                    return new CharSequenceAppender<>(
                            requireNonNullElse(System.getenv(name.substring(ENV_PREFIX.length())), ""));
                }

                final var configProperty = configProperties.getProperty(name);
                if (configProperty != null) {
                    return new CharSequenceAppender<>(parse(name, configProperty));
                }

                final var attachmentProperty = attachmentsProperties.getProperty(name);
                if (attachmentProperty != null) {
                    final var resource = repository.resolve(parse(name, attachmentProperty));
                    attachments.put(name, resource);
                    return new CharSequenceAppender<>(getCachePath(repository, resource).toString());
                }

                final var systemProperty = System.getProperty(name);
                if (systemProperty != null) {
                    return new CharSequenceAppender<>(systemProperty);
                }

                throw new IllegalArgumentException("Parameter \"" + name + "\" not defined");
            }

            @Nonnull
            private String parse(@Nonnull String name, @Nonnull String source) throws IOException {
                if (!stack.add(name)) {
                    throw new IllegalArgumentException("Template reference cycle detected: " + stack);
                }
                try {
                    final var builder = new StringBuilder();
                    new TemplateParser<IOException>()
                            .reset(this, new TemplateResultWriter<>(new StringBuilderAppendable<>(builder)))
                            .append(source)
                            .end();
                    return builder.toString();
                } finally {
                    stack.remove(name);
                }
            }
        };

        final Properties result = new Properties();

        for (final String name : configProperties.stringPropertyNames()) {
            resolve(resolver, result, name);
        }

        for (final String name : attachmentsProperties.stringPropertyNames()) {
            resolve(resolver, result, name);
        }

        result.setProperty("time.fileName",
                Instant.ofEpochMilli(System.currentTimeMillis()).toString().replace(':', '-').replace('.', '_'));

        return result;
    }

    private static void resolve(
            @Nonnull Resolver<IOException> resolver,
            @Nonnull Properties result,
            @Nonnull String name
    ) throws IOException {
        try {
            final var builder = new StringBuilder();
            resolver.resolve(name, new TemplateResultWriter<>(new StringBuilderAppendable<>(builder)));
            result.setProperty(name, builder.toString());
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to parse property name=" + name, e);
        }
    }

    @Nonnull
    private static String resolveString(@Nonnull Properties configProperties, @Nonnull String source) {

        final var builder = new StringBuilder();
        new TemplateParser<RuntimeException>()
                .reset((name, listener) -> listener.append(
                                new CharSequenceAppender<>(getValue(configProperties, name))),
                        new TemplateResultWriter<>(new StringBuilderAppendable<>(builder)))
                .append(source)
                .end();
        return builder.toString();
    }

    @Nonnull
    private static String getValue(@Nonnull Properties configProperties, String name) {

        if (name.startsWith(ENV_PREFIX)) {
            return requireNonNullElse(System.getenv(name.substring(ENV_PREFIX.length())), "");
        }

        final var configProperty = configProperties.getProperty(name);
        if (configProperty != null) {
            return configProperty;
        }

        final var systemProperty = System.getProperty(name);
        if (systemProperty != null) {
            return systemProperty;
        }

        throw new IllegalArgumentException("Parameter \"" + name + "\" not defined");
    }
}
