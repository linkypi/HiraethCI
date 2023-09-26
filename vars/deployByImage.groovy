#!/usr/bin/env groovy
import com.hiraethci.deployer.DockerManager
import com.hiraethci.deployer.Validator
import com.hiraethci.deployer.constant.DeployType
import com.hiraethci.deployer.util.TemplateUtil

/**
 *
 * @param args  入参
 * @param events 事件
 *   events: [
 *       beforeCompile: { -> } // 编译源码前执行
 *       afterCompile: { -> } // 编译源码完成后执行
 *   ]
 * @return
 */
def call(args) {
    args = args == null ? [:] : args
    args.deployType = DeployType.BY_IMAGE
    runPipeline(args, { config ->
        // 校验参数合法性
        runStage("check args", {
            Validator.checkDeployWithImageArgs(args, config.isRemoteHost)
        })

        runStage('create docker-compose file', {
            // 若 docker 参数有指定 容器名称 containerName则直接获取（就近原则）
            // 否则检查 args是否有指定 projectName，有则取，没有则从配置文件中获取项目名称作为容器名称
            args.projectInfo = [
                    projectName: args.image,
                    version    : args.version
            ]
            // 由于后端需要指定默认内存大小，故此处需要判断前后端，当前仅通过镜像名称判断
            if (args.docker.image.contains("front")) {
                args.frontend = true
            } else {
                args.backend = true
            }
            TemplateUtil.createDockerComposeFile(args, config)
        })

        String cicdPath = args.context.pwd() + config.cicdPath
        if(config.isRemoteHost){
            cicdPath = args.dockerComposePath + "/" + args.containerName
        }

        def dockerArgs = [
                nexus        : config.nexus,
                image        : args.docker.image,
                network      : args.docker.network,
                version      : args.docker.version,
                containerName: args.containerName,
                isRemoteHost : config.isRemoteHost,
                remote       : args.remote,
                context: args.context,
                cicdPath: cicdPath,
                hostname: args.docker.hostname
        ]

//        String env = StringUtil.isNullOrEmpty(args.env as String) ? "" : (args.env + "-")
        String projectName = "${args.containerName}"

        runStage("stop remove container", {
            DockerManager.stopAndRemoveContainer(dockerArgs, projectName)
        })
        runStage("remove image", {
            DockerManager.removeImage(dockerArgs)
        })
        runStage("pull image", {
            DockerManager.pullImage(dockerArgs)
        })
        runStage("start container", {
            DockerManager.startContainer(dockerArgs, projectName)
        })
    })
}
