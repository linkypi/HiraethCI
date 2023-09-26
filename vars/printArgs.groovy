#!/usr/bin/env groovy

def call(branch, environment , deployHost) {
    def mvnInfo
    def user
    script {
        mvnInfo = sh(script: "mvn -version", returnStdout: true).trim()
        user = sh(script: "whoami", returnStdout: true).trim()
    }
    echo "代码分支 branch ： ${branch}"
    echo "环境名称 environment ：${environment}"
    echo "部署主机名 hosts ：${deployHost}"
    echo "当前操作用户：${user}"
    echo "Maven Info：${mvnInfo}"
}