package com.hiraethci.deployer.util

import groovy.json.JsonOutput

/**
 * @author linxueqi
 * @description
 * @createtime 2020-08-26 13:49
 */
public class NetUtil {

    private NetUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static boolean isIpAddress(String ip) {
        return ip.matches("(([0-1]?[0-9]{1,2}\\.)|(2[0-4][0-9]\\.)|(25[0-5]\\.)){3}(([0-1]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5]))")
    }

    /**
     * 获取本地 IP 地址
     * @param networkCard 默认网卡是 eth0
     * @return
     */
    public static String getLocalIpAddress(String networkCard = "eth0", List<String> excludeIps, context) {
        try {
            Enumeration<NetworkInterface> allNetInterfaces = NetworkInterface.getNetworkInterfaces();
            InetAddress ip = null;
            while (allNetInterfaces.hasMoreElements()) {
                NetworkInterface netInterface = allNetInterfaces.nextElement();
                if (networkCard != "" && netInterface.getName().toUpperCase() != networkCard.toUpperCase()) {
                    continue
                }
                // 过来环回、虚拟以及虚拟机地址
                if (netInterface.isLoopback() || netInterface.isVirtual()
                        || !netInterface.isUp() || netInterface.getDisplayName().contains("Virtual")) {
                    continue;
                }

                Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    ip = addresses.nextElement();
                    if (ip instanceof Inet4Address) {
                        String ipAddr = ip.getHostAddress().toString()
                        if (ipAddr == "" || excludeIps.contains(ipAddr)) {
                            context.echo "exclude ip: " + ipAddr;
                            continue
                        }
                        return ipAddr;
                    }
                }
            }
        } catch (Exception ex) {
            print("get ip address take error: \n " + ex.getStackTrace());
        }
        return "";
    }

    public static String getLocalIpAddress(String networkCard = "eth0") {
        try {
            Enumeration<NetworkInterface> allNetInterfaces = NetworkInterface.getNetworkInterfaces();
            InetAddress ip = null;
            while (allNetInterfaces.hasMoreElements()) {
                NetworkInterface netInterface = allNetInterfaces.nextElement();
                if (networkCard != "" && netInterface.getName().toUpperCase() != networkCard.toUpperCase()) {
                    continue
                }
                // 过来环回、虚拟以及虚拟机地址
                if (netInterface.isLoopback() || netInterface.isVirtual()
                        || !netInterface.isUp() || netInterface.getDisplayName().contains("Virtual")) {
                    continue;
                }

                Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    ip = addresses.nextElement();

                    if (ip instanceof Inet4Address) {
                        return ip.getHostAddress();
                    }
                }
            }
        } catch (Exception ex) {
            print("get ip address take error: \n " + ex.getStackTrace());
        }
        return "";
    }

    /**
     * 获取系统可用端口，若默认端口被占用则将端口自增后再次检测，直到找到一个可用端口
     * @param context
     * @param defaultPort
     * @return
     */
    public static int getAvailablePort(context, int defaultPort) {
        // 检测端口是否被占用，占用则自增
        int failed = 1;
        int tempPort = defaultPort
        while (tempPort < 65535) {
            def returnStatus = context.sh returnStatus: true, script: "netstat -antp|grep ${tempPort}|grep LISTEN > usedPort.txt"
            if (returnStatus > -1) {
                def output = context.readFile('usedPort.txt').trim()
                context.echo "detect available port ${tempPort}, result: ${output}"
                if (output == "") {
                    return tempPort
                }
                context.echo "port ${tempPort++} already in used, try to use ${tempPort}"
            } else {
                context.echo "detect available port ${tempPort++} failed ${failed++}, returnStatus: ${returnStatus}"
                if (failed > 5) {
                    return defaultPort;
                }
            }
        }
    }
}
