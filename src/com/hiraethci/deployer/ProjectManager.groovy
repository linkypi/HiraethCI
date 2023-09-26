package com.hiraethci.deployer


import com.hiraethci.deployer.constant.ProjectType
import com.hiraethci.deployer.util.StringUtil
import com.hiraethci.deployer.constant.FrontendType
import groovy.json.JsonSlurper

/**
 * Description hiraethci-deployer
 * Created by troub on 2021/9/4 14:00
 */
class ProjectManager {

    static def parseProject(args, config) {

        // 工作目录很可能与开始的工作目录不一样，所以需要重新获取
        // 如job-admin项目路径为 /var/lib/jenkins/workspace/xxl_job
        // 待实际解析项目时却是  /var/lib/jenkins/workspace/xxl_job@2
        def context = args.context
        String workspace = context.pwd()
        def srcPath = workspace + (config.srcPath as String)

        autoDetectProjectType(args, srcPath)

        if (args.projectType == ProjectType.Npm) {
            args.projectInfo = getFrontendInfo(context, srcPath)
            String projectName = StringUtil.isNullOrEmpty(args.projectInfo.projectName as String) ? "" : args.projectInfo.projectName

            for (FrontendType ftype : FrontendType.values()) {
                if (projectName.toUpperCase().contains(ftype.getTag().toUpperCase())) {
                    args.frontendType = ftype
                    context.echo "当前系统 FrontendType： ${ftype.name()}"
                    return
                }
            }

            args.frontendType = FrontendType.WEB
            context.echo "当前系统 FrontendType： 通用Web前端系统"
            return
        }

        if (args.projectType == ProjectType.Maven) {
            args.projectInfo = getMavenInfo(args.context, srcPath)
            return
        }

        if (args.projectType == ProjectType.Gradle) {
            args.projectInfo = getGradleInfo(args.context, srcPath)
            return
        }
        throw new RuntimeException("Can not detect project info in config file, " +
                "please specified project type , such as pom.xml , build.gradle or package.json !!!")
    }

    private static void autoDetectProjectType(args, srcPath) {
        def context = args.context
        context.echo "auto detect source path : ${srcPath}, working space: ${ context.pwd() }"

        if (context.fileExists("${srcPath}package.json")) {
            args.frontend = true
            args.projectType = ProjectType.Npm
            return
        }
        if (context.fileExists("${srcPath}pom.xml") && context.fileExists("${srcPath}/build.gradle")) {
            args.backend = true
            // 两种构建工具的配置文件同时存在时需要在参数中指定具体的构建类型
            if (args.projectType == null || args.projectType == "") {
                throw new IllegalArgumentException("Unable detect project build type when pom.xml and build.gradle" +
                        " exist at the same time. It can be specified with argument projectType !!!");
            }
            args.projectType = ProjectType.of(args.projectType.toString())
            return
        }
        if (context.fileExists("${srcPath}pom.xml")) {
            args.backend = true
            args.projectType = ProjectType.Maven
            return
        }
        if (context.fileExists("${srcPath}build.gradle")) {
            args.backend = true
            args.projectType = ProjectType.Gradle
            return
        }

        throw new IllegalArgumentException("Unable detect project type , cannot find one of the file: pom.xml , build.gradle or package.json !");
    }

    private static def  getFrontendInfo(context, srcPath){
        String content = context.readFile("${srcPath}package.json")
        def packageInfo = new JsonSlurper().parseText(content)
        return [
                projectName: packageInfo.name,
                version: packageInfo.version
        ]
    }

    private static def getGradleInfo(context, srcPath) {
        // 提取出 version '1.0.0' 后根据空格分割，然后去除前后两边的单引号即可以得到 1.0.0
        // 注意 version 行首可能存在空格，所以需要使用 \s* 匹配空格
        String gradleConfigPath = "${srcPath}build.gradle"
        context.sh 'grep \"^\\s*version\" ' + gradleConfigPath + '|awk \'{print $NF}\'|sed \$\"s/\'//g\" > commandResult'
        String version = context.readFile('commandResult').trim()

        // 若版本指定的是特定属性 如 ${revision} 则从  properties 节点中的属性获取
        if (version.contains("\$")) {
            String prop = version.replace("\${", "").replace("}", "").replace("\"", "")
            String command = "grep '\\s\\+${prop}\\s\\+=' ${gradleConfigPath}|awk -F\'=\' \'{print \$NF}\'|sed \$\"s/\'//g\" > commandResult"
            context.sh command
            version = context.readFile('commandResult').trim()
            context.echo " detected gradle version: ${version}"
        }
        // 提取出 group 'service-provider' 后根据空格分割，然后去除前后两边的单引号即可以得到 service-provider
        // 注意 group 行首可能存在空格，所以需要使用 \s* 匹配空格
        context.sh 'grep \"^\\s*group\" ' + gradleConfigPath + '|awk \'{print $NF}\'|sed \$\"s/\'//g\" > commandResult'
        String projectName = context.readFile('commandResult').trim()
        return [
                projectName: projectName,
                version: version
        ]
    }

    private static def getMavenInfo(context, srcPath){
        def xmlfile = context.readMavenPom file: "${srcPath}pom.xml"
        String version = xmlfile.version

        context.echo " project version: ${version}"
        if(StringUtil.isNullOrEmpty(version)){
            throw new RuntimeException("Could not found project version in pom.xml, please check your project file pom.xml")
        }
        // 若版本指定的是特定属性 如 ${revision} 则从  properties 节点中的属性获取
        if(version.contains("\$")){
            String prop = version.replace("\${","").replace("}","")
            version = xmlfile.properties."${prop}"
        }
        def projectName = xmlfile.artifactId

        context.echo " project name: ${projectName}"
        return [
                projectName: projectName,
                version: version
        ]
    }
}
