<template>
  <div class="student-trace">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>学生上课查询</span>
        </div>
      </template>

      <div class="filter-section">
        <el-form :inline="true" :model="form">
          <el-form-item label="学号">
            <el-input v-model="form.schoolId" placeholder="请输入学号" clearable style="width: 220px" />
          </el-form-item>
          <el-form-item label="学期">
            <el-select v-model="form.semesterId" placeholder="全部" clearable style="width: 200px" filterable>
              <el-option v-for="s in semesterList" :key="s.semesterId" :label="s.semesterName" :value="s.semesterId" />
            </el-select>
          </el-form-item>
          <el-form-item label="实验套">
            <el-select v-model="form.suiteId" placeholder="全部" clearable style="width: 200px" filterable>
              <el-option v-for="s in suiteList" :key="s.suiteId" :label="s.suiteName" :value="s.suiteId" />
            </el-select>
          </el-form-item>
          <el-form-item label="单双周">
            <el-select v-model="form.weekType" placeholder="全部" clearable style="width: 140px">
              <el-option label="单周" :value="0" />
              <el-option label="双周" :value="1" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" @click="handleSearch">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table :data="rows" stripe v-loading="loading" style="width: 100%">
        <el-table-column prop="schoolId" label="学号" width="120" />
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column prop="classId" label="班级" width="120" />
        <el-table-column prop="groupName" label="小组" width="120" />
        <el-table-column prop="semesterName" label="学期" min-width="160" />
        <el-table-column prop="weekTypeLabel" label="周类型" width="90" />
        <el-table-column prop="experimentTime" label="时间段" min-width="160" />
        <el-table-column prop="teachers" label="任课老师" min-width="160" />
      </el-table>

      <div v-if="!loading && rows.length === 0" style="padding: 18px 0;">
        <el-empty description="暂无数据" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getStudentTrace } from '@/api/schedule'
import { getSemesterList } from '@/api/semester'
import { getAllSuites } from '@/api/suite'

const loading = ref(false)
const rows = ref([])

const form = reactive({
  schoolId: '',
  semesterId: null,
  suiteId: null,
  weekType: null
})

const semesterList = ref([])
const suiteList = ref([])

const loadSemesters = async () => {
  try {
    const resp = await getSemesterList({ current: 1, size: 100 })
    if (resp?.code === 200) {
      semesterList.value = resp.data?.records || []
    }
  } catch (e) {
    // ignore
  }
}

const loadSuites = async () => {
  try {
    const resp = await getAllSuites()
    if (resp?.code === 200) {
      suiteList.value = resp.data || []
    }
  } catch (e) {
    // ignore
  }
}

const handleSearch = async () => {
  if (!form.schoolId || !String(form.schoolId).trim()) {
    ElMessage.warning('请输入学号')
    return
  }
  loading.value = true
  try {
    const resp = await getStudentTrace({
      schoolId: String(form.schoolId).trim(),
      semesterId: form.semesterId,
      suiteId: form.suiteId,
      weekType: form.weekType
    })
    if (resp?.code === 200) {
      rows.value = resp.data || []
    } else {
      ElMessage.error(resp?.message || '查询失败')
    }
  } catch (e) {
    ElMessage.error(e?.message || '查询失败')
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  form.schoolId = ''
  form.semesterId = null
  form.suiteId = null
  form.weekType = null
  rows.value = []
}

onMounted(() => {
  loadSemesters()
  loadSuites()
})
</script>

<style scoped>
.filter-section {
  padding: 12px 0 2px 0;
}
</style>
