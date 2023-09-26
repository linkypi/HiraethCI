#!/usr/bin/env groovy
import com.hiraethci.deployer.ProjectManager
import com.hiraethci.deployer.constant.ProjectType
import com.hiraethci.deployer.constant.DeployType
import com.hiraethci.deployer.SourceBuilder
import com.hiraethci.deployer.exception.UnsupportedProjectTypeException

/**
 * 构建maven 源码并上传到 nexus
 * @param args  入参
 * @return
 */
def call(args) {
    args = args == null ? [:] : args
    args.deployType = DeployType.BUILD_MAVEN_SRC
    runPipeline(args, { config ->
        pullCodeIfNecesary(config, args.url, args.branch)

        // 解析项目构建类型，名称，版本号
        runStage('parse project', {
            ProjectManager.parseProject(args, config)
        })
        if (args.projectType != ProjectType.Maven) {
            throw new UnsupportedProjectTypeException("build source just support maven project.")
        }
        if(config.reviewCode) {
            runSonarQubeScan('sonarqube.30', args.projectType as ProjectType, this)
        }
        runStage('build maven and deploy', {
            new SourceBuilder(this).buildMavenAndDeploy()
        })
    })
}
