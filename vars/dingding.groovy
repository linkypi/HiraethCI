#!/usr/bin/env groovy
import com.hiraethci.deployer.constant.BuildStatus
import groovy.json.JsonOutput

def call(BuildStatus buildStatus, args, config) {
    def robotId = config.dingding.robotId
    def maxRecords = config.dingding.maxChangeRecords

    notify(robotId, buildStatus, args.projectInfo, args.image, maxRecords)
}

def GetChangeString(maxRecords) {
    def changeString = ""
    def changeLogSets = currentBuild.changeSets
    for (int i = 0; i < changeLogSets.size(); i++) {
        def entries = changeLogSets[i].items
        for (int j = 0; j < entries.length; j++) {
            def entry = entries[j]
            // truncated_msg = entry.msg.take(100) // 截取字符长度
            commitTime = new Date(entry.timestamp).format("yyyy-MM-dd HH:mm:ss")
            changeString += "> - ${entry.msg} [${entry.author} ${commitTime}]\n"
            if (maxRecords == 0) {
                break
            }
            maxRecords --
        }
    }
    if (!changeString) {
        changeString = "> - No new changes"
    }
    return changeString
}

void notify(robotId, BuildStatus buildStatus, projectInfo, image, int maxRecords = 50) {
    wrap([$class: 'BuildUser']) {
        def changeString = GetChangeString(maxRecords)
        String status = buildStatus.msg

        println " dingding project info : ${JsonOutput.toJson(projectInfo)}"
        String projectName = ""
        String version = ""

        if(projectInfo != null){
            projectName = projectInfo.projectName
            version = projectInfo.version
        }else{
            projectName = "${env.JOB_NAME}"
            version = "${env.BUILD_NUMBER}"
        }
        dingtalk (
                robot: robotId,
                type: 'MARKDOWN',
                title: '你有新的消息，请注意查收',
                text: [
                        "### 构建信息",
                        "> - 应用名称：**${projectName}**",
                        "> - 构建结果：**${status}**",
                        "> - 当前版本：**${version} (#${env.BUILD_NUMBER})**",
                        "> - 镜像：**${image}**",
                        "> - 构建发起：**${env.BUILD_USER}**",
                        "> - 持续时间：**${currentBuild.durationString}**",
                        "> - 构建日志：[点击查看详情](${env.BUILD_URL}console)",
                        "### 更新记录:",
                        "${changeString}"
                ],
                at: [ "${env.BUILD_USER}"]
        )
    }
}