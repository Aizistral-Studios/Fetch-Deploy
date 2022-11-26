package com.aizistral.fetchdeploy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import com.aizistral.fetchdeploy.config.FDConfig;
import com.aizistral.fetchdeploy.config.JSONConfig;
import com.aizistral.fetchdeploy.misc.Lists;
import com.aizistral.fetchdeploy.misc.MutableBoolean;
import com.aizistral.fetchdeploy.misc.SimpleFileVisitor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import lombok.SneakyThrows;
import lombok.val;
import lombok.var;

public class FetchDeploy {
    public static final Path RUN_DIR = new File(".").toPath();
    public static final Path DOWNLOAD_DIR = RUN_DIR.resolve("download");
    public static final Path CONFIG_DIR = RUN_DIR.resolve("config");
    private static String stylesheetHash = null;

    public static void main(String... args) throws IOException {
        log("Initialization...");
        Files.createDirectories(DOWNLOAD_DIR);
        Files.createDirectories(CONFIG_DIR);

        val config = FDConfig.getMain();
        val internal = FDConfig.getInternal();

        String organization = checkConfig(config.getOrganization(), "Organization");
        String repository = checkConfig(config.getRepository(), "Repository");
        String branch = checkConfig(config.getBranch(), "Repository branch");
        String artifact = checkConfig(config.getArtifact(), "Artifact");
        String token = checkConfig(config.getAccessToken().orElse(""), "Github access token");
        Path deployPath = Paths.get(checkConfig(config.getDeployPath(), "Deploy path"));

        if (!Files.exists(deployPath)) {
            error("Deployment path " + deployPath + " does not exist");
            return;
        }

        boolean errorDocs = config.specialErrorDocs();
        Path errorDocsArchive = null;
        Path errorDocsDeploy = null;

        if (errorDocs) {
            errorDocsArchive = Paths.get(config.getErrorDocsArchivePath());
            errorDocsDeploy = Paths.get(config.getErrorDocsDeploymentPath());

            if (!Files.exists(errorDocsDeploy)) {
                error("Error docs deployment path " + errorDocsDeploy + " does not exist");
                return;
            }
        }

        if ("{DEFAULT}".equals(branch)) {
            log("Specified {DEFAULT} as target branch, fetching...");
            branch = fetchDefaultBranch(organization, repository);
            log("Name of default branch is: " + branch);
        }

        log("Initialization complete, entering main loop.");

        while (true) {
            try {
                debug("Start of cycle.");
                Optional<String> commit = internal.getLastCommit();
                String lastCommit = fetchLastCommit(organization, repository, branch);

                boolean deploy = commit.isPresent() ? !commit.get().equals(lastCommit) : true;

                if (deploy) {
                    val artifactURL = fetchArtifact(organization, repository, lastCommit, artifact);

                    if (artifactURL.isPresent()) {
                        log("Found artifact associated with last commit %1$s in branch %2$s, starting deployment...",
                                lastCommit, branch);
                        Path archive = DOWNLOAD_DIR.resolve(lastCommit + ".zip");
                        downloadArtifact(artifactURL.get(), archive, true);

                        log("Clearing deployment directories...");
                        clearDeploymentDirectory(deployPath);

                        if (errorDocs) {
                            clearDeploymentDirectory(errorDocsDeploy);
                        }

                        log("Started artifact extraction...");
                        deployFiles(archive, deployPath, errorDocs, errorDocsArchive, errorDocsDeploy);

                        if (config.insertStylesheetHash()) {
                            log("Looking for stylesheet file...");
                            hashStylesheet(deployPath, errorDocsDeploy);
                        }

                        log("Performing placeholder replacements...");
                        replacePlaceholders(deployPath, errorDocsDeploy, organization, repository, branch, lastCommit);

                        log("Clearing downloads...");
                        clearDeploymentDirectory(DOWNLOAD_DIR);

                        log("Updating last commit...");
                        internal.setLastCommit(lastCommit);
                        internal.saveFile();

                        log("Sucessfully completed deployment for commit %s.", lastCommit);
                    } else {
                        debug("No artifact found that matches last commit %1$s in branch %2$s, skipping deployment.",
                                lastCommit, branch);
                    }
                } else {
                    debug("Skipped deployment because we're up-to-date with last commit.");
                }

                debug("End of cycle, will wait " + config.getCycleDelayMillis() + " millis.");
            } catch (Exception ex) {
                error("Loop cycle failed with exception: ");
                ex.printStackTrace();
            }

            try {
                Thread.sleep(config.getCycleDelayMillis());
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private static String getMD5Hash(Path file) throws IOException {
        return DigestUtils.md5Hex(Files.newInputStream(file));
    }

    private static void hashStylesheet(Path deployPath, Path errorDocsPath) throws IOException {
        for (Path path : Lists.nullable(deployPath, errorDocsPath)) {
            Files.walkFileTree(path, SimpleFileVisitor.create(file -> {
                if (file.endsWith("assets/css/styles.css")) {
                    stylesheetHash = getMD5Hash(file);
                    log("Stylesheet MD5 hash: " + stylesheetHash);
                }
            }));
        }
    }

    private static void replacePlaceholders(Path deployPath, Path errorDocsPath, String organization, String repository, String branch, String commit) throws IOException {
        String repo = "https://github.com/" + organization + "/" + repository;
        String configPath = FDConfig.getMain().getStylesheetPath();
        String stylesheetPath = configPath.startsWith("/") ? configPath : "/" + configPath;

        for (Path path : Lists.nullable(deployPath, errorDocsPath)) {
            Files.walkFileTree(path, SimpleFileVisitor.create(file -> {
                if (file.getFileName().toString().endsWith(".html")) {
                    List<String> lines = Files.readAllLines(file);
                    MutableBoolean replaced = new MutableBoolean(false);

                    lines.replaceAll(line -> {
                        String newLine = line.replace("{$brc}", branch)
                                .replace("{$brcl}", repo + "/tree/" + branch)
                                .replace("{$com}", commit.substring(0, 7))
                                .replace("{$coml}", repo + "/commit/" + commit);

                        if (FDConfig.getMain().insertStylesheetHash() && stylesheetHash != null) {
                            newLine = newLine.replace(stylesheetPath, stylesheetPath + "?checksum=" + stylesheetHash);
                        }

                        if (!newLine.equals(line)) {
                            replaced.setValue(true);
                        }

                        return newLine;
                    });

                    if (replaced.getValue()) {
                        debug("Writing replacements into file " + file);
                        Files.write(file, lines);
                    }
                }
            }));
        }

        stylesheetHash = null;
    }

    private static void deployFiles(Path archive, Path deploy, boolean errorDocs, Path errorDocsArchive,
            Path errorDocsDeploy) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(archive))) {
            byte[] buffer = new byte[1024];
            ZipEntry zipEntry = zis.getNextEntry();

            while (zipEntry != null) {
                Path newFile = null;

                if (errorDocs && Paths.get(zipEntry.getName()).startsWith(errorDocsArchive)) {
                    Path entryPath = Paths.get(zipEntry.getName());

                    if (entryPath.equals(errorDocsArchive)) {
                        zis.closeEntry();
                        zipEntry = zis.getNextEntry();
                        continue;
                    }

                    newFile = newFile(errorDocsDeploy, errorDocsArchive.relativize(entryPath).toString());
                } else {
                    newFile = newFile(deploy, zipEntry.getName());
                }

                if (zipEntry.isDirectory()) {
                    if (!Files.isDirectory(newFile) && !newFile.toFile().mkdirs())
                        throw new IOException("Failed to create directory " + newFile);
                } else {
                    Path parent = newFile.getParent();

                    if (!Files.isDirectory(parent) && !parent.toFile().mkdirs())
                        throw new IOException("Failed to create directory " + parent);

                    try (OutputStream fos = Files.newOutputStream(newFile)) {
                        int len = 0;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }

                zis.closeEntry();
                zipEntry = zis.getNextEntry();
            }
        }
    }

    private static Path newFile(Path destination, String entryName) throws IOException {
        Path targetFile = destination.resolve(entryName);

        if (!targetFile.toFile().getCanonicalPath().startsWith(destination.toFile().getCanonicalPath() + File.separator))
            throw new IOException("Zip entry is outside of the target directory: " + entryName);

        return targetFile;
    }

    private static void clearDeploymentDirectory(Path deploy) throws IOException {
        List<Path> exclusions = FDConfig.getMain().getDeploymentDeletionExclusions().stream().map(deploy::resolve)
                .collect(Collectors.toList());

        Files.walkFileTree(deploy, SimpleFileVisitor.create(file -> {
            if (!exclusions.stream().anyMatch(file::equals)) {
                Files.delete(file);
            }
        }, dir -> {
            if (!deploy.equals(dir)) {
                Files.delete(dir);
            }
        }));
    }

    private static boolean downloadArtifact(URL url, Path file, boolean deleteFormer) throws IOException {
        if (Files.exists(file)) {
            if (deleteFormer) {
                Files.delete(file);
            } else {
                log("Skipping download from " + url + ", file already exists.");
                return true;
            }
        }

        URLConnection connection = url.openConnection();
        FDConfig.getMain().getAccessToken().ifPresent(token -> connection.setRequestProperty("Authorization",
                "Bearer " + token));

        try (ReadableByteChannel rbc = Channels.newChannel(connection.getInputStream());
                FileOutputStream fos = new FileOutputStream(file.toFile())) {
            log("Download started: " + url);
            Files.createDirectories(file.getParent());
            fos.getChannel().transferFrom(rbc, 0L, Long.MAX_VALUE);
            fos.close();
        } catch (Exception ex) {
            error("Failed to download: " + url);
            ex.printStackTrace(System.err);
            if (Files.exists(file)) {
                Files.delete(file);
            }
            return false;
        }

        log("Saved as: " + file.toString());
        return true;
    }

    private static String checkConfig(String value, String valueName) {
        return checkEmpty(value, valueName + " is not specified in config");
    }

    private static String checkEmpty(String value, String error) {
        if (value == null || value.isEmpty())
            throw new IllegalArgumentException(error);
        return value;
    }

    public static Optional<URL> fetchArtifact(String organization, String repository, String commit, String name) throws IOException {
        try {
            JsonObject obj = fetchJson("https://api.github.com/repos/" + organization + "/" + repository +
                    "/actions/artifacts").getAsJsonObject();
            JsonArray artifacts = obj.getAsJsonArray("artifacts");

            for (JsonElement element : artifacts.asList()) {
                JsonObject info = element.getAsJsonObject();

                if (!name.equals(info.get("name").getAsString()) || info.get("expired").getAsBoolean()) {
                    continue;
                }

                if (commit.equals(info.getAsJsonObject("workflow_run").get("head_sha").getAsString()))
                    return Optional.of(new URL(info.get("archive_download_url").getAsString()));
            }
        } catch (IllegalStateException | NullPointerException ex) {
            throw new IOException("Failed to fetch artifact from JSON", ex);
        }

        return Optional.empty();
    }

    public static String fetchLastCommit(String organization, String repository, String branch) throws IOException {
        try {
            JsonArray array = fetchJson("https://api.github.com/repos/" + organization + "/" + repository + "/commits?sha="
                    + branch).getAsJsonArray();
            return array.get(0).getAsJsonObject().get("sha").getAsString();
        } catch (IllegalStateException | NullPointerException ex) {
            throw new IOException("Failed to read commit array from JSON", ex);
        }
    }

    public static String fetchDefaultBranch(String organization, String repository) throws IOException {
        JsonObject obj = fetchRepositoryData(organization, repository);

        try {
            return obj.get("default_branch").getAsString();
        } catch (UnsupportedOperationException | IllegalStateException| NullPointerException ex) {
            throw new IOException("Failed to read default branch data from JSON", ex);
        }
    }

    public static JsonObject fetchRepositoryData(String organization, String repository) throws IOException {
        return fetchJson("https://api.github.com/repos/" + organization + "/" + repository).getAsJsonObject();
    }

    public static JsonElement fetchJson(String urlPath) throws IOException {
        try {
            return JsonParser.parseString(fetchResponse(urlPath));
        } catch (JsonSyntaxException ex) {
            throw new IOException("Invalid JSON response syntax", ex);
        }
    }

    public static String fetchResponse(String urlPath) throws IOException {
        URL url = new URL(urlPath);

        StringBuilder response = new StringBuilder(1000);
        URLConnection connection = url.openConnection();
        FDConfig.getMain().getAccessToken().ifPresent(token -> connection.setRequestProperty("Authorization",
                "Bearer " + token));

        try (Scanner scanner = new Scanner(connection.getInputStream())) {
            while(scanner.hasNext()) {
                response.append(scanner.next());
            }
        }

        return response.toString();
    }

    public static void error(String line, Object... arguments) {
        error(String.format(line, arguments));
    }

    public static void error(String line) {
        System.err.println(line);
    }

    public static void log(String line, Object... arguments) {
        log(String.format(line, arguments));
    }

    public static void log(String line) {
        System.out.println(line);
    }

    public static void debug(String line, Object... arguments) {
        if (FDConfig.getMain().enableDebugLog()) {
            log(String.format(line, arguments));
        }
    }

    public static void debug(String line) {
        if (FDConfig.getMain().enableDebugLog()) {
            System.out.println(line);
        }
    }

}
