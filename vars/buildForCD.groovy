#!/usr/bin/env groovy
import com.hiraethci.deployer.constant.DeployType
import com.hiraethci.deployer.constant.OperationType
import com.hiraethci.deployer.util.StringUtil

/**
 * 1. 将源码编译为镜像
 * 2. 修改k8s-deployment指定项目镜像，使得 ArgoCD 监听器完成自动部署
 * @param args  入参
 * @param events 事件
 *   events: [
 *       beforeCompile: { -> } // 编译源码前执行
 *       afterCompile: { -> } // 编译源码完成后执行
 *   ]
 * @return
 */
def call(args, events = null) {
    args = args == null ? [:] : args
    args.opType = OperationType.BUILD
    args.deployType = DeployType.BY_SOURCE

    runPipeline(args, { config ->
        if(StringUtil.isNullOrEmpty(args.mainVersion as String)){
            println "warning: the main version not specified: mainVersion !!!"
        }
        buildAndPushImageInternal(args, config, events)
        updateRemoteTag(args, config)
    })
}

/**
 * 更新远程git仓库镜像，使得ArgoCD自动完成部署
 * @param args
 * @param config
 */
def updateRemoteTag(args, config) {

    String projectName = args.projectInfo.projectName
    String subModulePath = config.projectMappers.get(projectName)
    if (StringUtil.isNullOrEmpty(subModulePath)) {
        throw new RuntimeException("k8s deployment sub dir not found: ${subModulePath}, project name: ${projectName} !!!")
//            println "update deployment failed. k8s deployment sub dir not found, project name: ${projectName} !!!"
//            return
    }

    def context = args.context
    runStage('update deployment', {
        String version = args.projectInfo.version
//        String version = args.imageTag
        // 判断配置文件是否存在
        if (context.fileExists("values-${args.env}.yaml")) {
            throw new RuntimeException("k8s deployment file not found: values-${args.env}.yaml")
        }

        context.sh "rm -fr ./${config.k8sDeployment.relativeSubDir} || true"
        String newImage = sprintf(config.imageUrl, args.containerName, args.imageTag)

        updateRepoImageTag(config, context, args, subModulePath, newImage, version)
    })
    args.image = "${args.containerName}:${args.imageTag}"
    printf("update ${args.env} deployment image tag success: ${config.imageUrl}", args.containerName, args.imageTag)

    if(args.syncToCD) {
        runStage('sync to argocd', {
            context.sh "argocd login ${config.argocd.server} --username=${config.argocd.username} --password=${config.argocd.password} --insecure"
            String app = "${args.env}-${subModulePath}"
            context.sh "argocd app sync ${app} --resource apps:Deployment:${app}" // --force
//            String result = context.sh(script: "argocd app sync ${app} --resource apps:Deployment:${app}", returnStdout: true).trim()
//            context.sh "echo ${result}"
        })
    }
}

private void updateRepoImageTag(config, context, args, String subModulePath, String newImage, String version) {
    sshagent([config.gitCredential.id]) {
        context.sh "git config --global user.name ${config.gitCredential.userName}"
        context.sh "git config --global user.email ${config.gitCredential.userEmail}"

        context.sh "git clone -b ${args.env} ${config.k8sDeployment.url} ${config.k8sDeployment.relativeSubDir}"
        dir("${config.k8sDeployment.relativeSubDir}") {
            context.sh "git branch"
            context.sh "git status"
            context.sh "pwd"
//            context.sh "ls -la"
//                context.sh "git checkout -b ${config.k8sDeployment.branch} || true"
//                context.sh "git checkout ${config.k8sDeployment.branch} || true"
            context.sh "git pull origin ${args.env}"

            // 若是前端项目则需要将 nginx.conf 配置到config-map 中
            if (args.frontend) {
                generateConfigMapForConfig(args, config, subModulePath)
            }

            modifyValue(args, config, context, subModulePath, newImage, version)

            context.sh "git status"
            context.sh "git commit -a -m 'update ${subModulePath} image to ${newImage} and version to ${version}'"
            context.sh "git status"
            context.sh "git tag -a ${version} -m '${version}' || true"
//                context.sh "git pull "
            context.sh "git push -u origin ${args.env}:${args.env}"
        }
    }
}

/**
 * 为前端配置nginx.conf及config.js
 * @param args
 * @param config
 * @param subModulePath
 * @return
 */
def generateConfigMapForConfig(args, config, subModulePath){
    def context = args.context
    // 4个空格缩进
    def indent = "    "
    context.sh "sed -i 's/^/${indent}/g' ../cicd/nginx.conf"
    context.sh "sed -i 's/^/${indent}/g' ../dist/static/js/env/config.js"
    String nginxContent = context.sh(script: "cat ../cicd/nginx.conf", returnStdout: true).trim()
    String configContent = context.sh(script: "cat ../dist/static/js/env/config.js", returnStdout: true).trim()
    nginxContent = nginxContent.replace("\$","\\\$") //sed -i 's/\$/\\$/g'
    context.sh "cat > ${subModulePath}/templates/config-map.yml <<EOF\n" +
            "apiVersion: v1\n" +
            "kind: ConfigMap\n" +
            "metadata:\n" +
            "  name: ${subModulePath}-config\n" +
            "data: \n" +
            "  nginx.conf: |\n${indent}${nginxContent}\n" +
            "  config.js: |\n${indent}${configContent}\n" +
            "EOF"
}

def modifyValue(args, config, context, subModulePath, newImage, version){
    // 找到配置文件后仅需修改 version, image 及 className 即可，其他配置直接使用原有环境的配置
//    String image = sprintf(config.imageUrl, args.containerName, tag)
//    image = image.replace("/","\\/")
//    context.sh "sed -i 's/image:.*\$/image: ${newImage}/g' ${valuePath}"
//    context.sh "sed -i 's/version:.*\$/version: ${version}/g' ${valuePath}"
//    context.sh "sed -i 's/className:.*\$/className: ${scName}/g' ${valuePath}"

    def valuePath = "${subModulePath}/values-${args.env}.yaml"
//    def scName = prepareStorageClass(args, valuePath)
    context.sh "yq -i '.image = \"${newImage}\"' ${valuePath}"
    context.sh "yq -i '.version = \"${version}\"' ${valuePath}"
//    context.sh "yq -i '.storage.className = \"${scName}\"' ${valuePath}"

    def chartPath = "${subModulePath}/Chart.yaml"
    context.sh "yq -i '.appVersion = \"${args.mainVersion}\"' ${chartPath}"
}

String prepareStorageClass(args, valuePath) {
    def context = args.context
//    def name = context.sh(script: "grep -w 'name:' ${valuePath}|sed 's/name://g'", returnStdout: true).trim()
    String name = context.sh(script: "yq '.name' ${valuePath}", returnStdout: true).trim()
    if(StringUtil.isNullOrEmpty(name)){
        throw new RuntimeException("name not found in ${valuePath}")
    }

    def ns = args.env
    // 判断storage class是否存在
    // kubectl get sc -n cicd|grep web-backend-nfs-storage|wc -l
    def scName = "${ns}-${name}-nfs"
    def count = context.sh(script: "kubectl get sc|grep ${scName}|wc -l", returnStdout: true).trim()
    if (count != '0') {
        println "storage class name [${scName}'] exists."
        return scName
    }
    // 判断helm 是否存在指定项目名称, 若存在则卸载后重装
    def pvs = "${ns}-${name}-pvs"
    // helm list -n presit|grep api-auth-presit-pvs
    count = context.sh(script: "helm list -n ${ns}|grep ${pvs}|wc -l", returnStdout: true).trim()
    if (count != '0') {
        println "helm uninstall ${pvs} because it has exist."
        context.sh "helm uninstall ${pvs} -n ${ns}"
    }

    println "current dir: ${context.pwd()}"

    def serviceAccount = "${ns}-${name}-sa"
    def dir = "/data/nfs/${ns}/${name}"

    context.sh "helm install ${pvs} ./nfs-provisioner -n ${ns} \
            --set nfs.server=192.168.10.81 \
            --set nfs.path=${dir} \
            --set serviceAccount.name=${serviceAccount} \
            --set storageClass.name=${scName}"

    context.sh "echo 'create storage class name [${scName}'] success."
    return scName
}
