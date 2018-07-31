package com.nija123098.sithreon.backend.objects;

import com.nija123098.sithreon.backend.Config;

import java.util.List;

public class RepositoryConfig {
    /**
     * If the repository should be run in the competition.
     */
    public Boolean disabled = false;

    /**
     * The build type of the repository.
     * If not supplied a best guess will be supplied or the repository will be considered disabled.
     */
    public BuildType buildType;

    RepositoryConfig(List<String> lines) {
        Config.setValues(this, lines);
        // predict BuildType
        if (this.buildType == null) this.disabled = true;
    }
}
