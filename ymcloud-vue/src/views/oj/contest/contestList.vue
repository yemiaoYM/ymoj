<template>
  <div class="container">
    <x-flex vertical style="width: 100%;height: 100%;">
      <!-- 比赛查询模块 -->
      <el-card>
        <template #header>
          <x-flex align="center">
            <el-icon :size="24" color="#409eff">
              <Promotion />
            </el-icon>
            <span style="font-size: 20px; font-weight: 600; margin-left: 8px;">比赛查询</span>
          </x-flex>
        </template>
        <el-input v-model="keyword" placeholder="请输入搜索比赛名称" size="large" @keyup.enter="handleSearch" clearable>
          <template #append>
            <el-button @click="handleSearch" :icon="Search"></el-button>
          </template>
        </el-input>
      </el-card>

      <!-- 比赛列表模块 -->
      <el-card>
        <template #header>
          <x-flex align="center">
            <el-icon :size="24" color="#409eff">
              <List />
            </el-icon>
            <span style="font-size: 20px; font-weight: 600; margin-left: 8px;">比赛列表</span>
          </x-flex>
        </template>
        <el-skeleton v-if="ContestListLoading" :rows="20" :animated="true" />
        <!-- 比赛列表 -->
        <el-card v-for="competition in competitions" :key="competition.id"
          style="width: 100%;margin-bottom: 10px;border-radius: 10px;" :style="{
            borderLeft: competition.status === '已结束' ? '4px solid #aab4bf' : '4px solid #25bb9b'
          }" shadow="hover">
          <template #header>
            <!-- 比赛名称和进行状态 -->
            <x-flex justify="space-between" align="center">
              <x-flex>
                <span style="font-size: 20px; font-weight: 600;cursor: pointer;"
                  @click="toContestDetail(competition.id)">{{ competition.title }}</span>
                <el-tag v-if="competition.visibility === 0" disable-transitions size="large" effect="plain"
                  type="success">公开赛</el-tag>
                <el-tag v-else disable-transitions size="large" effect="plain" type="danger">私有赛</el-tag>
              </x-flex>
              <x-flex align="center">
                <el-tooltip effect="light" content="需要密码才能加入比赛" placement="top">
                  <el-icon v-show="competition.visibility" size="20">
                    <Lock />
                  </el-icon>
                </el-tooltip>
                <el-button :type="getContestStatusType(competition.status)" round style="width: 120px;height: 40px;"
                  @click="toContestDetail(competition.id)">
                  {{ competition.status }}
                </el-button>
              </x-flex>
            </x-flex>
          </template>
          <x-flex align="center">
            <!-- 比赛图片 -->
            <el-image class="contest-image" lazy :alt="competition.title" :src="competition.image || ''" />
            <!-- 比赛信息 -->
            <x-flex vertical size="large">
              <!-- 标签 -->
              <x-flex :size="10">
                <el-tag size="large" disable-transitions color="#ffc171" style="color: white;">
                  <span style="display: flex;align-items: center;">
                    <el-icon>
                      <Trophy />
                    </el-icon>{{ formatContestMode(competition.mode) }}
                  </span>
                </el-tag>
                <el-tag size="large" disable-transitions color="#409eff"
                  style="color: white;">{{ formatContestStyle(competition.style) }}</el-tag>
                <el-tag size="large" disable-transitions color="#25bb9b" style="color: white;">
                  {{ competition.sourceId === 0 ? '原创' : '重建赛' }}
                </el-tag>
                <el-tag size="large" v-show="competition.rated" disable-transitions color="#fe8053"
                  style="color: white;">
                  <span style="display: flex;align-items: center;">
                    <el-icon>
                      <Flag />
                    </el-icon>Rated</span>
                </el-tag>
              </x-flex>
              <x-flex align="center">
                <el-icon size="20">
                  <Clock />
                </el-icon>比赛时间
                <span>{{ competition.startTime + " ~ " + competition.endTime }}</span>
              </x-flex>
              <x-flex>
                <el-icon size="20">
                  <AlarmClock />
                </el-icon>比赛时长
                <span>{{ formatContestDuration(competition.duration) }}</span>
              </x-flex>
              <x-flex align="center">
                <el-icon size="20">
                  <CollectionTag />
                </el-icon>主办方
                <el-tag round size="large" disable-transitions color="#15bec4" style="color: white;font-size: 13px;">
                  {{ competition.creatorUsername }}</el-tag>
              </x-flex>
              <x-flex v-if="competition.visibility !== 0" align="center">
                <el-icon size="20">
                  <User />
                </el-icon>已报名
                <el-tag round effect="plain" disable-transitions style="font-size: 13px;"> × {{
                  competition.participantCount
                  }}</el-tag>
              </x-flex>
            </x-flex>
          </x-flex>
        </el-card>
      </el-card>
      <!-- 分页 -->
      <el-card>
        <x-flex justify="center">
          <el-pagination background layout="total, sizes, prev, pager, next" :page-sizes="[5, 10]" :total="total"
            :page-size="pageSize" :current-page="pageNo" @update:current-page="handleCurrentChange"
            @update:page-size="handleSizeChange" />
        </x-flex>
      </el-card>
    </x-flex>
  </div>
</template>


<script setup lang="ts">
//import
import { ref, onMounted } from 'vue';
import { Search, User } from '@element-plus/icons-vue';
import type { ContestTableVO } from '@/type/types';
import { useRouter } from 'vue-router';
import { getContestTableList } from '@/api/api';
import { formatContestMode, formatContestStyle, formatContestDuration, getContestStatusType } from '@/utils/utils';
import defaultContest from '@/assets/image/defaultContest.jpg';

//data
const router = useRouter();
const competitions = ref<ContestTableVO[]>([] as ContestTableVO[]);
const ContestListLoading = ref<boolean>(false);
const keyword = ref<string>('');
const pageNo = ref<number>(1);
const pageSize = ref<number>(5);
const total = ref<number>(0);

//methods
onMounted(() => {
  page();
})
const page = () => {
  ContestListLoading.value = true;
  getContestTableList(keyword.value, pageNo.value, pageSize.value).then(res => {
    competitions.value = res.data.data.records;
    total.value = res.data.data.total;
    competitions.value.forEach(e => {
      if (e.image === null) {
        e.image = defaultContest;
      } else {
        e.image = import.meta.env.VITE_API_URL + e.image;
      }
    })
  }).catch(err => {
    console.log(err);
  }).finally(() => {
    ContestListLoading.value = false;
  })
}
const handleSizeChange = (val: number) => {
  pageSize.value = val;
  page();
}
const handleCurrentChange = (val: number) => {
  pageNo.value = val;
  page();
}
const handleSearch = () => {
  console.log('Searching for:', keyword.value);
  page();
};
const toContestDetail = (contestID: number) => {
  router.push({ name: 'ContestDetail', params: { contestID: contestID } })
}
</script>

<style lang="css" scoped>
.contest-image {
  width: 150px;
  flex-shrink: 0;
}

.container {
  height: 100%;
  width: 100%;
  display: flex;
  margin: 0 auto;
  gap: 10px;
}

@media screen and (min-width: 1024px) {
  .container {
    padding: 15px 5%;
  }
}
</style>
