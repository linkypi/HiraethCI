package com.hiraethci.deployer


import com.hiraethci.deployer.util.StringUtil
import com.hiraethci.deployer.constant.ProjectType
import groovy.json.JsonSlurper


/**
 * Description hiraethci-deployer
 * Created by troub on 2021/9/4 14:30
 */
class SourceBuilder {

    private ProjectType projectType;
    private Closure beforeCompileEvent;
    private def context;

    public SourceBuilder(){}
    public SourceBuilder(ctx){
        this.context = ctx
    }

    public SourceBuilder(context, ProjectType projectType) {
        this.context = context
        this.projectType = projectType
    }

    public SourceBuilder(context, ProjectType projectType, Closure beforeCompileEvent) {
        this.context = context
        this.projectType = projectType
        this.beforeCompileEvent = beforeCompileEvent
    }

    def buildMavenAndDeploy() {
        //context.sh 'mvn org.sonarsource.scanner.maven:sonar-maven-plugin:3.7.0.1746:sonar'
        context.sh 'mvnd clean install deploy -Dmaven.test.skip=true -U'
    }

    def build(profile) {
        // 将该行脚本放在此处是为方便统一 beforeCompileEvent 执行， “ || true ” 表示即使脚本执行出错仍然往后执行
        context.sh 'chmod +x gradlew || true'

        if (beforeCompileEvent) {
            beforeCompileEvent()
        }
        if (projectType == ProjectType.Maven) {
            buildMaven()
        }
        if (projectType == ProjectType.Gradle) {
            buildGradle()
        }
        if (projectType == ProjectType.Npm) {
            buildFrontend(profile)
        }
    }

    def buildI18N() {
        if (projectType == ProjectType.Maven) {
            context.sh "mvnd test -Dtest=I18NGenerator -U"
        }
        if (projectType == ProjectType.Gradle) {
            context.sh 'chmod +x gradlew || true'
            context.sh "./gradlew test --tests I18NGenerator"
        }
    }

    private def buildMaven() {
        //context.sh 'mvn org.sonarsource.scanner.maven:sonar-maven-plugin:3.7.0.1746:sonar'
        context.sh 'mvnd clean install -Dmaven.test.skip=true -U'
    }

    private def buildGradle() {
        context.sh './gradlew clean bootjar --refresh-dependencies'
    }

    /**
     * 构建前端
     * @param env ： presit, sit , prod
     * @return
     */
    private def buildFrontend(profile) {
        String defaultCmd = "npm"
        // 检测 cnpm 命令是否存在，存在则使用 cnpm ， 不存在则安装并设置淘宝仓库
//        context.sh 'command -v cnpm > commandResult'
//        String result = context.readFile('commandResult').trim()
//        if(!StringUtil.isNullOrEmpty(result)) {
//            println "cnpm exist, use cnpm to build."
//            context.echo "cnpm exist, use cnpm to build."
//            defaultCmd = "cnpm"
//        }else{
//            println "cnpm not exist, install cnpm for building frontend"
//            context.echo "cnpm not exist, install cnpm for building frontend"
//            context.sh 'npm install -g cnpm --registry=https://registry.npmmirror.com'
//            context.sh 'npm config set registry https://registry.npmmirror.com'
//        }
        context.sh 'rm -fr node_modules'
        context.sh "${defaultCmd} rebuild node-sass"
        context.sh "${defaultCmd} cache clean --force"
        context.sh "${defaultCmd} i"
        if(StringUtil.isNullOrEmpty(profile)) {
            context.sh "${defaultCmd} run build"
        }else{
            context.sh "${defaultCmd} run build:${profile}"
        }
    }

    private def checkScripts(cmd, srcPath){
        String content = context.readFile("${srcPath}package.json")
        def packageInfo = new JsonSlurper().parseText(content)
        String target = packageInfo.scripts."${cmd}"
        if(StringUtil.isNullOrEmpty(target)){
            throw new RuntimeException("script ${cmd} not found in package.json, please check package.json file.")
        }
    }
}
