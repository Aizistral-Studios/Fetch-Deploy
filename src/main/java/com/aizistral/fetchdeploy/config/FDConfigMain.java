package com.aizistral.fetchdeploy.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.aizistral.fetchdeploy.misc.Lists;

public class FDConfigMain extends JSONConfig {
    protected static final String FILE_NAME = "config.json";
    private String organization = "", repository = "", branch = "{DEFAULT}", artifact = "Website", accessToken = "",
            deployPath = "./deploy", errorDocsArchivePath = "/errors", errorDocsDeploymentPath = "";
    private List<String> deploymentDeletionExclusions = Lists.create(".htaccess");
    private int cycleDelayMillis = 10000;
    private boolean debugLog = false;

    protected FDConfigMain() {
        super(FILE_NAME);
    }

    @Override
    public FDConfigMain getDefault() {
        return new FDConfigMain();
    }

    public String getOrganization() {
        return this.organization;
    }

    public String getRepository() {
        return this.repository;
    }

    public String getBranch() {
        return this.branch;
    }

    public String getArtifact() {
        return this.artifact;
    }

    public Optional<String> getAccessToken() {
        return this.accessToken != null && !this.accessToken.isEmpty() ? Optional.of(this.accessToken) : Optional.empty();
    }

    public int getCycleDelayMillis() {
        return this.cycleDelayMillis;
    }

    public boolean enableDebugLog() {
        return this.debugLog;
    }

    public String getDeployPath() {
        return this.deployPath;
    }

    public List<String> getDeploymentDeletionExclusions() {
        return Collections.unmodifiableList(this.deploymentDeletionExclusions);
    }

    public boolean specialErrorDocs() {
        return this.errorDocsArchivePath != null && this.errorDocsDeploymentPath != null &&
                !this.errorDocsArchivePath.isEmpty() && !this.errorDocsDeploymentPath.isEmpty();
    }

    public String getErrorDocsArchivePath() {
        return this.errorDocsArchivePath;
    }

    public String getErrorDocsDeploymentPath() {
        return this.errorDocsDeploymentPath;
    }

}
