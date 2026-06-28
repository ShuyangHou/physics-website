<template>
  <div v-if="!item.hidden && hasPermission(item)">
    <template v-if="!item.children">
      <el-menu-item :index="'/' + item.path">
        <el-icon v-if="item.meta && item.meta.icon">
          <component :is="item.meta.icon" />
        </el-icon>
        <template #title>{{ item.meta.title }}</template>
      </el-menu-item>
    </template>

    <el-sub-menu v-else :index="'/' + item.path">
      <template #title>
        <el-icon v-if="item.meta && item.meta.icon">
          <component :is="item.meta.icon" />
        </el-icon>
        <span>{{ item.meta.title }}</span>
      </template>
      <sidebar-item
        v-for="child in item.children"
        :key="child.path"
        :item="child"
      />
    </el-sub-menu>
  </div>
</template>

<script setup>
import { hasPermission } from '@/utils/permission'

defineProps({
  item: {
    type: Object,
    required: true
  }
})
</script> 