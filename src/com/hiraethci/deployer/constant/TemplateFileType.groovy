package com.hiraethci.deployer.constant

/**
 * Description hiraethci-deployer
 * Created by troub on 2021/9/6 15:33
 */
enum TemplateFileType {
   Dockerfile("Dockerfile"),
   DockerCompose("docker-compose"),
   NginxConf("nginx"),
   ConfigJs("config");

    private String fileName

    public String getFileName(){
        return fileName
    }

    TemplateFileType(String name){
        this.fileName = name
    }
}