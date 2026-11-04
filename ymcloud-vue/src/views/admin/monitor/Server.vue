<template>
  <div 
    class="server-container" 
    v-loading.fullscreen.lock="loading"
    element-loading-text="加载服务器配置中..."
    element-loading-background="var(--ym-loading-overlay)"
    >
    <el-row :gutter="10">
      <el-col :span="12" class="card-box">
        <el-card>
          <template #header>
            <Cpu style="width: 1em; height: 1em; vertical-align: middle;" /> <span
              style="vertical-align: middle;">CPU</span>
          </template>
          <div class="el-table el-table--enable-row-hover el-table--medium">
            <table cellspacing="0" style="width: 100%;">
              <thead>
                <tr>
                  <th class="el-table__cell is-leaf">
                    <div class="cell">属性</div>
                  </th>
                  <th class="el-table__cell is-leaf">
                    <div class="cell">值</div>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td class="el-table__cell is-leaf">
                    <div class="cell">核心数</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell" v-if="server.cpu">{{ server.cpu.cpuNum }}</div>
                  </td>
                </tr>
                <tr>
                  <td class="el-table__cell is-leaf">
                    <div class="cell">用户使用率</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell" v-if="server.cpu">{{ server.cpu.used }}%</div>
                  </td>
                </tr>
                <tr>
                  <td class="el-table__cell is-leaf">
                    <div class="cell">系统使用率</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell" v-if="server.cpu">{{ server.cpu.sys }}%</div>
                  </td>
                </tr>
                <tr>
                  <td class="el-table__cell is-leaf">
                    <div class="cell">当前空闲率</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell" v-if="server.cpu">{{ server.cpu.free }}%</div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </el-card>
      </el-col>

      <el-col :span="12" class="card-box">
        <el-card>
          <template #header>
            <Tickets style="width: 1em; height: 1em; vertical-align: middle;" /> <span
              style="vertical-align: middle;">内存</span>
          </template>
          <div class="el-table el-table--enable-row-hover el-table--medium">
            <table cellspacing="0" style="width: 100%;">
              <thead>
                <tr>
                  <th class="el-table__cell is-leaf">
                    <div class="cell">属性</div>
                  </th>
                  <th class="el-table__cell is-leaf">
                    <div class="cell">内存</div>
                  </th>
                  <th class="el-table__cell is-leaf">
                    <div class="cell">JVM</div>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td class="el-table__cell is-leaf">
                    <div class="cell">总内存</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell" v-if="server.mem">{{ server.mem.total }}G</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell" v-if="server.jvm">{{ server.jvm.total }}M</div>
                  </td>
                </tr>
                <tr>
                  <td class="el-table__cell is-leaf">
                    <div class="cell">已用内存</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell" v-if="server.mem">{{ server.mem.used }}G</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell" v-if="server.jvm">{{ server.jvm.used }}M</div>
                  </td>
                </tr>
                <tr>
                  <td class="el-table__cell is-leaf">
                    <div class="cell">剩余内存</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell" v-if="server.mem">{{ server.mem.free }}G</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell" v-if="server.jvm">{{ server.jvm.free }}M</div>
                  </td>
                </tr>
                <tr>
                  <td class="el-table__cell is-leaf">
                    <div class="cell">使用率</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell" v-if="server.mem" :class="{ 'text-danger': server.mem.usage > 80 }">{{
                      server.mem.usage
                    }}%</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell" v-if="server.jvm" :class="{ 'text-danger': server.jvm.usage > 80 }">{{
                      server.jvm.usage
                    }}%</div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </el-card>
      </el-col>

      <el-col :span="24" class="card-box">
        <el-card>
          <template #header>
            <Monitor style="width: 1em; height: 1em; vertical-align: middle;" /> <span
              style="vertical-align: middle;">服务器信息</span>
          </template>
          <div class="el-table el-table--enable-row-hover el-table--medium">
            <table cellspacing="0" style="width: 100%;">
              <tbody>
                <tr>
                  <td class="el-table__cell is-leaf">
                    <div class="cell">服务器名称</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell" v-if="server.sys">{{ server.sys.computerName }}</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell">操作系统</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell" v-if="server.sys">{{ server.sys.osName }}</div>
                  </td>
                </tr>
                <tr>
                  <td class="el-table__cell is-leaf">
                    <div class="cell">服务器IP</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell" v-if="server.sys">{{ server.sys.computerIp }}</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell">系统架构</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell" v-if="server.sys">{{ server.sys.osArch }}</div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </el-card>
      </el-col>

      <el-col :span="24" class="card-box">
        <el-card>
          <template #header>
            <CoffeeCup style="width: 1em; height: 1em; vertical-align: middle;" /> <span
              style="vertical-align: middle;">Java虚拟机信息</span>
          </template>
          <div class="el-table el-table--enable-row-hover el-table--medium">
            <table cellspacing="0" style="width: 100%;table-layout:fixed;">
              <tbody>
                <tr>
                  <td class="el-table__cell is-leaf">
                    <div class="cell">Java名称</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell" v-if="server.jvm">{{ server.jvm.name }}</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell">Java版本</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell" v-if="server.jvm">{{ server.jvm.version }}</div>
                  </td>
                </tr>
                <tr>
                  <td class="el-table__cell is-leaf">
                    <div class="cell">启动时间</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell" v-if="server.jvm">{{ server.jvm.startTime }}</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell">运行时长</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell" v-if="server.jvm">{{ server.jvm.runTime }}</div>
                  </td>
                </tr>
                <tr>
                  <td colspan="1" class="el-table__cell is-leaf">
                    <div class="cell">安装路径</div>
                  </td>
                  <td colspan="3" class="el-table__cell is-leaf">
                    <div class="cell" v-if="server.jvm">{{ server.jvm.home }}</div>
                  </td>
                </tr>
                <tr>
                  <td colspan="1" class="el-table__cell is-leaf">
                    <div class="cell">项目路径</div>
                  </td>
                  <td colspan="3" class="el-table__cell is-leaf">
                    <div class="cell" v-if="server.sys">{{ server.sys.userDir }}</div>
                  </td>
                </tr>
                <tr>
                  <td colspan="1" class="el-table__cell is-leaf">
                    <div class="cell">运行参数</div>
                  </td>
                  <td colspan="3" class="el-table__cell is-leaf">
                    <div class="cell" v-if="server.jvm">{{ server.jvm.inputArgs }}</div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </el-card>
      </el-col>

      <el-col :span="24" class="card-box">
        <el-card>
          <template #header>
            <MessageBox style="width: 1em; height: 1em; vertical-align: middle;" /> <span
              style="vertical-align: middle;">磁盘状态</span>
          </template>
          <div class="el-table el-table--enable-row-hover el-table--medium">
            <table cellspacing="0" style="width: 100%;">
              <thead>
                <tr>
                  <th class="el-table__cell el-table__cell is-leaf">
                    <div class="cell">盘符路径</div>
                  </th>
                  <th class="el-table__cell is-leaf">
                    <div class="cell">文件系统</div>
                  </th>
                  <th class="el-table__cell is-leaf">
                    <div class="cell">盘符类型</div>
                  </th>
                  <th class="el-table__cell is-leaf">
                    <div class="cell">总大小</div>
                  </th>
                  <th class="el-table__cell is-leaf">
                    <div class="cell">可用大小</div>
                  </th>
                  <th class="el-table__cell is-leaf">
                    <div class="cell">已用大小</div>
                  </th>
                  <th class="el-table__cell is-leaf">
                    <div class="cell">已用百分比</div>
                  </th>
                </tr>
              </thead>
              <tbody v-if="server.sysFiles">
                <tr v-for="(sysFile, index) in server.sysFiles" :key="index">
                  <td class="el-table__cell is-leaf">
                    <div class="cell">{{ sysFile.dirName }}</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell">{{ sysFile.sysTypeName }}</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell">{{ sysFile.typeName }}</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell">{{ sysFile.total }}</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell">{{ sysFile.free }}</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell">{{ sysFile.used }}</div>
                  </td>
                  <td class="el-table__cell is-leaf">
                    <div class="cell" :class="{ 'text-danger': sysFile.usage > 80 }">{{ sysFile.usage }}%</div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
//import
import { ref,onMounted } from 'vue';
import { getServerConfig } from '@/api/api';

//data
const loading = ref<boolean>(true);
const server = ref({
  "cpu": {
    "cpuNum": 2,
    "total": 199000.0,
    "sys": 0.0,
    "used": 0.0,
    "wait": 0.0,
    "free": 100.0
  },
  "mem": {
    "total": 7.31,
    "used": 3.09,
    "free": 4.22,
    "usage": 42.28
  },
  "jvm": {
    "total": 707.5,
    "max": 1008.0,
    "free": 349.21,
    "version": "1.8.0_111",
    "home": "/usr/java/jdk1.8.0_111/jre",
    "inputArgs": "[-Dname=target/ruoyi-vue.jar, -Duser.timezone=Asia/Shanghai, -Xms512m, -Xmx1024m, -XX:MetaspaceSize=128m, -XX:MaxMetaspaceSize=512m, -XX:+HeapDumpOnOutOfMemoryError, -XX:+PrintGCDateStamps, -XX:+PrintGCDetails, -XX:NewRatio=1, -XX:SurvivorRatio=30, -XX:+UseParallelGC, -XX:+UseParallelOldGC]",
    "runTime": "108天12小时31分钟",
    "startTime": "2025-05-26 10:23:54",
    "used": 358.29,
    "usage": 50.64,
    "name": "Java HotSpot(TM) 64-Bit Server VM"
  },
  "sys": {
    "computerName": "iZwz90rjtrmd83mt2s0df8Z",
    "computerIp": "172.23.12.66",
    "userDir": "/home/ruoyi/projects/ruoyi-vue",
    "osName": "Linux",
    "osArch": "amd64"
  },
  "sysFiles": [
    {
      "dirName": "/",
      "sysTypeName": "ext4",
      "typeName": "/",
      "total": "39.2 GB",
      "free": "24.2 GB",
      "used": "15.0 GB",
      "usage": 38.17
    }
  ]
});
//methods
onMounted(() => {
  initServerConfig();
});

const initServerConfig = () => {
  getServerConfig().then(res => {
    server.value = res.data.data;
  }).catch(e => {
    console.log(e);
  }).finally(() => {
    loading.value = false;
  });
}
</script>

<style scoped lang="css">
.server-container {
  padding: 20px;
}

.card-box {
  margin-bottom: 10px;
}
</style>