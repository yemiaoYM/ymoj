package com.ymcloud.core.web.domain.server;

import cn.hutool.core.date.BetweenFormatter;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.ymcloud.common.utils.Arith;
import lombok.Getter;
import lombok.Setter;

import java.lang.management.ManagementFactory;
import java.util.Date;

/**
 * @PackageName: com.ymcloud.web.controller.admin.monitor
 * @ClassName: ServerController
 * @Author: Yemiao
 * @CreateTime: 2025-09-11  23:29
 * @Description: JVM相关信息
 */
@Setter
public class Jvm {

    /**
     * 当前JVM占用的内存总数(M)
     */
    private double total;

    /**
     * JVM最大可用内存总数(M)
     */
    private double max;

    /**
     * JVM空闲内存(M)
     */
    private double free;

    /**
     * JDK版本
     */
    @Getter
    private String version;

    /**
     * JDK路径
     */
    private String home;

    public double getTotal()
    {
        return Arith.div(total, (1024 * 1024), 2);
    }

    public double getMax()
    {
        return Arith.div(max, (1024 * 1024), 2);
    }

    public double getFree()
    {
        return Arith.div(free, (1024 * 1024), 2);
    }

    public double getUsed()
    {
        return Arith.div(total - free, (1024 * 1024), 2);
    }

    public double getUsage()
    {
        return Arith.mul(Arith.div(total - free, total, 4), 100);
    }

    /**
     * 获取JDK名称
     */
    public String getName()
    {
        return ManagementFactory.getRuntimeMXBean().getVmName();
    }

    /**
     * JDK启动时间
     */
    public String getStartTime() {
        long startTime = ManagementFactory.getRuntimeMXBean().getStartTime();
        Date serverStartDate = new Date(startTime);
        return DateUtil.formatDateTime(serverStartDate);
    }

    /**
     * JDK运行时间
     */
    public String getRunTime() {
        long startTime = ManagementFactory.getRuntimeMXBean().getStartTime();
        DateTime serverStartDate = DateUtil.date(startTime);
        return DateUtil.formatBetween(DateUtil.date(), serverStartDate, BetweenFormatter.Level.SECOND);
    }

    /**
     * 运行参数
     */
    public String getInputArgs() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().toString();
    }

}
