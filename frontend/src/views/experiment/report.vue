<template>
  <div class="experiment-report">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>实验报告</span>
          <div class="header-actions">
            <el-button type="primary" @click="handleRefresh">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <!-- 筛选条件 -->
      <div class="filter-section">
        <el-form :inline="true" :model="filterForm">
          <el-form-item label="实验类型">
            <el-select v-model="filterForm.type" placeholder="请选择实验类型" clearable>
              <el-option label="力学实验" value="mechanics" />
              <el-option label="电学实验" value="electricity" />
              <el-option label="光学实验" value="optics" />
              <el-option label="热学实验" value="thermodynamics" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="filterForm.status" placeholder="请选择状态" clearable>
              <el-option label="已提交" value="submitted" />
              <el-option label="已批改" value="graded" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="isTeacher">
            <el-select v-model="filterForm.group" placeholder="请选择小组" clearable>
              <el-option label="231012A" value="231012A" />
              <el-option label="231012B" value="231012B" />
              <el-option label="231012C" value="231012C" />
              <el-option label="231012D" value="231012D" />
              <el-option label="231012E" value="231012E" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleFilter">筛选</el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 报告列表 -->
      <el-table :data="reportList" style="width: 100%" v-loading="loading">
        <el-table-column prop="experimentName" label="实验名称" min-width="200" />
        <el-table-column prop="experimentType" label="实验类型" width="120">
          <template #default="scope">
            <el-tag :type="getTypeTagType(scope.row.experimentType)">
              {{ scope.row.experimentType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="group" label="小组" width="100" />
        <el-table-column prop="submitTime" label="提交时间" width="150" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="getStatusTagType(scope.row.status)">
              {{ scope.row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="score" label="成绩" width="80">
          <template #default="scope">
            <span v-if="scope.row.score" :style="{ color: getScoreColor(scope.row.score) }">
              {{ scope.row.score }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="comment" label="评语" min-width="150" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="scope">
            <el-button 
              type="primary" 
              size="small" 
              @click="handleViewReport(scope.row)"
            >
              查看
            </el-button>
            <el-button 
              v-if="isTeacher && scope.row.status === 'submitted'"
              type="warning" 
              size="small" 
              @click="handleGradeReport(scope.row)"
            >
              批改
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 报告详情对话框 -->
    <el-dialog
      v-model="reportDialogVisible"
      title="实验报告详情"
      width="80%"
      top="5vh"
    >
      <div v-if="currentReport" class="report-detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="实验名称">
            {{ currentReport.experimentName }}
          </el-descriptions-item>
          <el-descriptions-item label="实验类型">
            {{ currentReport.experimentType }}
          </el-descriptions-item>
          <el-descriptions-item label="小组">
            {{ currentReport.group }}
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">
            {{ currentReport.submitTime }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusTagType(currentReport.status)">
              {{ currentReport.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="成绩">
            <span v-if="currentReport.score" :style="{ color: getScoreColor(currentReport.score) }">
              {{ currentReport.score }}
            </span>
            <span v-else>-</span>
          </el-descriptions-item>
        </el-descriptions>
        
        <div class="report-content">
          <h4>实验目的</h4>
          <p>{{ currentReport.objective }}</p>
          
          <h4>实验原理</h4>
          <p>{{ currentReport.principle }}</p>
          
          <h4>实验步骤</h4>
          <p>{{ currentReport.procedure }}</p>
          
          <h4>实验数据</h4>
          <p>{{ currentReport.data }}</p>
          
          <h4>结果分析</h4>
          <p>{{ currentReport.analysis }}</p>
          
          <h4>实验结论</h4>
          <p>{{ currentReport.conclusion }}</p>
        </div>
      </div>
    </el-dialog>

    <!-- 批改对话框 -->
    <el-dialog
      v-model="gradeDialogVisible"
      title="批改实验报告"
      width="600px"
    >
      <el-form :model="gradeForm" label-width="100px">
        <el-form-item label="成绩">
          <el-input-number 
            v-model="gradeForm.score" 
            :min="0" 
            :max="100" 
            :precision="1"
            style="width: 200px"
          />
        </el-form-item>
        
        <el-form-item label="评语">
          <el-input
            v-model="gradeForm.comment"
            type="textarea"
            :rows="4"
            placeholder="请输入评语"
          />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="gradeDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSaveGrade">保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const reportDialogVisible = ref(false)
const gradeDialogVisible = ref(false)
const currentReport = ref(null)

// 用户信息
const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || '{}'))
const isTeacher = computed(() => userInfo.value.userType === 'teacher')
const isStudent = computed(() => userInfo.value.userType === 'student')

// 筛选表单
const filterForm = reactive({
  type: '',
  status: '',
  group: ''
})

// 分页信息
const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

// 批改表单
const gradeForm = reactive({
  reportId: null,
  score: 0,
  comment: ''
})

// 报告列表数据
const reportList = ref([
  {
    reportId: 1,
    experimentName: '力学实验一：测量重力加速度',
    experimentType: '力学实验',
    group: '231012A',
    submitTime: '2024-01-15 14:30',
    status: 'submitted',
    score: null,
    comment: '',
    objective: '通过单摆法测量重力加速度，验证理论值与实验值的一致性。',
    principle: '单摆的周期公式：T = 2π√(L/g)，其中L为摆长，g为重力加速度。',
    procedure: '1. 调节单摆长度\n2. 测量摆动周期\n3. 记录实验数据\n4. 计算重力加速度',
    data: '摆长L = 1.000m\n周期T = 2.006s\n计算得g = 9.81m/s²',
    analysis: '实验测得重力加速度为9.81m/s²，与理论值9.80m/s²基本一致。',
    conclusion: '通过本次实验，成功测量了重力加速度，验证了单摆理论的正确性。'
  },
  {
    reportId: 2,
    experimentName: '电学实验二：欧姆定律验证',
    experimentType: '电学实验',
    group: '231012B',
    submitTime: '2024-01-20 16:45',
    status: 'graded',
    score: 88,
    comment: '实验数据记录完整，分析合理',
    objective: '验证欧姆定律，测量电阻值，学习电路连接和测量方法。',
    principle: '欧姆定律：I = U/R',
    procedure: '1. 连接电路\n2. 调节电压\n3. 测量电流\n4. 记录数据',
    data: '电压U = 5.0V\n电流I = 0.5A\n电阻R = 10.0Ω',
    analysis: '实验验证了欧姆定律的正确性，测量误差在允许范围内。',
    conclusion: '通过本次实验，成功验证了欧姆定律，掌握了基本电路测量方法。'
  }
])

// 获取实验类型标签样式
const getTypeTagType = (type) => {
  const typeMap = {
    '力学实验': 'primary',
    '电学实验': 'success',
    '光学实验': 'warning',
    '热学实验': 'danger'
  }
  return typeMap[type] || 'info'
}

// 获取状态标签样式
const getStatusTagType = (status) => {
  const statusMap = {
    'submitted': 'warning',
    'graded': 'success'
  }
  return statusMap[status] || 'info'
}

// 获取成绩颜色
const getScoreColor = (score) => {
  if (score >= 90) return '#67C23A'
  if (score >= 80) return '#409EFF'
  if (score >= 70) return '#E6A23C'
  return '#F56C6C'
}

// 筛选
const handleFilter = () => {
  loading.value = true
  setTimeout(() => {
    loading.value = false
    ElMessage.success('筛选完成')
  }, 1000)
}

// 重置筛选
const handleReset = () => {
  filterForm.type = ''
  filterForm.status = ''
  filterForm.group = ''
  handleFilter()
}

// 刷新
const handleRefresh = () => {
  loading.value = true
  setTimeout(() => {
    loading.value = false
    ElMessage.success('刷新完成')
  }, 1000)
}

// 查看报告
const handleViewReport = (row) => {
  currentReport.value = row
  reportDialogVisible.value = true
}

// 批改报告
const handleGradeReport = (row) => {
  gradeForm.reportId = row.reportId
  gradeForm.score = 0
  gradeForm.comment = ''
  gradeDialogVisible.value = true
}

// 保存批改
const handleSaveGrade = () => {
  ElMessage.success('批改成功')
  gradeDialogVisible.value = false
  
  // 更新报告状态
  const report = reportList.value.find(r => r.reportId === gradeForm.reportId)
  if (report) {
    report.score = gradeForm.score
    report.comment = gradeForm.comment
    report.status = 'graded'
  }
}

// 分页大小改变
const handleSizeChange = (size) => {
  pagination.size = size
  pagination.current = 1
  handleFilter()
}

// 当前页改变
const handleCurrentChange = (current) => {
  pagination.current = current
  handleFilter()
}

onMounted(() => {
  pagination.total = reportList.value.length
})
</script>

<style scoped>
.experiment-report {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.filter-section {
  margin-bottom: 20px;
  padding: 20px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.pagination-wrapper {
  margin-top: 20px;
  text-align: right;
}

.report-detail {
  padding: 20px 0;
}

.report-content {
  margin-top: 20px;
}

.report-content h4 {
  margin: 20px 0 10px 0;
  color: #303133;
  font-size: 16px;
}

.report-content p {
  color: #606266;
  line-height: 1.6;
  margin-bottom: 15px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style> 