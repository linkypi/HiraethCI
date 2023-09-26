package com.hiraethci.deployer.constant

/**
 * Description hiraethci-deployer
 * Created by troub on 2021/9/8 17:27
 */
enum PipelineType {
    //通过脚本的方式进行部署
    Script,
    // 通过在 jenkins 指定仓库地址及脚本所在项目位置进行部署
    FromSCM;
}
