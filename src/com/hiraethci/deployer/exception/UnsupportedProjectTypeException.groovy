package com.hiraethci.deployer.exception;

/**
 * Description hiraethci-deployer
 * Created by troub on 2021/9/4 16:23
 */
public class UnsupportedProjectTypeException extends Exception{
    public UnsupportedProjectTypeException(){
        super()
    }

    public UnsupportedProjectTypeException(String msg){
        super(msg)
    }
}
