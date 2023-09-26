package com.hiraethci.deployer.constant

/**
 * Description hiraethci-deployer
 * Created by troub on 2021/9/7 10:25
 */
enum FrontendType {
    WEB("web","通用Web系统"),
    CMS("cms","CMS系统"),
    MGR_SYS("mgr", "管理系统"),
    H5("h5", "H5")

    public String getTag(){
        return tag
    }

    private String tag
    private String name
    FrontendType(tag,name){
        this.tag = tag
        this.name = name
    }
}
