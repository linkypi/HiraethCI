package com.hiraethci.deployer

import com.hiraethci.deployer.constant.PipelineType
import com.hiraethci.deployer.util.NetUtil
import com.hiraethci.deployer.util.StringUtil
import groovy.json.JsonOutput

/**
 * Description hiraethci-deployer
 * Created by troub on 2021/9/4 13:56
 */
class Configuration {

    public static Object getConfig(args, context) {
        def configText = args.context.libraryResource resource: 'com/hiraethci/config.yml'
        def config = args.context.readYaml text: configText
        // 构建源码及镜像所在机器
        String agent = args.agent
        // 若代理机器不存在则使用默认主机
        if (StringUtil.isNullOrEmpty(agent)) {
            context.echo " agent not specified, use default ${config.defaultAgent}"
            agent = config.defaultAgent
        }
        config.agent = agent
        args.agent = agent

        config.pipelineType = detectPipelineType(args.context)

        getDeployIP(args, config, context)

        config.isRemoteHost = isRemoteHost(config, context)

        String configJson = JsonOutput.toJson(config)
        context.echo " System default config [构建插件默认配置]: ${configJson}"

        String argsJson = JsonOutput.toJson(args)
        context.echo " User input args [项目指定配置]: ${argsJson}"

        config.srcPath = getChildProjectPath(context, config.pipelineType, args, config)

        if (config.pipelineType == PipelineType.FromSCM) {
            context.echo " srcPath [源码路径]: ${config.srcPath}"
            context.echo " cicdPath [cicd路径]: ${config.cicdPath}"
            config.cicdPath = "${config.srcPath}cicd"
        } else {
            config.cicdPath = "${config.srcPath}"
        }

        // 初始化JVM默认参数
        config.defaultJvm = ""
        config.defaultJavaOpts.each {
            if (it.flag) {
                config.defaultJvm += it.flag
                if (it.connector) {
                    config.defaultJvm += it.connector.toString()
                }
                if (it.value) {
                    config.defaultJvm += it.value.toString()
                }
                config.defaultJvm += " "
            }
        }

        // 默认 reviewCode=true 执行Code Review
        if (args.reviewCode != null) {
            config.reviewCode = args.reviewCode
        }
        return config
    }

    /**
     * 项目可能包含不同子项目，如 xxl-job 包含 job-center 及 job-admin
     * 此处用于获取子项目的目录，方便查找编译结果文件，如jar包，dist文件等
     * @param context
     */
    private static String getChildProjectPath(context, pipelineType, args, config) {
        // 如 my-project/cicd/Jenkinsfile
        // 通过自定义脚本的方式进行部署时无法获取获取启动脚本
        if (pipelineType == PipelineType.Script) {
            return "";
        }

        String scriptPath = context.currentBuild.rawBuild.parent.definition.scriptPath
        // 移除预定部分
        if (StringUtil.isNullOrEmpty(scriptPath)) {
            return ""
        }
        String[] arr = scriptPath.split("/")
        String scriptFileName = arr[arr.length - 1];
        String path = scriptPath.replace("cicd/${scriptFileName}", "")
        return "/${path}"
    }

    private static PipelineType detectPipelineType(context) {
        // 检查jenkins部署方式，
        // 若 branch 存在则说明是通过 Pipeline script from SCM 的方式部署, 此时 args 中的url, branch无效
        // 若 branch 不存在则说明是通过 Pipeline script 的方式部署，此时需要校验 args 中的url, branch
        try {
            String branch = context.scm.branches[0].name.split("/")[1]
            if (!StringUtil.isNullOrEmpty(branch)) {
                return PipelineType.FromSCM
            }
        } catch (ignored) {
            // ‘checkout scm’ is only available when using “Multibranch Pipeline” or “Pipeline script from SCM”
        }
        return PipelineType.Script
    }

    private static void getDeployIP(args, config, context) {
        // 获取部署IP，首先判断 deployHost 填写的是否是 presit, sit, uat, prod 等标志信息，若是则直接从deployHostIpMapper取出对应 IP
        // 若不是再判断deployHost是否填写的是实际IP地址，如果是则使用实际IP 否则使用 defaultDeployHost
        if (StringUtil.isNullOrEmpty(args.deployHost as String)) {
            config.deployIp = config.deployHostIpMapper.get(config.defaultDeployHost)
            context.echo " Deploy host un specified , will use default host[部署项目所在机器未指定，使用默认机器进行部署]: ${config.deployIp} ."
        } else {
            boolean isIp = NetUtil.isIpAddress(args.deployHost as String)
            if (!isIp) {
                config.deployIp = config.deployHostIpMapper.get(args.deployHost)
                context.echo " Deploy host use specified value [部署项目所在机器使用的是指定标识]: ${args.deployHost} ."
                if (config.deployIp == null || config.deployIp == "") {
                    throw new IllegalArgumentException("Deploy host use specified value: ${args.deployHost}, " +
                            "but could not found the mapper[deployHostIpMapper] IP in deploy plugin config.yml. " +
                            "[部署主机参数deployHost已指定: ${args.deployHost}, 但配置文件中没有配置该值所对应的IP地址.]")
                }
            } else {
                config.deployIp = args.deployHost
                context.echo " Deploy host use the specifial IP [部署项目所在机器使用的是实际IP]: ${args.deployHost} ."
            }
        }
    }

    private static boolean isRemoteHost(config, context) {
        // 获取当前构建的机器IP
        String ip = NetUtil.getLocalIpAddress(config.VRRPIP as List<String>, context)
        context.echo " Current builder host IP [构建机器所在 IP]： ${ip}, deployIp: ${config.deployIp}"
        String deployIp = config.deployIp as String
        // 若当前构建的机器IP与部署机器不是同一个则可判定是远程部署
        return deployIp != null && deployIp != ip
    }

}
