#!/usr/bin/env groovy
import com.hiraethci.deployer.DockerManager
import com.hiraethci.deployer.ProjectManager
import com.hiraethci.deployer.SourceBuilder
import com.hiraethci.deployer.Validator
import com.hiraethci.deployer.constant.ProjectType
import com.hiraethci.deployer.util.TemplateUtil

/**
 * 将源码编译为镜像
 * @param args  入参
 * @param events 事件
 * @param after 回调函数，可以在该回调函数中继续增加其他stage处理
 *   events: [
 *       beforeCompile: { -> } // 编译源码前执行
 *       afterCompile: { -> } // 编译源码完成后执行
 *   ]
 * @return
 */
def call(args, config, events) {
    pullCodeIfNecesary(config, args.url, args.branch)

    // 解析项目构建类型，名称，版本号
    runStage('parse project', {
        ProjectManager.parseProject(args, config)
    })

    // 校验参数合法性
    runStage("check args", {
        Validator.checkCommonArgs(args, config.isRemoteHost)
    })

    boolean customNginxFile = (args.customNginxConfFile == true || args.customNginxConfFile == 'true')
    args.customNginxConfFile = customNginxFile
    if(args.frontend && !customNginxFile){
        runStage('create nginx.conf', {
            TemplateUtil.createNginxConfFile(args, config)
        })
    }

    runStage('create dockerfile', {
        TemplateUtil.createDockerFileIfNotExist(args, config)
    })

    Closure beforeCompileEvent = null
    if (events) {
        beforeCompileEvent = events.beforeCompileEvent
    }
    args.generateI18NMsg = getGenerateI18NMsg(args.generateI18NMsg)

    if (args.generateI18NMsg) {
        runStage('build i18n', {
            new SourceBuilder(this, args.projectType as ProjectType).buildI18N()
        })
    }
    if(config.reviewCode) {
        runSonarQubeScan('sonarqube.30', args.projectType as ProjectType, this)
    }

    runStage('build source', {
        new SourceBuilder(this, args.projectType as ProjectType, beforeCompileEvent).build(args.docker.profile)
    })

    def envVars = TemplateUtil.getFrontendCustomConfig(args, config)
    // 若当前是前端项目，且指定了env baseUrl等参数则需要在 dist/static/js目录下生成 config.js
    if(args.frontend){
        runStage('create config.js', {
            TemplateUtil.createConfigFileForFrontend(args, envVars)
        })
    }

    args.commitId = args.context.sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
    args.imageTag = "${args.projectInfo.version}-${env.BUILD_NUMBER}-${args.commitId}"
    println("image tag: ${args.imageTag}")

    String containerName = TemplateUtil.getContainerName(config, args)
    args.containerName = containerName
    def dockerArgs = [
            nexus        : config.nexus,
            network      : args.docker.network,
            // 为了能够使得 argoCD 监听到每次部署后的变化 需要调整镜像的tag
            // 当前镜像的tag默认使用 version 来传递，故需要将version调整为每次编译相关的tag
            version      : args.imageTag,
            containerName: containerName,
            isRemoteHost : args.isRemoteHost,
            remote       : args.remote,
            context      : args.context,
            cicdPath: args.context.pwd() + config.cicdPath,
            hostname: args.docker.hostname
    ]
    runStage("remove image", {
        DockerManager.removeImage(dockerArgs)
    })
    runStage("build image", {
        println("docker current build dir: ")
        args.context.sh "pwd"
        DockerManager.buildImage(dockerArgs)
    })
    runStage("push image", {
        DockerManager.pushImage(dockerArgs)
    })

}

static boolean getGenerateI18NMsg(generateI18NMsg){
    if( generateI18NMsg == true || generateI18NMsg == "true"){
        return true
    }
    return false
}
