package com.aizistral.fetchdeploy.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

import com.aizistral.fetchdeploy.FetchDeploy;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public abstract class JSONConfig {
    protected static final Path CONFIG_DIR = FetchDeploy.CONFIG_DIR;
    protected static final Gson GSON = new GsonBuilder().setPrettyPrinting()
            .setExclusionStrategies(new ExclusionStrategy() {
                @Override
                public boolean shouldSkipField(FieldAttributes field) {
                    return field.getDeclaringClass() == JSONConfig.class;
                }

                @Override
                public boolean shouldSkipClass(Class<?> theClass) {
                    return false;
                }
            }).create();

    protected final String fileName;
    protected final Path filePath;

    protected JSONConfig(String file) {
        this.fileName = file;
        this.filePath = CONFIG_DIR.resolve(this.fileName);
    }

    public Path getFile() {
        return this.filePath;
    }

    public void saveFile() {
        FetchDeploy.log("Writing config file %s...", this.fileName);
        writeFile(this.fileName, this);
    }

    protected void uponLoad() {
        // NO-OP
    }

    public abstract JSONConfig getDefault();

    public static <T extends JSONConfig> T loadConfig(Class<T> configClass, Supplier<T> freshInstance, String fileName) {
        FetchDeploy.log("Reading config file %s...", fileName);
        T config = readFile(fileName, configClass).orElseGet(freshInstance);
        config.uponLoad();
        return config;
    }

    private static <T extends JSONConfig> Optional<T> readFile(String fileName, Class<T> configClass) {
        Path file = CONFIG_DIR.resolve(fileName);

        if (!Files.isRegularFile(file))
            return Optional.empty();

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            return Optional.of(GSON.fromJson(reader, configClass));
        } catch (Exception ex) {
            FetchDeploy.error("Could not read config file: %s", file);
            FetchDeploy.error("This likely indicates the file is corrupted. "
                    + "You can try deleting it to fix this problem. Full stacktrace below:");
            ex.printStackTrace();
            return null;
        }
    }

    private static <T> void writeFile(String fileName, T config) {
        Path file = CONFIG_DIR.resolve(fileName);

        try {
            Files.createDirectories(file.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(file)) {
                GSON.toJson(config, writer);
            }
        } catch (Exception ex) {
            FetchDeploy.log("Could not write config file: %s", file);
            throw new RuntimeException(ex);
        }
    }

}
