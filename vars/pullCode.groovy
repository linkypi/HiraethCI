#!/usr/bin/env groovy
import com.hiraethci.deployer.Validator
import com.hiraethci.deployer.util.StringUtil

def call(config, String url, String branch, String srcDir = null){

    // 直接通过脚本的方式进行部署， 需要指定代码地址及分支
    Validator.checkSourceArgs(url, branch)
    stage('pull code') {
        if(StringUtil.isNullOrEmpty(srcDir)) {
            checkoutFrom(url, branch, config.gitCredential.id)
        }else{
            println "pull code to sub dir: ${srcDir}"
            dir(srcDir){
                checkoutFrom(url, branch, config.gitCredential.id)
            }

        }
    }
}

def checkoutFrom(url, branch, credentialsId){
    checkout([
            $class: 'GitSCM',
            branches: [[ name: "*/${branch}" ]],
            extensions: [],
            userRemoteConfigs: [[
                credentialsId: credentialsId,
                url: url
            ]]
    ])
}