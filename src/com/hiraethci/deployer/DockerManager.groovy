package com.hiraethci.deployer

public class DockerManager {
    private DockerManager() {}

    static def stopAndRemoveContainer(dockerArgs, projectName) {
        String command = getStopAndRemoveContainerCommand(dockerArgs.cicdPath, projectName)
        runCmd(dockerArgs, command)
    }

    static def removeImage(dockerArgs) {
        List<String> commands = getRemoveImageCommand(dockerArgs.containerName, dockerArgs.version, dockerArgs.nexus)
        commands.each {
            runCmd(dockerArgs, it)
        }
    }

    static def buildImage(dockerArgs) {
        String command = getBuildImageCommand(dockerArgs.cicdPath, dockerArgs.containerName, dockerArgs.version)
        runCmd(dockerArgs, command)
    }

    static def pushImage(dockerArgs) {
        List<String> commands = getPushImageCommands(dockerArgs.containerName, dockerArgs.version, dockerArgs.nexus)
        commands.each {
            runCmd(dockerArgs, it)
        }
    }

    static def pullImage(dockerArgs) {
        List<String> commands = getPullImageCommands(dockerArgs.image, dockerArgs.containerName, dockerArgs.version, dockerArgs.nexus)
        commands.each {
            runCmd(dockerArgs, it)
        }
    }

    static def startContainer(dockerArgs, projectName) {
        String createNetworkCommand = getCreateNetworkCommand(dockerArgs.network)
        runCmd(dockerArgs, createNetworkCommand)

        String command = getStartContainerCommand(dockerArgs.cicdPath, projectName)
        runCmd(dockerArgs, command)
    }

    private static void runCmd(dockerArgs, String command) {
        boolean isRemoteHost = dockerArgs.isRemoteHost
        def remote = dockerArgs.remote
        def context = dockerArgs.context
        if (isRemoteHost) {
            context.echo "run remote command \"${command}\""
            context.sshCommand remote: remote, command: command
        } else {
            context.echo "run local command \"${command}\""
            context.sh command
        }
    }

    public static String getStopAndRemoveContainerCommand(String cicdPath, String projectName) {
        String command = "docker-compose -p ${projectName} -f ${cicdPath}/docker-compose.yml down || true"
        return command
    }

    public static String getStartContainerCommand(String cicdPath, String projectName) {
        String command = "docker-compose -p ${projectName}  -f ${cicdPath}/docker-compose.yml up -d"
        return command
    }

    public static List<String> getRemoveImageCommand(String containerName, String version, nexus) {
        List<String> commands = new ArrayList<>()
        // 移除远程及本地镜像
        commands.add("docker rmi -f ${nexus.url}/${containerName}:${version} || true")
        commands.add("docker rmi -f ${containerName}:${version} || true")
        return commands
    }

    public static String getBuildImageCommand(String cicdPath, String containerName, String version) {
        String command = "docker build -f ${cicdPath}/Dockerfile -t ${containerName}:${version} . "
        return command
    }

    public static List<String> getPushImageCommands(String containerName, String version, nexus) {
        List<String> commands = new ArrayList<>()
        commands.add("docker login -u ${nexus.userName} -p ${nexus.password} ${nexus.url}")
        commands.add("docker tag ${containerName}:${version} ${nexus.url}/${containerName}:${version} || true")
        commands.add("docker push ${nexus.url}/${containerName}:${version} || true")
        return commands
    }

    /**
     *
     * @param imageName pull image ${nexus.url}/${imageName}:${version}
     * @param containerName tag local image to ${containerName}:${version}
     * @param version
     * @param nexus
     * @return
     */
    public static List<String> getPullImageCommands(String imageName, String containerName, String version, nexus) {

        List<String> commands = new ArrayList<>()
        commands.add("docker login -u ${nexus.userName} -p ${nexus.password} ${nexus.url}")
        if (imageName) {
            commands.add("docker pull ${nexus.url}/${imageName}:${version}")
            commands.add("docker tag ${nexus.url}/${imageName}:${version} ${containerName}:${version}")
            commands.add("docker rmi -f ${nexus.url}/${imageName}:${version} || true")
        } else {
            throw "Missing docker.image arg"
        }
        return commands
    }

    public static String getCreateNetworkCommand(String network) {
        String command = "docker network create ${network} || true"
        return command
    }

}



