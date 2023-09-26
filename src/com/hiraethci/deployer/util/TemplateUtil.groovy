package com.hiraethci.deployer.util

import com.cloudbees.groovy.cps.NonCPS
import com.hiraethci.deployer.constant.DeployType
import com.hiraethci.deployer.constant.FrontendType
import com.hiraethci.deployer.constant.OperationType
import com.hiraethci.deployer.constant.PipelineType
import com.hiraethci.deployer.constant.ProjectType
import com.hiraethci.deployer.constant.TemplateFileType
import groovy.json.JsonOutput
import groovy.text.StreamingTemplateEngine

/**
 * Description hiraethci-deployer
 * Created by troub on 2021/9/4 14:26
 */
class TemplateUtil {
    static def createDockerComposeFile(args, config) {
        buildDockerComposeParams(args, config)

        def dockerArgs = args.docker == null ? [:] : args.docker
        dockerArgs.container = getContainerName(config, args)
        dockerArgs.imageName = getImageName(args, dockerArgs, config)
        dockerArgs.backend = args.backend
        dockerArgs.version = getVersion(config, args)
        dockerArgs.remote = args.remote
        dockerArgs.isRemoteHost = config.isRemoteHost
        dockerArgs.dockerComposePath = args.dockerComposePath

        args.containerName = dockerArgs.container

        String fileName = TemplateFileType.DockerCompose.getFileName()
        // 若主机名未指定则使用容器名作为主机名
        if (StringUtil.isNullOrEmpty(dockerArgs.hostname as String)) {
            dockerArgs.hostname = dockerArgs.container
            dockerArgs.serviceName = dockerArgs.container
        } else {
            // 若指定hostname则将 serviceName 设置为 hostname 否则会导致同一网段的主机无法连通
            dockerArgs.serviceName = dockerArgs.hostname
        }
        generateTemplateFile(config.cicdPath, args.context, dockerArgs, fileName, fileName, "yml")
    }

    static String getVersion(config, args) {
        if (config.pipelineType == PipelineType.FromSCM) {
            return args.projectInfo.version
        }
        return args.docker.version
    }

    static def createNginxConfFile(args, config) {
        def dockerArgs = args.docker == null ? [:] : args.docker
        if (args.service != null) {
            args.service.each({ k, v ->
                dockerArgs."${k}" = v
            })
        }

        dockerArgs.hasWS = false
        if (dockerArgs.backendWsService != null && dockerArgs.backendWsService != "") {
            dockerArgs.hasWS = true
        }
        if (args.nginx == null || StringUtil.isNullOrEmpty(args.nginx.proxyLocation as String)) {
            dockerArgs.proxyLocation = config.nginxDefaultProxyLocation
        } else {
            dockerArgs.proxyLocation = args.nginx.proxyLocation
        }

        dockerArgs.rewriteStaticPath = false

        def context = args.context
        context.echo "nginx config: ${args.nginx}"

        if (args.nginx == null || StringUtil.isNullOrEmpty(args.nginx.location as String)) {
            // 用户nginx配置为空则使用默认配置
            dockerArgs.location = config.nginx.location
        } else {
            dockerArgs.location = args.nginx.location
            if (args.nginx.rewriteStaticPath == true) {
                dockerArgs.rewriteStaticPath = true
            }
            dockerArgs.rewriteStaticContent = dockerArgs.rewriteStaticPath ? ("location ${dockerArgs.location}/static/ {alias /site/static/;}") : ""
        }

        String fileName = TemplateFileType.NginxConf.getFileName()
        // 若是通用web前端项目则使用 nginx.template， 否则根据指定前端类型取 nginx.${tag}.template 模板
        String templateName = fileName
        if (args.frontendType != FrontendType.WEB) {
            templateName = fileName + "." + (args.frontendType.getTag() as String)
        }

        generateTemplateFile(config.cicdPath, args.context, dockerArgs, fileName, fileName, "conf")
    }

    static String getContainerName(config, args) {
        // 若 docker 参数有指定 容器名称 containerName则直接获取（就近原则）
        // 否则检查 args是否有指定 projectName，有则取，没有则从配置文件中获取项目名称作为容器名称
        String env = StringUtil.isNullOrEmpty(args.env as String) ? "" : (args.env + "-")
        String containerName = args.docker.containerName
        if (!StringUtil.isNullOrEmpty(containerName)) {
            if (containerName.contains(env)) {
                return containerName
            }
            return "${env}${containerName}"
        }

        if (config.pipelineType == PipelineType.Script) {
            return "${args.docker.hostname}"
        }
        if (!StringUtil.isNullOrEmpty(args.projectName as String)) {
            return "${env}${args.projectName}"
        }
        return "${env}${args.projectInfo.projectName}"
    }

    /**
     *
     * @description Only for self-develop images
     * @param args
     * @param dockerArgs
     * @param config
     * @return ${dockerArgs.container}:${version}
     */
    private static String getImageName(args, dockerArgs, config) {
        String version = getVersion(config, args)
        return "${dockerArgs.container}:${version}";
    }

    static def createDockerFileIfNotExist(args, config) {
        // 是否使用自定义dockerfile，当前仅用于 online-preview 项目
        def context = args.context
        args.docker = args.docker == null ? [:] : args.docker
        String cicdPath = context.pwd() + config.cicdPath
        if (args.customDockerfile) {
            if (!args.context.fileExists("${cicdPath}/Dockerfile")) {
                throw new IllegalArgumentException("The Dockerfil not found in ${cicdPath} when specified argument customDockerfile !!!")
            }
            context.echo "Use custom Dockerfile: ${cicdPath}/Dockerfile"
            return
        }

        //若是前端项目则需要复制 entrypoint 里面的脚本文件到服务器
//        if(!args.backend) {
//            transferEntryPointFileToServer(config.cicdPath, args.context, args);
//        }
        buildDockerParams(args, config, context)
        String fileName = TemplateFileType.Dockerfile.getFileName()
        generateTemplateFile(config.cicdPath, args.context, args.docker, fileName, fileName)
    }

    private static void transferEntryPointFileToServer(cicdPath, context, args) {
        cicdPath = context.pwd() + cicdPath
        String filePath = "${cicdPath}/frontend-start.sh"
        // grant exec permission
        context.sh "chmod 755 ${filePath}"

        String content = context.libraryResource "com/hiraethci/templates/frontend-start.sh"
        context.writeFile file: "${filePath}", text: content

        //若是部署到远程主机 则将脚本复制到目标主机
        if (args.isRemoteHost) {
            // 确保路径已存在
            context.sshCommand remote: args.remote, command: "mkdir -p ${args.dockerComposePath} || true"

            String remoteFilePath = "${args.dockerComposePath}/${args.container}/frontend-start.sh"
            context.sshPut remote: args.remote, from: "${filePath}", into: "${remoteFilePath}", override: true
            context.echo "transfer frontend-start.sh to remote host success: ${remoteFilePath}"
        }
    }

    private static void buildDockerParams(args, config, context) {
        def dockerArgs = args.docker
        if (StringUtil.isNullOrEmpty(dockerArgs.baseImage as String)) {
            dockerArgs.baseImage = args.backend ? config.defaultImage.backend : config.defaultImage.frontend
        }
        dockerArgs.commands = new ArrayList<String>()
        def cicdPath = context.pwd() + config.cicdPath
        if (args.backend) {
            if (args.projectType == ProjectType.Maven) {
                // 不包含源码 jar
                dockerArgs.commands.add("COPY ${config.srcPath}target/*[^source].jar /${config.defaultJarName}")
            }
            if (args.projectType == ProjectType.Gradle) {
                dockerArgs.commands.add("COPY ${config.srcPath}build/libs/*.jar /${config.defaultJarName}")
            }

            dockerArgs.entryPoint = "\"/bin/sh\",\"-c\",\"java \$JVM_OPTS -Dspring.profiles.active=\${profile}"
            // 配置文件需要配置在jar包外，而非直接使用jar包内部的配置文件
            if (args.opType == OperationType.BUILD) {
                dockerArgs.entryPoint += " -Dspring.config.additional-location=${config.springConfigAdditionalLocation}"
            }
            if (dockerArgs.enableRemoteDebug) {
                dockerArgs.entryPoint += " ${config.dockerDebugArgs}${config.defaultDockerRemoteDebugPort} "
            }
            dockerArgs.entryPoint += " -jar /${config.defaultJarName}\" "
        } else {
            dockerArgs.commands.add("COPY ${config.srcPath}dist /site")
            if (args.opType == OperationType.DEPLOY) {
                dockerArgs.commands.add("COPY ${config.srcPath}cicd/nginx.conf /etc/nginx/nginx.conf")
            }
//            dockerArgs.commands.add("COPY ${config.srcPath}/cicd/frontend-start.sh /frontend-start.sh")
            dockerArgs.entryPoint = ""
        }
    }

    private static void buildDockerComposeParams(args, config) {

        boolean isBackend = args.backend
        def dockerArgs = args.docker
        def context = args.context
        String cicdPath = context.pwd() + config.cicdPath

        if (dockerArgs.volumes == null) {
            dockerArgs.volumes = []
        }

        if (dockerArgs.environments == null) {
            dockerArgs.environments = []
        }
        if (!StringUtil.isNullOrEmpty(dockerArgs.profile as String)) {
            dockerArgs.environments.add("profile=${dockerArgs.profile}")
        }

        if (StringUtil.isNullOrEmpty(dockerArgs.restart as String)) {
            dockerArgs.restart = "\'always\'"
        }

        checkRemoteDebugArgs(dockerArgs, config, context)

        dockerArgs.command = ""

        if (isBackend) {
            prepareJvmOptions(dockerArgs, config, context)
            dockerArgs.environments.add("LOG_PATH=./logs")
            dockerArgs.containerLogPath = config.defaultContainerLogPath.backend
        } else {
            dockerArgs.containerLogPath = config.defaultContainerLogPath.frontend
            // 若是使用源码部署则需自动生成nginx.conf ，使用镜像部署则无需生成，因为前面的镜像已经包含了该配置文件
            if (args.deployType == DeployType.BY_SOURCE && !args.customNginxConfFile) {
                String nginxConfigPath = "${cicdPath}/nginx.conf"
                dockerArgs.volumes.add("${nginxConfigPath}:/etc/nginx/nginx.conf")
                context.echo "Auto generate nginx.conf file : ${nginxConfigPath}, and add to docker volumns."
            }
        }

        dockerArgs.hasEnvs = dockerArgs.environments.size() > 0
    }

    private static void checkRemoteDebugArgs(dockerArgs, config, context) {
        dockerArgs.enableRemoteDebug = dockerArgs.enableRemoteDebug == null ? config.enableRemoteDebug : dockerArgs.enableRemoteDebug
        if (dockerArgs.enableRemoteDebug) {
            String debugPort = config.defaultDockerRemoteDebugPort
            boolean exist = false;
            // 若已开启远程调试，同时指定端口则以用户指定的端口为主
            if (dockerArgs.ports != null) {
                for (String item in dockerArgs.ports) {
                    if (!StringUtil.isNullOrEmpty(item) && item.contains(debugPort)) {
                        String[] arr = item.split(':')
                        if (arr.length > 1 && arr[1] == (debugPort)) {
                            exist = true
                            break
                        }
                    }
                }
            }
            if (!exist) {
                // 查找宿主机可用端口
                int availablePort = NetUtil.getAvailablePort(context, config.defaultDockerRemoteDebugPort as int)
                dockerArgs.ports.add("${availablePort}:${debugPort}")
            }
        }
    }

    private static void prepareJvmOptions(dockerArgs, config, context) {
        // 判断是否有指定 JVM 参数 JVM_OPTS
        def customJvmOpt = false
        def index = 0
        context.echo "prepare JvmOptions..., environments: ${JsonOutput.toJson(dockerArgs.environments)}"

        for (env in dockerArgs.environments) {
            if (!env.contains("JVM_OPTS")) {
                index += 1
                continue
            }
            customJvmOpt = true
            context.echo "customJvmOpt: " + customJvmOpt
            // 判断用户指定的jvm参数是否已经包含插件默认的jvm参数
            // 不包含则添加
            for (opt in config.defaultJavaOpts) {
                if (!env.contains(opt.flag.toString())) {
                    env += " " + opt.flag.toString()
                    if (opt.connector) {
                        env += opt.connector.toString()
                    }
                    if (opt.value) {
                        env += opt.value.toString() + " "
                    }
                }
            }

            dockerArgs.environments[index] = env
            context.echo "merge JvmOptions: " + dockerArgs.environments[index]
            break
        }

        if (!customJvmOpt) {
            dockerArgs.environments.add("JVM_OPTS=" + config.defaultJvm.toString())
            context.echo "custom JvmOptions not specified, use default instead: " + config.defaultJvm.toString()
        }
        context.echo "after prepare JvmOptions..., environments: ${JsonOutput.toJson(dockerArgs.environments)}"
    }

    private static void generateTemplateFile(cicdPath, context, args, String templateName, String fileName, String suffix = "") {

        cicdPath = context.pwd() + cicdPath

        def binding = new HashMap(args)
        String json = JsonOutput.toJson(binding)
        context.echo " Template file json [${templateName}.template 模板文件参数]: ${json}"

        String filePath = "${cicdPath}/${fileName}"

        String content = context.libraryResource "com/hiraethci/templates/${templateName}.template"
        def text = parseTemplate(content, binding)
        context.writeFile file: "${filePath}.tmp", text: text

        // 去除文件多余空白行
        if (StringUtil.isNullOrEmpty(suffix)) {
            context.sh "awk NF ${filePath}.tmp > ${filePath}"
        } else {
            context.sh "awk NF ${filePath}.tmp > ${filePath}.${suffix}"
            // 如果是nginx配置文件则需要对 \$\\ 做 特殊处理
            if (fileName.contains("nginx")) {
                context.sh "sed -i 's/\\\\\$\\\\\\\\/\$/g' ${filePath}.${suffix}"
            }
        }
        // 删除临时文件
        context.sh "rm -fr ${filePath}.tmp"

        //若是部署到远程主机 则将脚本复制到目标主机
        if (args.isRemoteHost) {
            // 确保路径已存在
            context.sshCommand remote: args.remote, command: "mkdir -p ${args.dockerComposePath} || true"

            String remoteFilePath = "${args.dockerComposePath}/${args.container}/docker-compose.yml"
            context.sshPut remote: args.remote, from: "${filePath}.${suffix}", into: "${remoteFilePath}", override: true
            context.echo "transfer docker-compose.yml to remote host success: ${remoteFilePath}"
        }
    }

    /**
     * 此处增加注解是为了解决 java.io.NotSerializableException 的问题，
     * 解决方案参考自 ： https://stackoverflow.com/questions/59526894/groovy-streamingtemplateengine-gives-error-with-withcredentials-function
     * @param text
     * @param bindings
     * @return
     */
    @NonCPS
    private static String parseTemplate(String text, Map bindings) {
        return new StreamingTemplateEngine().createTemplate(text).make(bindings).toString()
    }

    /**
     * 获取前端配置，用于生成 config.js
     * @param args
     * @param config
     * @return
     */
    static def getFrontendCustomConfig(args, config) {
        args.docker = args.docker == null ? [:] : args.docker
        if (StringUtil.isNullOrEmpty(args.docker.profile as String)) {
            args.docker.profile = config.defaultH5Config.defaultEnv
        }

        def (isCustomConfig, envVars) = useCustomConfig(args, config)
        return envVars
    }

    /**
     * 检测前端配置，用于生成 config.js
     * @param args
     * @param config
     * @return
     */
    static def useCustomConfig(args, config) {
        def isCustomConfig = false
        def temp = config.defaultFrontendConfig
        args.context.echo "check frontend custom config: ${JsonOutput.toJson(args.docker.environments)}"
        if (!StringUtil.isNullOrEmpty(args.docker.profile as String)) {
            isCustomConfig = true
            temp.env = args.docker.profile
        }

        for (item in args.docker.environments) {
            if (!item.contains("=")) {
                continue
            }
            def arr = item.split("=")
            if (arr.size() < 2) {
                continue
            }
            String key = arr[0]
            String value = arr[1]

            temp."${key}" = value
        }

        // 若 docker 内部未通过 profile 指定后端得执行环境 则首先通过 args.env 参数获取
        // 若 args.env 同样未设置则使用默认值 presit
        if (StringUtil.isNullOrEmpty(temp.env)) {
            temp.env = StringUtil.isNullOrEmpty(args.env) ? config.defaultH5Config.defaultEnv : args.env
        }

        List<String> list = new ArrayList<>();
        // env: '${env}',
        temp.each { key, value ->
            list.add("${key}: '${value}'")
        }
        for (int i = 0; i < list.size(); i++) {
            String item = list.get(i)
            if (i != list.size() - 1 && !StringUtil.isNullOrEmpty(item)) {
                list.set(i, item + ",")
            }
        }
        list = list.findAll { a -> !StringUtil.isNullOrEmpty(a) }
        return [isCustomConfig, [props: list]]
    }

    static def createConfigFileForFrontend(args, envVars) {
        // 在 index.html 文件中插入配置文件脚本
        def script = "<script src=\""

        String path = ""
        if (args != null && args.nginx != null && !StringUtil.isNullOrEmpty(args.nginx.location as String)) {
            path = (args.nginx.location as String).replace("/","\\/")
        }

        // 若 args.nginx.location 有配置，即有调整前端请求目录则无需增加以下路径， site 及 h5 为默认路径
//        if (path == "" && args.frontendType == FrontendType.MGR_SYS) {
//            script += "\\/site"
//        }
//        if (path == "" && args.frontendType == FrontendType.H5) {
//            script += "\\/h5"
//        }

        script += "${path}\\/static\\/js\\/env\\/config.js\" type=\"text\\/javascript\"><\\/script>"
        script = "sed -i 's/<title>/${script}&/' ${args.context.pwd()}/dist/index.html"
        args.context.echo "insert config script for index.html: ${script}"
        args.context.sh script

        // 在 /dist/static/js 文件夹生产 config.js
        String fileName = TemplateFileType.ConfigJs.getFileName()
        generateTemplateFile("/dist/static/js/env", args.context, envVars, fileName, fileName, "js")
    }
}
