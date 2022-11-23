package com.aizistral.fetchdeploy.config;

import java.util.Optional;

import lombok.NonNull;

public class FDConfigInternal extends JSONConfig {
    protected static final String FILE_NAME = "internal.json";
    private String lastCommit = "";

    protected FDConfigInternal() {
        super(FILE_NAME);
    }

    @Override
    public FDConfigInternal getDefault() {
        return new FDConfigInternal();
    }

    public Optional<String> getLastCommit() {
        return this.lastCommit != null && !this.lastCommit.isEmpty() ? Optional.of(this.lastCommit) : Optional.empty();
    }

    public void setLastCommit(@NonNull String lastCommit) {
        this.lastCommit = lastCommit;
    }

}