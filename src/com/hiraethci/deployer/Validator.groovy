package com.hiraethci.deployer

import com.hiraethci.deployer.constant.OperationType
import com.hiraethci.deployer.util.StringUtil

/**
 * Description hiraethci-deployer
 * Created by troub on 2021/9/4 13:51
 */
class Validator {

    public static checkDeployWithImageArgs(args, boolean isRemoteHost) {
        checkImageArgs(args.docker.image, args.docker.version)
        checkCommonArgs(args, isRemoteHost)

        //docker-compose.yml 文件在远程主机存放的位置 , dockerComposePath会自动加上 containerName， 即完整路径为 /var/docker/${containerName}
        if(isRemoteHost && StringUtil.isNullOrEmpty(args.dockerComposePath as String)){
            throw new IllegalArgumentException("The argument dockerComposePath unspecified !!!")
        }

        //docker-compose.yml 文件在远程主机存放的位置
        if(StringUtil.isNullOrEmpty(args.docker.containerName as String) && StringUtil.isNullOrEmpty(args.docker.hostname as String)){
            throw new IllegalArgumentException("The argument containerName and hostname must be specify one !!!")
        }
    }

    static def checkCommonArgs(args, boolean isRemoteHost) {
//        if(args.env == null || args.env == ""){
//            throw new IllegalArgumentException("The argument env unspecified !!!");
//        }
        args.env = StringUtil.isNullOrEmpty(args.env as String) ? "" : args.env
        checkRemoteArgs(args, isRemoteHost)
        checkDockerArgs(args)
    }

    public static checkSourceArgs(url, branch){
        if(StringUtil.isNullOrEmpty(url as String)){
            throw new IllegalArgumentException("The argument source url unspecified !!!")
        }

        if(StringUtil.isNullOrEmpty(branch as String)){
            throw new IllegalArgumentException("The argument source branch unspecified !!!")
        }
    }

    public static checkImageArgs(String image, String version){
        if(StringUtil.isNullOrEmpty(image)){
            throw new IllegalArgumentException("The argument image unspecified !!!")
        }

        if(StringUtil.isNullOrEmpty(version)){
            throw new IllegalArgumentException("The argument version unspecified !!!")
        }
    }

    public static checkDockerArgs(args){

        // 只有部署时才会检查docker参数
        if(args.opType == OperationType.DEPLOY) {
            if(args.docker == null){
                throw new IllegalArgumentException("The argument docker unspecified !!!")
            }
            if (args.docker.network == null || args.docker.network == "") {
                throw new IllegalArgumentException("The argument docker network unspecified !!!")
            }
        }
//        if(args.backend && StringUtil.isNullOrEmpty(args.docker.profile as String)){
//            throw new IllegalArgumentException("The argument docker profile unspecified in backend project !!!")
//        }
    }

    public static checkRemoteArgs(args, boolean isRemoteHost){
        if(isRemoteHost){
            if(args.remote == null){
                throw new IllegalArgumentException("The argument remote unspecified when " +
                        "the agent host and deploy host is not the same in project !!!")
            }
            if(StringUtil.isNullOrEmpty(args.remote.host)){
                throw new IllegalArgumentException("The argument remote host unspecified when " +
                        "the agent host and deploy host is not the same in project !!!")
            }
            if(StringUtil.isNullOrEmpty(args.remote.user)){
                throw new IllegalArgumentException("The argument remote user unspecified when " +
                        "the agent host and deploy host is not the same in project !!!")
            }
            // 公钥文件不存在则检查 password 是否存在
            if(StringUtil.isNullOrEmpty(args.remote.identityFile)){
                if(StringUtil.isNullOrEmpty(args.remote.password)){
                    throw new IllegalArgumentException("The argument remote password unspecified when " +
                            "the agent host and deploy host is not the same in project !!!")
                }
            }
        }

    }
}
