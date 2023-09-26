#!/usr/bin/env groovy
import com.hiraethci.deployer.Configuration
import com.hiraethci.deployer.constant.BuildStatus
import com.hiraethci.deployer.constant.PipelineType

/**
 * pipeline 该部分 DSL 代码只能放在 vars 全局变量中
 * 不能放到源码 com.hiraethci.deployer 下， 因为环境上下文不同，无法执行
 * @param args
 * @param func
 * @return
 */
def call(args , Closure func){
    println " start pipeline !"
    args.context = this
    def config = Configuration.getConfig(args, this)

    // 若有指定 nexus 则使用用户指定的nexus
    if(args.nexus){
        config.nexus = args.nexus
    }

    // 当前执行脚本位置
    if(config.pipelineType == PipelineType.FromSCM) {
        println "current script path: ${currentBuild.rawBuild.parent.definition.scriptPath}"
    }
    println "Current agent[当前代理机器]：${config.agent}"

    pipeline {
        agent {
            label "${config.agent}"
        }
        stages {
            stage('prepare') {
                steps {
                    script {
                        if(func) {
                            func(config)
                        }
                    }
                }
            }
        }

        post {
            success {
                script {
                    println 'build success'
                    dingding(BuildStatus.SUCCESS, args, config)
                }
            }
            failure {
                script {
                    println 'build failed'
                    dingding(BuildStatus.FAILED, args, config)
                }
            }
            aborted {
                script {
                    println 'build aborted'
                    dingding(BuildStatus.ABORT, args, config)
                }
            }
        }
    }
}