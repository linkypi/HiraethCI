#!/usr/bin/env groovy


/**
 * stage 该部分 DSL 代码只能放在 vars 全局变量中
 * 不能放到源码 com.hiraethci.deployer 下， 因为环境上下文不同，无法执行
 * @param name
 * @param func
 * @return
 */
def call(String name, Closure func) {
    stage(name) {
        if (func) {
            func()
        }
    }
}