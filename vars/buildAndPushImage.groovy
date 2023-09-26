#!/usr/bin/env groovy
import com.hiraethci.deployer.constant.DeployType
import com.hiraethci.deployer.constant.OperationType

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
def call(args, events = null) {
    args = args == null ? [:] : args
    args.deployType = DeployType.BY_SOURCE
    args.opType = OperationType.BUILD

    runPipeline(args, { config ->
        buildAndPushImageInternal(args, config, events)
    })
}