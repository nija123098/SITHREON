package com.nija123098.sithreon.backend.objects;

import com.nija123098.sithreon.backend.Config;
import com.nija123098.sithreon.backend.util.FileUtil;
import com.nija123098.sithreon.backend.util.Log;
import com.nija123098.sithreon.backend.util.StreamUtil;
import com.nija123098.sithreon.backend.util.throwable.SithreonException;

import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Collections;

/**
 * Represents the container environments that
 * a {@link Repository} can chose to run in.
 */
public enum BuildType {
    ALPINE("sithreon-runner:alpine") {
        @Override
        public String getDockerfileText() {
            return "FROM openjdk:8-jdk-alpine\n" +
                    super.getDockerfileText();
        }
    },
    UBUNTU("sithreon-runner:ubuntu") {
        @Override
        public String getDockerfileText() {
            return "FROM openjdk:8-jdk\n" +
                    super.getDockerfileText();
        }
    };

    private final String imageName;
    private boolean built;

    BuildType(String imageName) {
        this.imageName = imageName;
    }

    public String getContainer() {
        this.ensureImageBuild();
        return this.imageName;
    }

    public String getDockerfileText() {
        return "RUN mkdir -p /srv/sithreon/\n" +
                "WORKDIR /srv/sithreon/\n" +
                "COPY ./sithreon.jar ./sithreon.jar\n" +
                "COPY ./run.sh ./run.sh\n" +
                "COPY ./config.cfg ./config.cfg\n" +
                "CMD /bin/sh run.sh\n";
    }

    private boolean isImageBuilt() {
        try {
            Process process = new ProcessBuilder("docker", "image", "ls", "-q", this.imageName).start();
            process.waitFor();// Shouldn't block long.
            return (this.built = process.getInputStream().read() != -1);
        } catch (IOException e) {
            throw new SithreonException("IOException checking docker image existence: " + this.imageName, e);
        } catch (InterruptedException e) {
            throw new SithreonException("Inspected InterruptedException waiting for image check", e);
        }
    }

    /**
     * Check that the image this represents is built.
     */
    public void ensureImageBuild() {
        if (this.built) return;
        synchronized (this) {
            if (this.built) return;// Recheck in case a previous call built it while idle.
            if (this.isImageBuilt()) return;
            try {
                Log.INFO.log("Building Docker container: " + this.imageName);
                String buildPath = "tmp/image/" + this.imageName + "/";
                Path path = Paths.get(buildPath);
                Files.createDirectories(path);
                Files.write(Paths.get(buildPath, "Dockerfile"), Collections.singletonList(this.getDockerfileText()));
                Files.write(Paths.get(buildPath, "run.sh"), Arrays.asList("sleep 15", "java -jar sithreon.jar gc $COMPETITOR $AUTH_CODE"));
                Files.write(Paths.get(buildPath, "config.cfg"), Arrays.asList("gameServerAddress=" + Config.gameServerAddress, "internalPort=" + +Config.internalPort, "authenticateMachines=" + Config.authenticateMachines, "checkRepositoryValidity=false", "machineId=" + Config.machineId + "-runner", "standardAccessibleDomains=" + Config.gameServerAddress), StandardOpenOption.CREATE);
                Files.copy(Paths.get(FileUtil.getSithreonLocation()), Paths.get(buildPath, "sithreon.jar"), StandardCopyOption.REPLACE_EXISTING);
                Process process = new ProcessBuilder("docker", "build", "--tag", this.imageName, "--force-rm", buildPath).start();
                process.waitFor();
                if (!this.isImageBuilt()) {
                    Log.ERROR.log("Failed build for container: " + this.imageName + "\n" + StreamUtil.readFully(process.getInputStream(), 4096));
                }
                Log.INFO.log("Completed building Docker container: " + this.imageName);
            } catch (IOException e) {
                throw new SithreonException("IOException building docker existence: " + this.imageName, e);
            } catch (InterruptedException e) {
                throw new SithreonException("Inspected InterruptedException waiting for image check", e);
            }
        }
    }
}
