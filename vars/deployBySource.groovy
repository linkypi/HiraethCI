#!/usr/bin/env groovy
import com.hiraethci.deployer.DockerManager
import com.hiraethci.deployer.ProjectManager
import com.hiraethci.deployer.SourceBuilder
import com.hiraethci.deployer.Validator
import com.hiraethci.deployer.constant.DeployType
import com.hiraethci.deployer.constant.OperationType
import com.hiraethci.deployer.constant.ProjectType
import com.hiraethci.deployer.util.StringUtil
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
def call(args, events = null) {
    args = args == null ? [:] : args
    args.deployType = DeployType.BY_SOURCE
    args.opType = OperationType.DEPLOY

    runPipeline(args, { config ->
        runScript(args, config, events)
    })
}

def runScript(args, config, events) {
    println " start pipeline stage  !!!"
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

    runStage('create docker-compose file', {
        TemplateUtil.createDockerComposeFile(args, config)
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

    def (isCustomConfig, envVars) = TemplateUtil.useCustomConfig(args, config)
    // 若当前是前端项目，且指定了env baseUrl等参数则需要在 dist/static/js目录下生成 config.js
    if(args.frontend && isCustomConfig){
        runStage('create config.js', {
            TemplateUtil.createConfigFileForFrontend(args, envVars)
        })
    }

    def dockerArgs = [
            nexus        : config.nexus,
            network      : args.docker.network,
            version      : args.projectInfo.version,
            containerName: args.containerName,
            isRemoteHost : args.isRemoteHost,
            remote       : args.remote,
            context      : args.context,
            cicdPath: args.context.pwd() + config.cicdPath,
            hostname: args.docker.hostname
    ]
    runStage("stop remove container", {
        DockerManager.stopAndRemoveContainer(dockerArgs, args.projectInfo.projectName)
    })
    runStage("remove image", {
        DockerManager.removeImage(dockerArgs)
    })
    runStage("build image", {
        DockerManager.buildImage(dockerArgs)
    })
    runStage("push image", {
        DockerManager.pushImage(dockerArgs)
    })
    runStage("start container", {
        String env = StringUtil.isNullOrEmpty(args.env as String) ? "" : (args.env + "-")
        String projectName = "${env}${args.projectInfo.projectName}"
        DockerManager.startContainer(dockerArgs, projectName)
    })
}

static boolean getGenerateI18NMsg(generateI18NMsg){
    if( generateI18NMsg == true || generateI18NMsg == "true"){
        return true
    }
    return false
}
