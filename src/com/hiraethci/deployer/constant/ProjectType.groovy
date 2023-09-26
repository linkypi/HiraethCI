package com.hiraethci.deployer.constant

enum ProjectType {
    Npm("npm"),
    Maven("maven"),
    Gradle("gradle")

    private String name

    ProjectType(String value){
        this.name = value
    }

    static ProjectType of(String value){
        if(Npm.name.toUpperCase() == value.toUpperCase()){
            return Npm
        }
        if(Maven.name.toUpperCase() == value.toUpperCase()){
            return Maven
        }
        if(Gradle.name.toUpperCase() == value.toUpperCase()){
            return Gradle
        }
        return null
    }
}
