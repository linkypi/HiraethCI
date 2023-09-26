package com.hiraethci.deployer.util

import com.cloudbees.groovy.cps.NonCPS

/**
 * @author linxueqi
 * @description
 * @createtime 2020-08-26 13:49
 */
public class StringUtil {

    private StringUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static boolean isNullOrEmpty(String value){
        return value == null || value == ""
    }
}
