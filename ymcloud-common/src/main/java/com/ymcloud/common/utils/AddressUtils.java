package com.ymcloud.common.utils;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-09-14  22:06
 * @Description: 获取地址类
 */
@Slf4j
public class AddressUtils {

    //IP地址查询
    public static final String IP_URL = "http://whois.pconline.com.cn/ipJson.jsp";

    //未知地址
    public static final String UNKNOWN = "XX XX";

    public static String getRealAddressByIP(String ip){
        //内网不查询
        if (NetUtil.isInnerIP(ip)) {
            return "内网IP";
        }
        try {
            String rspStr = HttpUtil.get(IP_URL + "?ip=" + ip + "&json=true", CharsetUtil.CHARSET_GBK);
            if (StrUtil.isEmpty(rspStr)) {
                log.error("获取地理位置异常 {}", ip);
                return UNKNOWN;
            }
            JSONObject obj = JSON.parseObject(rspStr);
            return obj.getString("addr");
        } catch (Exception e) {

        }
        return UNKNOWN;
    }

}
