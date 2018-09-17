package com.nija123098.sithreon.backend.objects;

public enum BuildType {
    JAVA_MAVEN,
    ;

    public String getContainer() {
        return "hello-world";// TODO write Dockerfile
    }
}
