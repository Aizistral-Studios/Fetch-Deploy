package com.aizistral.fetchdeploy.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

public final class FDConfig {
    private static FDConfigMain common = null;
    private static FDConfigInternal internal = null;

    private FDConfig() {
        throw new IllegalStateException("Can't touch this");
    }

    public static FDConfigMain getMain() {
        return checkLoaded(() -> common);
    }

    public static FDConfigInternal getInternal() {
        return checkLoaded(() -> internal);
    }

    private static <T extends JSONConfig> T checkLoaded(Supplier<T> config) {
        if (config.get() == null) {
            load();
        }

        return config.get();
    }

    public static void load() {
        common = JSONConfig.loadConfig(FDConfigMain.class, FDConfigMain::new, FDConfigMain.FILE_NAME);
        internal = JSONConfig.loadConfig(FDConfigInternal.class, FDConfigInternal::new, FDConfigInternal.FILE_NAME);
        save();
    }

    public static void save() {
        checkLoaded(() -> common).saveFile();
        checkLoaded(() -> internal).saveFile();
    }

}
