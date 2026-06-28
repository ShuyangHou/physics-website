<template>
  <div class="experiment-schedule">
    <el-card class="full-width-card" shadow="hover" v-if="isAdmin || isTeacher">
      <template #header>
        <div class="card-header">
          <span>{{ readonly ? '实验课表' : '自动分组编排（日历）' }}</span>
          <div class="intro-notice" v-if="readonly">
            <el-tag type="warning" size="small">
              <el-icon><Star /></el-icon>
              第一周为绪论课
            </el-tag>
          </div>
          <div class="header-actions">
            <!-- 学期选择器 -->
            <el-select v-model="semesterId" placeholder="选择学期" style="width: 180px" @change="handleSemesterChange">
              <el-option 
                v-for="sem in semesterOptions" 
                :key="sem.semesterId" 
                :label="sem.semesterName" 
                :value="sem.semesterId"
              />
            </el-select>
            
            <el-select v-model="selectedSuiteId" placeholder="选择实验套" style="width: 220px" @change="handleSuiteChange" :disabled="suiteOptions.length === 0">
              <el-option :key="0" :label="'全部实验套'" :value="null" />
              <el-option 
                v-for="s in suiteOptions" 
                :key="s.experimentSuiteId || s.suiteId" 
                :label="s.suiteName" 
                :value="s.experimentSuiteId || s.suiteId" 
              />
            </el-select>
            <span v-if="suiteOptions.length === 0" style="color: #667085; font-size: 14px; margin-left: 10px;">
              暂无实验套
            </span>
            <!-- 单/双周切换 -->
            <el-radio-group v-model="weekType" size="large" @change="handleWeekTypeChange">
              <el-radio-button :label="0">单周</el-radio-button>
              <el-radio-button :label="1">双周</el-radio-button>
            </el-radio-group>
            
            <el-button type="primary" @click="handleCalendarReset" v-if="isAdmin && !readonly" size="large">
              <el-icon><Refresh /></el-icon>
              清空当前输入
            </el-button>
            
            <!-- 打印课表按钮 -->
            <el-divider direction="vertical" />
            <el-button 
              :type="printButtonLoading ? 'warning' : 'info'" 
              @click="openPrintScheduleDialog" 
              size="large"
              :loading="printButtonLoading"
              :disabled="printButtonLoading"
            >
              <el-icon><Printer /></el-icon>
              {{ printButtonLoading ? '准备中...' : '打印课表' }}
            </el-button>

            <el-button type="success" size="large" @click="handleExportScheduleExcel" :loading="exportingSchedule">
              导出Excel
            </el-button>
            

          </div>
        </div>
      </template>

      <div class="calendar-grid">
        <div class="calendar-header"></div>
        <div class="calendar-header" v-for="d in weekdays" :key="d">{{ d }}</div>

        <template v-for="slot in timeSlots" :key="slot">
          <div class="calendar-slot-label">{{ slot }}</div>
          <div
            v-for="d in weekdays"
            :key="d + '-' + slot"
            class="calendar-cell"
          >
            <div class="cell-columns" :class="{ single: !readonly }">
              <!-- 左侧：班级分组 -->
              <div class="class-box">
                <div class="box-title">班级</div>
                <template v-if="!cellState[d][slot].grouped">
                  <div v-if="isAdmin && !readonly" class="cell-inputs">
                    <!-- 班级标签输入：一个框一个班 -->
                    <div class="class-tags big">
                      <el-tag
                        v-for="(cls, idx) in cellState[d][slot].classList"
                        :key="cls + '-' + idx"
                        type="info"
                        round
                        closable
                        @close="removeClassTag(d, slot, idx)"
                      >
                        {{ cls }}
                      </el-tag>
                      <el-input
                        v-model="cellState[d][slot].classInput"
                        size="large"
                        placeholder="输入班级后回车添加"
                        class="class-input big"
                        @keyup.enter.native="addClassTag(d, slot)"
                        @blur="addClassTag(d, slot, true)"
                        clearable
                      />
      </div>

                    <div class="cell-actions">
                      <el-button type="primary" size="small" @click="handleCellGroup(d, slot)" :loading="cellState[d][slot].loading">
                        一键分组
                      </el-button>
                      <el-button size="small" type="primary" plain @click="openBatchDialog(d, slot)">手动分组</el-button>
                    </div>
                  </div>
                  <div v-else-if="readonly" class="cell-readonly">
                    <div v-if="cellState[d][slot].groupNames && cellState[d][slot].groupNames.length > 0" class="group-results">
                      <div v-for="group in cellState[d][slot].groupNames" :key="group" class="group-chip">
                        {{ group }}
                      </div>
                    </div>
                    <div v-else class="no-data-text">暂无安排</div>
                  </div>
                  <div v-else class="cell-readonly">待编排</div>
            </template>
                <template v-else>
                  <div class="group-results">
                    <div v-for="(g, idx) in cellState[d][slot].groupNames" :key="g + idx" class="group-chip">{{ g }}</div>
      </div>
                  <div class="cell-actions" v-if="isAdmin && !readonly">
                    <div class="cell-actions-left">
                      <el-button size="small" @click="handleCellRegroup(d, slot)">重新分组</el-button>
                    </div>
                    <div class="cell-actions-right">
                      <el-button size="small" type="primary" @click="openBatchDialog(d, slot)">批量设置</el-button>
                    </div>
                  </div>
            </template>
              </div>

              <!-- 右侧：教师选择/展示（管理员视图暂不渲染） -->
              <div class="teacher-box" v-if="readonly">
                <div class="box-title teacher-label">教师</div>
                <div v-if="isAdmin && !readonly" class="teacher-inputs compact">
                  <!-- 已选教师标签展示（绿色） -->
                  <div class="teacher-tags">
                    <el-tag
                      v-for="(t, idx) in cellState[d][slot].teacherTags"
                      :key="String(t.id ?? t.name)"
                      type="success"
                      round
                      closable
                      @close="handleRemoveTeacherTag(d, slot, idx)"
                    >
                      {{ t.name || '未命名' }}
                    </el-tag>
                  </div>
                  <!-- 仅显示一个小按钮，点击后在浮层中选择 -->
                  <el-popover
                    placement="bottom-start"
                    width="280"
                    trigger="click"
                    v-model:visible="cellState[d][slot].teacherPickerVisible"
                  >
                    <template #reference>
                      <el-button size="small" type="primary" plain>选择</el-button>
            </template>
                    <div class="teacher-picker">
                      <el-checkbox-group v-model="cellState[d][slot].tempTeacherIds" class="teacher-checks">
                        <el-checkbox
                          v-for="t in teacherList"
                          :key="t.userId"
                          :label="t.userId"
                        >{{ t.realName }}</el-checkbox>
                      </el-checkbox-group>
                      <div class="picker-actions">
                        <el-button size="small" @click="cellState[d][slot].teacherPickerVisible = false">取消</el-button>
                        <el-button size="small" type="primary" @click="confirmTeacherPick(d, slot)">确定</el-button>
                      </div>
                    </div>
                  </el-popover>
                </div>
                <div v-else class="teacher-tags readonly">
                  <template v-if="(cellState[d][slot].teacherTags || []).length">
                    <el-tag
                      v-for="(t, idx) in cellState[d][slot].teacherTags"
                      :key="'ro-' + String(t.id ?? t.name) + '-' + idx"
                      :type="t.isIntroTeacher ? 'warning' : 'success'"
                      round
                    >
                      <el-icon v-if="t.isIntroTeacher"><Star /></el-icon>
                      {{ t.name || '未命名' }}
                      <span v-if="t.isIntroTeacher" class="intro-label">（绪论课）</span>
                    </el-tag>
            </template>
                  <template v-else>
                    <span class="teacher-empty">未设置</span>
                  </template>
      </div>
              </div>
            </div>
      </div>
        </template>
      </div>
    </el-card>

    <!-- 新增/编辑课表对话框 -->
    <el-dialog
      v-model="scheduleDialogVisible"
      :title="dialogTitle"
      width="600px"
    >
      <el-form :model="scheduleForm" label-width="100px">
        <el-form-item label="实验项目">
          <el-select v-model="scheduleForm.experimentId" placeholder="请选择实验项目" style="width: 100%" v-if="scheduleOptionsReady">
            <el-option
              v-for="item in experimentList"
              :key="item.experimentId"
              :label="item.experimentName"
              :value="item.experimentId"
            />
          </el-select>
        </el-form-item>
        
        <el-form-item label="上课教师">
          <el-select v-model="scheduleForm.teacherIds" placeholder="请选择上课教师" multiple style="width: 100%" v-if="scheduleOptionsReady">
            <el-option
              v-for="item in teacherList"
              :key="item.userId"
              :label="item.realName"
              :value="item.userId"
            />
          </el-select>
        </el-form-item>
        
        <el-form-item label="实验室">
          <el-select v-model="scheduleForm.location" placeholder="请选择实验室" style="width: 100%" v-if="scheduleOptionsReady">
            <el-option label="物理楼101" value="物理楼101" />
            <el-option label="物理楼102" value="物理楼102" />
            <el-option label="物理楼103" value="物理楼103" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="上课小组">
          <el-select v-model="scheduleForm.groupIds" placeholder="请选择上课小组" multiple style="width: 100%" v-if="scheduleOptionsReady">
            <el-option
              v-for="item in groupList"
              :key="item.groupId"
              :label="item.groupName"
              :value="item.groupId"
            />
          </el-select>
        </el-form-item>
        
        <el-form-item label="实验时间">
          <el-row :gutter="10">
            <el-col :span="8">
              <el-select v-model="scheduleForm.week" placeholder="第几周" style="width: 100%" v-if="scheduleOptionsReady">
                <el-option
                  v-for="week in totalWeeks"
                  :key="week"
                  :label="`第${week}周`"
                  :value="week"
                />
              </el-select>
            </el-col>
            <el-col :span="8">
              <el-select v-model="scheduleForm.weekday" placeholder="星期几" style="width: 100%" v-if="scheduleOptionsReady">
                <el-option label="周一" value="周一" />
                <el-option label="周二" value="周二" />
                <el-option label="周三" value="周三" />
                <el-option label="周四" value="周四" />
                <el-option label="周五" value="周五" />
                <el-option label="周六" value="周六" />
                <el-option label="周日" value="周日" />
              </el-select>
            </el-col>
            <el-col :span="8">
              <el-select v-model="scheduleForm.timeSlot" placeholder="时间段" style="width: 100%" v-if="scheduleOptionsReady">
                <el-option label="上午" value="上午" />
                <el-option label="下午" value="下午" />
              </el-select>
            </el-col>
          </el-row>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="scheduleDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSaveSchedule">保存</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 批量设置对话框 -->
    <el-dialog v-model="batchDialogVisible" title="批量设置（按小组逐周配置）" width="900px">
      <el-form label-width="90px">
        <el-form-item label="小组">
          <el-select v-model="batchForm.singleGroup" placeholder="选择小组" style="width:100%" v-if="batchOptionsReady">
            <el-option
              v-for="g in (cellState[batchContext.weekday]?.[batchContext.slot]?.groupNames || [])"
              :key="g"
              :label="g"
              :value="g"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="周次范围">
          <el-radio-group v-model="batchForm.weekMode">
            <el-radio-button label="odd">单周</el-radio-button>
            <el-radio-button label="even">双周</el-radio-button>
          </el-radio-group>
          <el-button size="small" style="margin-left:8px" @click="applyWeekMode">应用</el-button>
          <span style="margin-left:8px;color:#667085">可在下表逐行微调</span>
        </el-form-item>

        <el-form-item label="实验套">
          <el-select v-model="batchForm.filterSuiteId" placeholder="筛选实验范围" clearable filterable style="width:100%">
            <el-option v-for="s in suiteOptions" :key="s.experimentSuiteId || s.suiteId" :label="s.suiteName" :value="(s.experimentSuiteId || s.suiteId)" />
          </el-select>
        </el-form-item>

        <el-alert type="info" show-icon :closable="false" style="margin-bottom: 12px" title="为所选小组设置第几周做哪个实验、由谁授课。未选择的行不提交。"/>
        

        <el-table :key="batchDialogInstanceKey" :data="groupPlan" :row-key="row => row.week" border size="small" style="width: 100%">
          <el-table-column prop="week" label="周次" width="80">
            <template #default="scope">第{{ scope.row.week }}周</template>
          </el-table-column>
          <el-table-column label="启用" width="80">
            <template #default="scope">
              <el-checkbox v-model="scope.row.enabled" @change="() => onTogglePlanRow(scope.row)"/>
            </template>
          </el-table-column>
          <el-table-column label="实验">
            <template #default="scope">
              <el-select
                v-if="scope.row.enabled"
                :key="String(batchDialogInstanceKey) + '|' + String(batchForm.singleGroup || '') + '|' + String(batchContext.weekday || '') + '|' + String(batchContext.slot || '') + '|' + String(scope.row.week)"
                v-model="scope.row.experimentId"
                placeholder="选择实验"
                clearable
                filterable
                style="width:100%"
              >
                <el-option v-for="e in availableExperimentsFor(scope.row)" :key="e.experimentId" :label="e.experimentName" :value="e.experimentId"/>
              </el-select>
              <span v-else style="color:#c0c4cc;">-</span>
            </template>
          </el-table-column>
          <el-table-column label="教师" width="220">
            <template #default="scope">
              <el-select
                v-if="scope.row.enabled"
                :key="String(batchDialogInstanceKey) + '|' + String(batchForm.singleGroup || '') + '|' + String(batchContext.weekday || '') + '|' + String(batchContext.slot || '') + '|' + String(scope.row.week) + '|teacher'"
                v-model="scope.row.teacherId"
                placeholder="选择教师"
                clearable
                filterable
                style="width:100%"
              >
                <el-option v-for="t in teacherList" :key="t.userId" :label="t.realName" :value="String(t.userId)"/>
              </el-select>
              <span v-else style="color:#c0c4cc;">-</span>
            </template>
          </el-table-column>
        </el-table>

        <el-divider />
        <div style="display:flex;align-items:center;gap:10px;margin:8px 0 12px;">
          <span style="font-weight:600;">手动分组</span>
          <el-tooltip content="即使已自动分组，也可在此按班级手动挑选学生并设置组号" placement="top">
            <el-icon><InfoFilled /></el-icon>
          </el-tooltip>
        </div>
        <el-form-item label="班级号">
          <el-select v-model="manualForm.classIds" multiple filterable clearable placeholder="选择班级" style="width: 420px" collapse-tags>
            <el-option v-for="c in manualClassOptions" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
          <el-button style="margin-left: 8px" type="primary" @click="loadManualStudents">查询</el-button>
        </el-form-item>
        <el-form-item label="组号">
          <el-input v-model="manualForm.groupName" placeholder="输入组号/组名，如 231011A" clearable style="width: 260px" />
          <el-button style="margin-left: 8px" type="success" :disabled="!manualForm.groupName || manualSelectedIds.length===0" @click="submitManualGrouping">应用到分组</el-button>
        </el-form-item>
        
        <!-- 批量选择控制 -->
        <el-form-item label="批量选择" v-if="manualStudents.length > 0">
          <div style="display: flex; flex-direction: column; gap: 12px;">
            <!-- 选择统计 -->
            <div style="display: flex; align-items: center; gap: 16px; padding: 8px 12px; background: #f8f9fa; border-radius: 6px;">
              <span style="color: #666;">
                已选择: <strong style="color: #409eff;">{{ manualSelectedIds.length }}</strong> 人
              </span>
              <span style="color: #666;">
                总人数: <strong>{{ manualStudents.length }}</strong> 人
              </span>
              <el-button size="small" @click="clearAllSelection" type="danger" plain>
                清空选择
              </el-button>
            </div>
            
            <!-- 批量选择方式 -->
            <div style="display: flex; align-items: center; gap: 12px; flex-wrap: wrap;">
              <!-- 方式1: 范围选择 -->
              <div style="display: flex; align-items: center; gap: 8px;">
                <el-button 
                  type="primary" 
                  @click="startRangeSelection" 
                  :disabled="rangeSelectMode"
                  size="small"
                >
                  <el-icon><Select /></el-icon>
                  范围选择
                </el-button>
                <el-button 
                  type="success" 
                  @click="confirmRangeSelection" 
                  :disabled="!rangeSelectMode || rangeStart === null || rangeEnd === null"
                  size="small"
                >
                  确认范围
                </el-button>
                <el-button 
                  @click="cancelRangeSelection" 
                  :disabled="!rangeSelectMode"
                  size="small"
                >
                  取消
                </el-button>
              </div>
              
              <!-- 方式2: 快速选择 -->
              <div style="display: flex; align-items: center; gap: 8px;">
                <el-button 
                  type="warning" 
                  @click="selectAllStudents"
                  size="small"
                >
                  全选
                </el-button>
                <el-button 
                  type="info" 
                  @click="selectByClass"
                  size="small"
                >
                  按班级选择
                </el-button>
              </div>
            </div>
            
            <!-- 范围选择提示 -->
            <div v-if="rangeSelectMode" style="padding: 8px 12px; background: #e6f7ff; border: 1px solid #91d5ff; border-radius: 6px; color: #1890ff;">
              <el-icon><InfoFilled /></el-icon>
              <span v-if="rangeStart === null">
                请点击第一个学生作为开始位置
              </span>
                            <span v-else-if="rangeEnd === null">
                已选择开始位置（第{{ rangeStart + 1 }}个），请点击最后一个学生作为结束位置
              </span>
              <span v-else>
                已选择范围：第{{ Math.min(rangeStart, rangeEnd) + 1 }} 到 第{{ Math.max(rangeStart, rangeEnd) + 1 }} 个学生，
                共 {{ Math.abs(rangeEnd - rangeStart) + 1 }} 人，请点击"确认范围"按钮
              </span>
            </div>
          </div>
        </el-form-item>
        
        <el-table 
          :data="manualStudents" 
          border 
          height="260" 
          @selection-change="onManualSelectionChange" 
          @row-click="onRangeRowClick"
          v-loading="manualLoading" 
          ref="manualStudentsTable"
          :row-class-name="getRangeRowClassName"
        >
          <el-table-column type="selection" width="50" />
          <el-table-column prop="realName" label="姓名" width="140" />
          <el-table-column prop="schoolId" label="学号/工号" width="160" />
          <el-table-column prop="classId" label="班级" width="160" />
          <el-table-column prop="groupName" label="当前小组" />
        </el-table>
        
        <!-- 分组完成后的课表生成区域 -->
        <div v-if="manualGroupingCompleted" class="schedule-generation-section">
          <el-divider />
          <div class="generation-info">
            <el-icon color="#67c23a"><CircleCheckFilled /></el-icon>
            <span>分组已完成！现在可以生成课表了</span>
          </div>
          <div class="generation-actions">
            <el-button type="primary" @click="handleGenerateScheduleFromGroups" :loading="generatingSchedule">
              <el-icon><Star /></el-icon>
              生成课表
            </el-button>
            <el-button @click="resetManualGrouping">重新分组</el-button>
          </div>
        </div>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="batchDialogVisible = false">关闭</el-button>
          <el-button type="primary" @click="saveGroupPlan">保存周次设置</el-button>
          <el-button 
            type="warning" 
            @click="openTeacherSettingDialog"
          >
            <el-icon><User /></el-icon>
            教师设置
          </el-button>
          <el-button 
            type="success" 
            @click="generateScheduleFromBatchDialog" 
            :loading="generatingSchedule"
            :disabled="!hasExistingGroups"
          >
            <el-icon><Star /></el-icon>
            生成课表
          </el-button>
        </span>
      </template>
    </el-dialog>
    
    <!-- 教师设置对话框 -->
    <el-dialog v-model="teacherSettingDialogVisible" title="教师设置（按时间段分配教师）" width="800px">
      <el-form label-width="120px">
        <el-form-item label="时间段">
          <span class="time-slot-display">{{ weekType === 0 ? '单' : '双' }}{{ batchContext.weekday }}{{ batchContext.slot }}</span>
        </el-form-item>
        
        <el-form-item label="实验套">
          <el-input :value="getSuiteName(teacherSettingForm.suiteId)" disabled />
        </el-form-item>
        
        <el-alert type="info" show-icon :closable="false" style="margin-bottom: 12px" title="为每个时间段的实验分配教师。每2个实验分配给一个老师。"/>
        
        <el-table :data="teacherAssignmentPlan" border size="small" style="width: 100%">
          <el-table-column label="实验名称">
            <template #default="scope">
              <div v-for="exp in scope.row.experiments" :key="exp.experimentId" class="experiment-item">
                {{ exp.experimentName }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="负责教师" width="180">
            <template #default="scope">
              <el-select v-model="scope.row.teacherId" placeholder="选择教师" clearable filterable style="width:100%">
                <el-option v-for="t in teacherList" :key="t.userId" :label="t.realName" :value="String(t.userId)"/>
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="是否绪论课" width="120" align="center">
            <template #default="scope">
              <el-checkbox v-model="scope.row.isIntroClass"></el-checkbox>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="teacherSettingDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSaveTeacherAssignment" :loading="savingTeacherAssignment">
            {{ savingTeacherAssignment ? '保存中...' : '保存教师分配' }}
          </el-button>
        </span>
      </template>
    </el-dialog>
    
        <!-- 打印课表选择对话框 -->
    <el-dialog v-model="showPrintScheduleDialog" title="打印课表" width="600px" :close-on-click-modal="false">
      <div class="print-schedule-container">
        <!-- 加载进度条 -->
        <div v-if="printLoading" class="print-loading-section">
          <el-progress 
            :percentage="printProgress" 
            :format="(percentage) => `${percentage}%`"
            :stroke-width="20"
            status="success"
          />
          <div class="progress-text">{{ printProgressText }}</div>
        </div>
        
        <!-- 课表类型选择 -->
        <div v-else class="schedule-type-selector" style="margin-bottom: 20px; text-align: center;">
          <el-radio-group v-model="selectedScheduleType" size="large">
            <el-radio-button label="teacher">教师课表</el-radio-button>
            <el-radio-button label="student">学生课表</el-radio-button>
          </el-radio-group>
        </div>
        
        <!-- 实验套选择 -->
        <div v-if="!printLoading" class="suite-selector" style="margin-bottom: 20px;">
          <el-form :inline="true" style="text-align: center;">
            <el-form-item label="选择实验套：">
              <el-select v-model="selectedPrintSuiteId" placeholder="选择要打印的实验套" style="width: 300px;">
                <el-option label="全部实验套" :value="null" />
                <el-option
                  v-for="suite in suiteOptions"
                  :key="suite.suiteId"
                  :label="suite.suiteName"
                  :value="suite.suiteId"
                />
              </el-select>
            </el-form-item>
          </el-form>
        </div>
        
        <!-- 教师课表打印 -->
        <div v-if="selectedScheduleType === 'teacher' && !printLoading" class="teacher-schedule-print">
          <div class="schedule-print-info">
            <el-alert
              title="教师课表打印说明"
              type="info"
              :closable="false"
              show-icon
            >
              <template #default>
                <p>教师课表将按照单周/双周分组显示</p>
                <p>包含各时间段的教师安排和小组信息</p>
                <p>格式：一行显示两个小组，一行显示一个老师</p>
                <p>黑白表格、字体已放大，适合打印</p>
              </template>
            </el-alert>
          </div>
          
          <div class="schedule-print-actions">
            <el-button type="primary" size="large" @click="refreshPrintData" class="refresh-btn">
              <el-icon><Refresh /></el-icon>
              刷新数据
            </el-button>
            <el-button type="success" size="large" @click="printTeacherSchedule" class="print-btn" :loading="printButtonLoading">
              <el-icon><Printer /></el-icon>
              打印教师课表
            </el-button>
          </div>
        </div>
        
        <!-- 学生课表打印 -->
        <div v-if="selectedScheduleType === 'student' && !printLoading" class="student-schedule-print">
          <div class="schedule-print-info">
            <el-alert
              title="学生课表打印说明"
              type="info"
              :closable="false"
              show-icon
            >
              <template #default>
                <p>学生课表将按照单周/双周分组显示</p>
                <p>包含各小组在不同实验中的安排信息</p>
                <p>按时间段分组：单一上午、单一下午、单二上午等</p>
              </template>
            </el-alert>
          </div>
          
          <div class="schedule-print-actions">
            <el-button type="primary" size="large" @click="refreshPrintData" class="refresh-btn">
              <el-icon><Refresh /></el-icon>
              刷新数据
            </el-button>
            <el-button type="success" size="large" @click="printStudentSchedule" class="print-btn" :loading="printButtonLoading">
              <el-icon><Printer /></el-icon>
              打印学生课表
            </el-button>
          </div>
        </div>
      </div>
    
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showPrintScheduleDialog = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getScheduleList, addSchedule, updateSchedule, deleteSchedule, updateTeachersByTime, updateGroupsByTime, generateScheduleFromGroups, saveTeacherAssignment, getScheduleForDisplay, exportScheduleXlsx } from '@/api/schedule'
import { getExperimentList, getExperimentsBySuite } from '@/api/experiment'
import { getTeacherList, getStudentList } from '@/api/user'
import { getGroupList } from '@/api/group'
import { autoGroupAndUpdate } from '@/api/autoGrouping'
import { getCurrentSemester, getSemesterList, getWeekInfo } from '@/api/semester'
import { getSuiteList, getAllSuites } from '@/api/suite'
import { batchAssignItems, getGroupExperimentByGroup, getGroupExperimentsByTime, getAllGroupExperiments, getStudentSchedule } from '@/api/groupExperiment'
import { assignGroup } from '@/api/user'
import { InfoFilled, CircleCheckFilled, Star, User, Select, Printer, Refresh } from '@element-plus/icons-vue'
import { closePrintWindow, printWhenLoaded, writePrintDocument } from '@/utils/printWindow'

// 定义 props
const props = defineProps({
  readonly: {
    type: Boolean,
    default: false
  },
  semesterId: {
    type: [Number, String],
    default: null
  }
})

const loading = ref(false)
const scheduleDialogVisible = ref(false)
const isEdit = ref(false)
const scheduleOptionsReady = ref(false)
const exportingSchedule = ref(false)

// 用户信息
const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || '{}'))
const isAdmin = computed(() => userInfo.value.userType === 'admin')
const isTeacher = computed(() => userInfo.value.userType === 'teacher')
const isStudent = computed(() => userInfo.value.userType === 'student')
const studentGroupName = computed(() => String(userInfo.value.groupName || '').trim())

// 学期ID，从后端获取当前学期或从props传入
const semesterId = ref(props.semesterId ? Number(props.semesterId) : 0)
const totalWeeks = ref(20)

// 规则B：动态教学周（来自 /semester/week-info）
const teachingWeeksOdd = ref([])
const teachingWeeksEven = ref([])

// 实验套选择（null 表示全部实验套；需在 buildTeacherStoreKey 之前声明）
const selectedSuiteId = ref(null)
const suiteOptions = ref([])

// 学期选项
const semesterOptions = ref([])

// 打印课表相关变量
const showPrintScheduleDialog = ref(false)
const selectedScheduleType = ref('teacher')
const selectedPrintSuiteId = ref(null)
const printLoading = ref(false)
const printProgress = ref(0)
const printProgressText = ref('')
const printButtonLoading = ref(false)


// 学生课表数据
const studentScheduleData = ref({})

// 保存所有group_experiment数据，用于查询教师和绪论课信息
const allGroupExperimentsData = ref([])

// 缓存机制
const dataCache = ref({
  scheduleData: null,
  teacherList: null,
  studentScheduleData: null,
  contextKey: '',
  lastLoadTime: 0
})

// 缓存有效期（30分钟，增加缓存时间）
const CACHE_DURATION = 30 * 60 * 1000

// 预加载标志
const isPreloading = ref(false)
const preloadPromise = ref(null)

const getPrintCacheKey = () => [
  semesterId.value || '',
  selectedPrintScheduleSuiteId() || '',
  selectedScheduleType.value || ''
].join('|')

// 检查缓存是否有效
const isCacheValid = () => {
  return dataCache.value.lastLoadTime > 0 &&
         dataCache.value.contextKey === getPrintCacheKey() &&
         (Date.now() - dataCache.value.lastLoadTime) < CACHE_DURATION
}

// 清除缓存
const clearPrintCache = () => {
  dataCache.value = {
    scheduleData: null,
    teacherList: null,
    studentScheduleData: null,
    contextKey: '',
    lastLoadTime: 0
  }
  preloadPromise.value = null
}

// 预加载打印数据
const preloadPrintData = async () => {
  if (isPreloading.value || isCacheValid()) {
    return preloadPromise.value
  }
  
  isPreloading.value = true
  preloadPromise.value = loadAllDataForPrint(false, true)
  
  try {
    await preloadPromise.value
  } catch (error) {
  } finally {
    isPreloading.value = false
  }
  
  return preloadPromise.value
}

// 刷新打印数据
const refreshPrintData = async () => {
  try {
    clearPrintCache()
    await loadAllDataForPrint(true)
    ElMessage.success('数据已刷新')
  } catch (error) {
    console.error('刷新数据失败:', error)
    ElMessage.error('刷新数据失败')
  }
}

// 异步打印方法，避免阻塞主页面
const asyncPrint = (printWindow, printType) => {
  return new Promise((resolve) => {
    // 使用 requestAnimationFrame 确保不阻塞主线程
    requestAnimationFrame(() => {
      try {
        printWindow.focus()
        
        // 使用 setTimeout 异步调用打印
        setTimeout(() => {
          try {
            printWindow.print()
            
            // 设置超时自动关闭
            setTimeout(() => {
              if (!printWindow.closed) {
                printWindow.close()
              }
              ElMessage.success(`${printType}打印完成`)
              resolve()
            }, 3000) // 3秒后自动关闭
            
          } catch (printError) {
            console.error('打印调用失败:', printError)
            if (!printWindow.closed) {
              printWindow.close()
            }
            ElMessage.error('打印失败，请重试')
            resolve()
          }
        }, 100)
        
      } catch (error) {
        console.error('打印过程出错:', error)
        if (!printWindow.closed) {
          printWindow.close()
        }
        ElMessage.error('打印过程出错，请重试')
        resolve()
      }
    })
  })
}

// 并行加载所有必要数据（按需加载，减少无关数据请求）
const loadAllDataForPrint = async (forceRefresh = false, silent = false) => {
  try {
    if (!silent) {
      printLoading.value = true
      printProgress.value = 0
      printProgressText.value = '准备加载数据...'
    }
    
    // 检查缓存（除非强制刷新）
    if (!forceRefresh && isCacheValid()) {
      // 基于当前选择的课表类型，懒加载缺失部分，避免整包刷新
      const needTeacher = selectedScheduleType.value === 'teacher' && !dataCache.value.teacherList
      const needStudent = selectedScheduleType.value === 'student' && !dataCache.value.studentScheduleData

      scheduleList.value = dataCache.value.scheduleData || []
      teacherList.value = dataCache.value.teacherList || []
      studentScheduleData.value = dataCache.value.studentScheduleData || {}

      if (!needTeacher && !needStudent) {
        if (!silent) {
          printProgress.value = 100
          printProgressText.value = '使用缓存数据'
        }
        return
      }

      const tasks = []
      if (needTeacher) tasks.push(loadTeacherListWithProgress(true).then(d => { teacherList.value = d; dataCache.value.teacherList = d }))
      if (needStudent) tasks.push(loadStudentScheduleDataWithProgress(true).then(d => { studentScheduleData.value = d; dataCache.value.studentScheduleData = d }))
      await Promise.all(tasks)
      if (!silent) {
        printProgress.value = 100
        printProgressText.value = '数据已补齐'
      }
      return
    }
    
    if (!silent) {
      printProgress.value = 10
      printProgressText.value = '加载课表数据...'
    }
    
    // 并行加载所需数据：按需加载教师/学生数据
    const includeTeacher = selectedScheduleType.value === 'teacher'
    const includeStudentData = selectedScheduleType.value === 'student'

    const scheduleP = loadScheduleForDisplayWithProgress(silent)
    const teacherP = includeTeacher ? loadTeacherListWithProgress(silent) : Promise.resolve(null)
    const studentP = includeStudentData ? loadStudentScheduleDataWithProgress(silent) : Promise.resolve(null)

    const [scheduleRes, teacherRes, studentRes] = await Promise.all([scheduleP, teacherP, studentP])

    // 更新缓存
    dataCache.value = {
      scheduleData: scheduleRes,
      teacherList: teacherRes,
      studentScheduleData: studentRes,
      contextKey: getPrintCacheKey(),
      lastLoadTime: Date.now()
    }
    
    if (!silent) {
      printProgress.value = 100
      printProgressText.value = '数据加载完成'
    }
    
  } catch (error) {
    console.error('加载打印数据失败:', error)
    if (!silent) {
      ElMessage.error('加载数据失败: ' + error.message)
    }
  } finally {
    if (!silent) {
      // 立即结束加载，减少感知等待
      printLoading.value = false
      printProgress.value = 0
      printProgressText.value = ''
    }
  }
}

// 带进度的课表数据加载（按实验套过滤以减少数据量）
const loadScheduleForDisplayWithProgress = async (silent = false) => {
  try {
    if (!silent) {
      printProgress.value = 20
      printProgressText.value = '获取课表安排...'
    }
    
    const params = {}
    if (semesterId.value) {
      params.semesterId = semesterId.value
    }
    // 若打印对话框中选定了实验套，则按套过滤；否则不传以兼容旧后端
    if (selectedPrintScheduleSuiteId()) {
      params.suiteId = selectedPrintScheduleSuiteId()
    }
    
    const response = await getScheduleForDisplay(params)
    const data = response.data || []
    
    if (!silent) {
      printProgress.value = 40
      printProgressText.value = '处理课表数据...'
    }
    
    scheduleList.value = data
    return data
  } catch (error) {
    console.error('加载课表数据失败:', error)
    throw error
  }
}

// 获取用于打印过滤的实验套ID（优先对话框选择）
const selectedPrintScheduleSuiteId = () => {
  if (selectedPrintSuiteId && selectedPrintSuiteId.value != null) return selectedPrintSuiteId.value
  if (selectedSuiteId && selectedSuiteId.value) return selectedSuiteId.value
  return null
}

// 带进度的教师列表加载
const loadTeacherListWithProgress = async (silent = false) => {
  try {
    if (!silent) {
      printProgress.value = 60
      printProgressText.value = '加载教师信息...'
    }
    
    const response = await getTeacherList({ current: 1, size: 100 })
    const data = response.data.records || []
    
    if (!silent) {
      printProgress.value = 80
      printProgressText.value = '处理教师数据...'
    }
    
    teacherList.value = data
    return data
  } catch (error) {
    console.error('加载教师列表失败:', error)
    throw error
  }
}

// 带进度的学生课表数据加载（直接从后端获取结构化数据）
const loadStudentScheduleDataWithProgress = async (silent = false) => {
  try {
    if (!silent) {
      printProgress.value = 85
      printProgressText.value = '加载学生课表...'
    }
    
    if (!semesterId.value) {
      await ensureSemesterId()
    }
    
    if (!semesterId.value) {
      return {}
    }
    // 直接调用后端接口获取学生课表数据
    const resp = await getStudentSchedule(semesterId.value)
    const data = resp && resp.data ? resp.data : {}

    if (!silent) {
      printProgress.value = 95
      printProgressText.value = '处理学生数据...'
    }

    studentScheduleData.value = data
    return data
  } catch (error) {
    console.error('加载学生课表数据失败:', error)
    return {}
  }
}

const ensureSemesterId = async () => {
  // 如果props传入了semesterId，直接使用
  if (props.semesterId) {
    semesterId.value = Number(props.semesterId)
    return semesterId.value
  }
  
  if (semesterId.value) return semesterId.value
  
  try {
    // 先获取学期列表
    const semesterResp = await getSemesterList({ current: 1, size: 100 })
    if (semesterResp?.data?.records) {
      semesterOptions.value = semesterResp.data.records
      
      // 找到当前学期（status为active的）
      const currentSemester = semesterResp.data.records.find(s => s.status === 'active')
      if (currentSemester) {
        semesterId.value = Number(currentSemester.semesterId)
        // 更新localStorage
        localStorage.setItem('semesterId', String(semesterId.value))
        localStorage.setItem('currentSemesterId', String(semesterId.value))

      }
    }
    
    // 如果还是没有找到，尝试调用getCurrentSemester接口
    if (!semesterId.value) {
      const resp = await getCurrentSemester()
      const cur = resp?.data?.semesterId || resp?.data?.semester_id || 0
      if (cur) {
        semesterId.value = Number(cur)
        localStorage.setItem('semesterId', String(semesterId.value))
        localStorage.setItem('currentSemesterId', String(semesterId.value))
      }
    }
  } catch (e) {
    console.error('获取学期信息失败:', e)
  }
  
  return semesterId.value
}

// 日历维度
const weekdays = ['周一','周二','周三','周四','周五']
const timeSlots = ['上午','下午']
const weekType = ref(0) // 0 单周，1 双周

const refreshWeekTypeBySemester = async () => {
  try {
    if (!semesterId.value) return
    const info = await getWeekInfo({ semesterId: semesterId.value })
    if (info?.code === 200 && info?.data && (info.data.weekType === 0 || info.data.weekType === 1)) {
      weekType.value = Number(info.data.weekType)
    }
    if (info?.code === 200 && info?.data && info.data.totalWeeks) {
      const tw = Number(info.data.totalWeeks)
      if (!Number.isNaN(tw) && tw > 0) {
        totalWeeks.value = tw
      }
    }
    if (info?.code === 200 && info?.data) {
      const odd = Array.isArray(info.data.teachingWeeksOdd) ? info.data.teachingWeeksOdd : []
      const even = Array.isArray(info.data.teachingWeeksEven) ? info.data.teachingWeeksEven : []
      teachingWeeksOdd.value = odd.map(n => Number(n)).filter(n => !Number.isNaN(n) && n > 0)
      teachingWeeksEven.value = even.map(n => Number(n)).filter(n => !Number.isNaN(n) && n > 0)
    }
  } catch (e) {
    // ignore
  }
}

const getTeachingWeeksByMode = (mode) => {
  if (mode === 'odd') {
    if (Array.isArray(teachingWeeksOdd.value) && teachingWeeksOdd.value.length) return teachingWeeksOdd.value
    const maxWeek = (totalWeeks.value && Number(totalWeeks.value) > 0) ? Number(totalWeeks.value) : 20
    const weeks = []
    for (let w = 3; w <= maxWeek; w++) {
      if (w % 2 === 1) weeks.push(w)
    }
    return weeks
  }
  if (mode === 'even') {
    if (Array.isArray(teachingWeeksEven.value) && teachingWeeksEven.value.length) return teachingWeeksEven.value
    const maxWeek = (totalWeeks.value && Number(totalWeeks.value) > 0) ? Number(totalWeeks.value) : 20
    const weeks = []
    for (let w = 3; w <= maxWeek; w++) {
      if (w % 2 === 0) weeks.push(w)
    }
    return weeks
  }
  return []
}

// 批量设置用周次：单周包含第1周，双周包含第2周
const getBatchWeeksByMode = (mode) => {
  const maxWeek = (totalWeeks.value && Number(totalWeeks.value) > 0) ? Number(totalWeeks.value) : 20
  const base = getTeachingWeeksByMode(mode)
  const withIntro = mode === 'odd'
    ? [1, ...base]
    : (mode === 'even' ? [2, ...base] : [...base])
  return Array.from(new Set(withIntro))
    .map(n => Number(n))
    .filter(n => !Number.isNaN(n) && n >= 1 && n <= maxWeek)
    .sort((a, b) => a - b)
}

const getWeekTypeWeeksForIntro = (wt) => {
  // 兼容旧逻辑：单周包含第1周，双周包含第2周；教学周来自后端动态列表
  if (wt === 0) return [1, ...getTeachingWeeksByMode('odd')]
  if (wt === 1) return [2, ...getTeachingWeeksByMode('even')]
  return []
}

const handleExportScheduleExcel = async () => {
  exportingSchedule.value = true
  try {
    if (!semesterId.value) {
      ElMessage.warning('请先选择学期')
      return
    }
    const params = { semesterId: semesterId.value }
    if (selectedSuiteId.value) params.suiteId = selectedSuiteId.value
    if (weekType.value === 0 || weekType.value === 1) params.weekType = weekType.value
    const resp = await exportScheduleXlsx(params)
    const blob = new Blob([resp.data], {
      type: resp.headers?.['content-type'] || 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'schedule.xlsx'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('开始导出')
  } catch (e) {
    ElMessage.error(e?.message || '导出失败')
  } finally {
    exportingSchedule.value = false
  }
}

// 日历状态：每格保存 classIds、groupNames、grouped、loading
const cellState = reactive({})
const initCalendarState = () => {
  weekdays.forEach(d => {
    if (!cellState[d]) cellState[d] = {}
    timeSlots.forEach(s => {
      const storeKey = buildTeacherStoreKey(d, s)
      const savedTeachers = localStorage.getItem(storeKey)
      let parsed = []
      try { parsed = savedTeachers ? JSON.parse(savedTeachers) : [] } catch (e) { parsed = [] }
      cellState[d][s] = {
        classList: cellState[d][s]?.classList || [],
        classInput: '',
        groupNames: cellState[d][s]?.groupNames || [],
        grouped: cellState[d][s]?.grouped || false,
        loading: false,
        teacherIds: parsed,
        teacherTags: cellState[d][s]?.teacherTags || [],
        teacherInput: '',
        teacherPickerVisible: false,
        tempTeacherIds: parsed.slice(),
        // 新增：绪论课教师相关字段
        introTeacherIds: [],
        introTeacherTags: []
      }
    })
  })
}

const buildExperimentTime = (weekday, slot, weekType = null) => {
  if (weekType !== null) {
    const weekTypeText = weekType === 0 ? '单' : '双'
    return `${weekTypeText}${weekday}${slot}`
  }
  return `${weekday}${slot}`
}
const buildTeacherStoreKey = (weekday, slot) => {
  const sem = semesterId.value || 0
  const suite = selectedSuiteId.value || 0
  const wt = weekType.value || 0
  return `calendarTeachers:${sem}:${suite}:wt${wt}:${buildExperimentTime(weekday, slot, wt)}`
}

// initialize immediately to prevent undefined in first render
initCalendarState()

const parseScheduleTimeKey = (experimentTime) => {
  // experimentTime may be JSON string { week, weekday, timeSlot } or plain string like '第x周周一上午'
  try {
    const data = typeof experimentTime === 'string' ? JSON.parse(experimentTime) : experimentTime
    if (data && data.weekday && data.timeSlot) {
      // 这里不需要传递 weekType，因为是从已有数据解析
      return buildExperimentTime(data.weekday, data.timeSlot)
    }
  } catch (e) {
    // try to extract by regex from formatted text
    if (typeof experimentTime === 'string') {
      const m = experimentTime.match(/周[一二三四五六日][上下]午/)
      if (m) return m[0]
    }
  }
  return ''
}

const parseTeacherIds = (teacherIds) => {
  if (!teacherIds) return []
  if (Array.isArray(teacherIds)) return teacherIds
  
  // 尝试解析 JSON 数组格式
  try {
    const arr = JSON.parse(teacherIds)
    if (Array.isArray(arr)) return arr
  } catch (e) {
    // 不是 JSON 格式，尝试解析逗号分隔的字符串
  }
  
  // 处理逗号分隔的字符串格式，如 "2,3"
  const str = String(teacherIds).trim()
  if (str) {
    return str.split(',').map(id => id.trim()).filter(Boolean)
  }
  
  return []
}

const onTeacherChange = (weekday, slot) => {
  const key = buildTeacherStoreKey(weekday, slot)
  const ids = cellState[weekday][slot].teacherIds || []
  localStorage.setItem(key, JSON.stringify(ids))
}

// 教师标签：查询、添加、删除、持久化
const teacherIdToName = (id) => {
  return teacherNameById.value.get(String(id)) || ''
}

const persistTeacherIds = (weekday, slot) => {
  const key = buildTeacherStoreKey(weekday, slot)
  const tags = cellState[weekday][slot].teacherTags || []
  const ids = tags.map(t => t.id).filter(id => id !== undefined)
  // 同步 v-model
  cellState[weekday][slot].teacherIds = ids
  localStorage.setItem(key, JSON.stringify(ids))
}

const onTeacherIdsChanged = (weekday, slot, ids) => {
  // 根据选择的ids同步tags与本地存储
  const tags = (ids || []).map(id => ({ id, name: teacherIdToName(id) || String(id) }))
  cellState[weekday][slot].teacherTags = tags
  persistTeacherIds(weekday, slot)
}

const confirmTeacherPick = async (weekday, slot) => {
  const ids = (cellState[weekday][slot].tempTeacherIds || []).slice()
  cellState[weekday][slot].teacherIds = ids
  onTeacherIdsChanged(weekday, slot, ids)
  cellState[weekday][slot].teacherPickerVisible = false

  // 同步到后端
  try {
    if (!semesterId.value) {
      await ensureSemesterId()
    }
    if (!semesterId.value) {
      ElMessage.warning('未检测到学期ID，无法保存教师到后端')
      return
    }
    // 检查是否有绪论课教师（在实际使用中，这里可能需要从其他地方获取绪论课教师信息）
    // 当前实现中，绪论课教师主要通过教师设置对话框处理，所以这里我们添加一个占位符
    // 在实际场景中，你可能需要从其他来源获取introTeacherId
    let introTeacherId = undefined
    
    const params = {
      experimentTime: buildExperimentTime(weekday, slot, weekType.value),
      teacherIds: ids.join(','),
      semesterId: semesterId.value,
      suiteId: selectedSuiteId.value || undefined,
      weekType: weekType.value,
      introTeacherId: introTeacherId
    }
    await updateTeachersByTime(params)
    ElMessage.success('教师已保存')
    await loadScheduleList()
  } catch (e) {
    console.error(e)
    ElMessage.error('保存教师失败')
  }
}

const handleRemoveTeacherTag = (weekday, slot, idx) => {
  const cell = cellState[weekday][slot]
  if (!Array.isArray(cell.teacherTags) || idx < 0 || idx >= cell.teacherTags.length) return
  cell.teacherTags.splice(idx, 1)
  persistTeacherIds(weekday, slot)
}

// 当从 schedule 回填 teacherIds 时，尽力生成 tags
const hydrateTeacherTagsFromIds = () => {
  // 如果教师列表还没有加载完成，直接返回
  if (!teacherList.value || teacherList.value.length === 0) {
    return
  }
  
  weekdays.forEach(d => {
    timeSlots.forEach(s => {
      const cell = cellState[d][s]
      if (cell.teacherIds && cell.teacherIds.length && (!cell.teacherTags || cell.teacherTags.length === 0)) {
        cell.teacherTags = cell.teacherIds.map(id => ({ id, name: teacherIdToName(id) || String(id) }))
      }
    })
  })
}

const populateTeachersFromSchedule = () => {
  // 如果教师列表还没有加载完成，直接返回
  if (!teacherList.value || teacherList.value.length === 0) {
    return
  }
  
  ;(scheduleList.value || []).forEach(item => {
    const key = parseScheduleTimeKey(item.experimentTime)
    if (!key) return
    const ids = parseTeacherIds(item.teacherIds)
    if (ids.length) {
      // also store in namespaced key
      const weekday = key.slice(0, 2)
      const slot = key.slice(2)
      const storeKey = buildTeacherStoreKey(weekday, slot)
      localStorage.setItem(storeKey, JSON.stringify(ids))
      if (cellState[weekday] && cellState[weekday][slot]) {
        cellState[weekday][slot].teacherIds = ids
        const tags = ids.map(id => ({ id, name: teacherIdToName(id) || String(id) }))
        cellState[weekday][slot].teacherTags = tags
      }
    }
  })
}

// 解析逗号/方括号包裹的字符串为数组
const parseNameList = (raw) => {
  if (!raw) return []
  let s = String(raw).trim()
  s = s.replace(/^[\[]|[\]]$/g, '') // 去掉首尾方括号
  if (!s) return []
  return s.split(',').map(v => v.trim()).filter(Boolean)
}

// 从 schedule 列表回填班级、分组结果
const populateClassesAndGroupsFromSchedule = () => {
  try {
    // 重要：根据当前的 weekType 只填充对应周次类型的数据，避免单周和双周互相覆盖
    // weekType 是一个 ref，使用 .value 访问
    let currentWeekType = 0 // 默认值
    try {
      if (weekType && typeof weekType === 'object' && 'value' in weekType) {
        // 安全地访问 ref 的值
        const weekTypeValue = weekType.value
        currentWeekType = weekTypeValue != null ? Number(weekTypeValue) : 0
      } else {
        console.warn('weekType 不是有效的 ref，使用默认值 0')
      }
    } catch (e) {
      console.error('访问 weekType.value 时出错:', e)
      currentWeekType = 0
    }
  
  (scheduleList.value || []).forEach(item => {
    // 只处理当前周次类型的记录
    const itemWeekType = item.weekType != null ? Number(item.weekType) : null
    if (itemWeekType !== null && itemWeekType !== currentWeekType) {
      return // 跳过不同周次类型的记录
    }
    
    // 解析experimentTime，提取星期和上下午
    // experimentTime可能是"单周一上午"或"双周一上午"格式
    const experimentTime = item.experimentTime || ''
    let weekday = ''
    let slot = ''
    
    // 尝试匹配"单周X上午/下午"或"双周X上午/下午"格式
    const timeMatch = experimentTime.match(/(?:单|双)?周([一二三四五六日])(上午|下午)/)
    if (timeMatch) {
      weekday = '周' + timeMatch[1]
      slot = timeMatch[2] === '上午' ? '上午' : '下午'
    } else {
      // 回退：使用 parseScheduleTimeKey
      const key = parseScheduleTimeKey(experimentTime)
      if (!key) return
      weekday = key.slice(0, 2)
      slot = key.slice(2)
    }
    
    const cell = cellState[weekday]?.[slot]
    if (!cell) return

    // 班级：classIds -> classList（标签）
    if (item.classIds) {
      const classes = parseNameList(item.classIds)
      if (classes.length) {
        // 只有当前周次类型匹配时才更新班级列表
        cell.classList = classes
      }
    }

    // 分组：groupIds 存的是组名字符串
    // 重要：只有当前周次类型匹配时才更新分组，避免被其他周次类型覆盖
    if (item.groupIds) {
      const groups = parseNameList(item.groupIds)
      if (groups.length) {
        cell.groupNames = groups
        cell.grouped = true
      }
    }
  })
  } catch (error) {
    console.error('populateClassesAndGroupsFromSchedule 错误:', error)
    console.error('weekType 类型:', typeof weekType, weekType)
  }
}

// 从 group_experiment 数据中获取教师信息（用于课表显示）
const loadTeachersFromGroupExperiments = async () => {
  try {
    if (!semesterId.value) {
      await ensureSemesterId()
    }
    if (!semesterId.value) return



    // 获取所有小组实验数据
    const params = {
      semesterId: semesterId.value,
      suiteId: selectedSuiteId.value || undefined
    }
    
    try {
      const response = await getAllGroupExperiments(params)
      const allGroupExperiments = response.data || []
      
      // 按时间段分组数据
      const timeSlotData = {}
      
      // 初始化时间段数据结构
      weekdays.forEach(weekday => {
        timeSlotData[weekday] = {}
        timeSlots.forEach(slot => {
          timeSlotData[weekday][slot] = []
        })
      })
      
      // 将小组实验数据按时间段分组
      allGroupExperiments.forEach(group => {
        const weekMatch = group.experimentTime.match(/第(\d+)周/)
        if (weekMatch) {
          const weekNum = parseInt(weekMatch[1])
          
          // 根据周次确定时间段
          // 第1、3、5、7、9、11、13、15周 -> 上午
          // 第2、4、6、8、10、12、14、16周 -> 下午
          const slot = weekNum % 2 === 1 ? '上午' : '下午'
          
          // 根据周次确定星期几
          // 这里需要根据实际的课程安排来确定
          // 暂时假设所有周次都是周一，实际应该从课程安排中获取
          const weekday = '周一'
          
          if (timeSlotData[weekday] && timeSlotData[weekday][slot]) {
            timeSlotData[weekday][slot].push(group)
          }
        }
      })
      

      
      // 为每个时间段设置多个教师
      weekdays.forEach(weekday => {
        timeSlots.forEach(slot => {
          const groups = timeSlotData[weekday][slot] || []
          
          if (groups.length > 0) {
            // 按小组名称排序，确保顺序一致
            groups.sort((a, b) => a.groupName.localeCompare(b.groupName))
            
            // 收集该时间段的所有教师
            const teachers = []
            const teacherIds = []
            
            // 添加小组对应的教师
            groups.forEach(group => {
              if (group.teacherId && group.teacherName) {
                const teacherId = group.teacherId
                const teacherName = group.teacherName
                
                // 避免重复添加同一个教师
                if (!teacherIds.includes(teacherId)) {
                  // 检查是否为绪论课教师（第一周）
                  const weekMatch = group.experimentTime?.match(/第(\d+)周/)
                  const isFirstWeek = weekMatch && parseInt(weekMatch[1]) === 1
                  const isIntroTeacher = isFirstWeek && group.isIntroCourse === 1
                  
                  teachers.push({ 
                    id: teacherId, 
                    name: teacherName,
                    isIntroTeacher: isIntroTeacher
                  })
                  teacherIds.push(teacherId)
                }
              }
            })
            
            // 如果教师数量不足5个，从教师列表中补充
            if (teachers.length < 5 && teacherList.value && teacherList.value.length > 0) {
              teacherList.value.forEach(teacher => {
                if (teachers.length < 5 && !teacherIds.includes(teacher.userId)) {
                  teachers.push({ id: teacher.userId, name: teacher.realName })
                  teacherIds.push(teacher.userId)
                }
              })
            }
            
            // 更新课表显示
            if (cellState[weekday] && cellState[weekday][slot]) {
              cellState[weekday][slot].teacherIds = teacherIds
              cellState[weekday][slot].teacherTags = teachers
            }
          }
        })
      })
      
    } catch (error) {
      console.error('获取小组实验数据失败:', error)
    }
  } catch (error) {
    console.error('加载教师信息失败:', error)
  }
}

// 添加/删除班级标签
const normalizeClassId = (val) => (val || '').trim()
const addClassTag = (weekday, slot, onBlur = false) => {
  const cell = cellState[weekday][slot]
  const raw = cell.classInput
  const value = normalizeClassId(raw)
  if (!value) return
  // 支持用户误输逗号空格，按分隔符拆分
  const parts = value.split(/[，,\s]+/).map(v => v.trim()).filter(Boolean)
  parts.forEach(p => {
    if (!cell.classList.includes(p)) {
      cell.classList.push(p)
    }
  })
  cell.classInput = ''
}
const removeClassTag = (weekday, slot, idx) => {
  const cell = cellState[weekday][slot]
  cell.classList.splice(idx, 1)
}

const handleCellGroup = async (weekday, slot) => {
  if (!isAdmin.value) return
  const cell = cellState[weekday][slot]
  // 补充一次回车/失焦添加
  addClassTag(weekday, slot, true)
  if (!cell.classList || cell.classList.length === 0) {
    ElMessage.warning('请先添加班级')
    return
  }
  if (!semesterId.value) {
    await ensureSemesterId()
  }
  if (!semesterId.value) {
    ElMessage.warning('未检测到学期ID，请先在学期设置中选择当前学期')
    return
  }
  if (!selectedSuiteId.value) {
    ElMessage.warning('请先选择实验套')
    return
  }
  try {
    cell.loading = true
    const params = {
      experimentTime: buildExperimentTime(weekday, slot, weekType.value),
      classIds: cell.classList.join(','),
      semesterId: semesterId.value,
      suiteId: selectedSuiteId.value,
      weekType: weekType.value
    }
    const resp = await autoGroupAndUpdate(params)
    const groupStr = resp.data || ''
    const groups = groupStr.split(',').map(g => g.trim()).filter(Boolean)
    cell.groupNames = groups
    cell.grouped = true
    ElMessage.success('分组成功')
    // 自动刷新课表数据
    await loadScheduleList()
  } catch (e) {
    console.error(e)
    ElMessage.error('分组失败')
  } finally {
    cell.loading = false
  }
}

const handleCellRegroup = (weekday, slot) => {
  if (!isAdmin.value) return
  const cell = cellState[weekday][slot]
  // 允许保留上一次的classIds，恢复到可编辑状态
  cell.grouped = false
  cell.groupNames = []
}

const handleCalendarReset = () => {
  if (!isAdmin.value) return
  initCalendarState()
  ElMessage.success('已清空当前输入')
}

// 下面为原有列表逻辑

// 分页信息
const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

// 安排表单
const scheduleForm = reactive({
  scheduleId: null,
  experimentId: null,
  teacherIds: [],
  groupIds: [],
  location: '',
  week: null,
  weekday: '',
  timeSlot: ''
})

// 实验安排列表数据
const scheduleList = ref([])
let scheduleListSeq = 0
let activeScheduleListKey = ''
let activeScheduleListPromise = null

// 实验项目列表
const experimentList = ref([])

// 教师列表
const teacherList = ref([])
const teacherNameById = computed(() => {
  const map = new Map()
  teacherList.value.forEach(t => {
    map.set(String(t.userId), t.realName)
  })
  return map
})

// 小组列表
const groupList = ref([])

// 按实验类型分组的课表
const type1ScheduleList = computed(() => {
  return scheduleList.value.filter(item => item.experimentType === 1)
})

const type2ScheduleList = computed(() => {
  return scheduleList.value.filter(item => item.experimentType === 2)
})

// 对话框标题
const dialogTitle = computed(() => {
  return isEdit.value ? '编辑实验课表' : '新增实验课表'
})

// 加载实验安排列表
const loadScheduleList = async (options = {}) => {
  const params = { current: 1, size: 1000 }
  if (selectedSuiteId.value) {
    params.suiteId = selectedSuiteId.value
  }
  if (semesterId.value) {
    params.semesterId = semesterId.value
  }

  const requestKey = JSON.stringify(params)
  if (!options.force && activeScheduleListPromise && activeScheduleListKey === requestKey) {
    return activeScheduleListPromise
  }

  const seq = ++scheduleListSeq
  activeScheduleListKey = requestKey
  activeScheduleListPromise = (async () => {
    try {
      if (!options.silent) loading.value = true
      // 移除 weekType 参数，获取所有周次类型的数据
      // params.weekType = weekType.value
      const response = await getScheduleList(params)
      if (seq !== scheduleListSeq) return
      scheduleList.value = response.data.records
      populateClassesAndGroupsFromSchedule()

      // 从 group_experiment 数据中获取教师信息
      // 确保教师列表已加载完成
      if (teacherList.value && teacherList.value.length > 0) {
        await loadTeachersFromGroupExperiments()
      }
    } catch (error) {
      console.error('加载实验安排列表失败:', error)
      if (!options.silent) ElMessage.error('加载实验安排列表失败')
    } finally {
      if (seq === scheduleListSeq) {
        if (!options.silent) loading.value = false
        activeScheduleListPromise = null
        activeScheduleListKey = ''
      }
    }
  })()

  return activeScheduleListPromise
}

// 加载课表显示数据 - 返回所有实验套的数据
const loadScheduleForDisplay = async () => {
  try {
    loading.value = true
    const params = {}
    if (semesterId.value) {
      params.semesterId = semesterId.value
    }
    
    const response = await getScheduleForDisplay(params)
    
    scheduleList.value = response.data
    
    // 从 group_experiment 数据中获取教师信息
    // 确保教师列表已加载完成
    if (teacherList.value && teacherList.value.length > 0) {
      await loadTeachersFromGroupExperiments()
    }
  } catch (error) {
    console.error('加载课表显示数据失败:', error)
    ElMessage.error('加载课表显示数据失败')
  } finally {
    loading.value = false
  }
}

let experimentListPromise = null
let teacherListPromise = null
let groupListPromise = null

// 加载实验项目列表
const loadExperimentList = async () => {
  if (experimentList.value.length) return experimentList.value
  if (experimentListPromise) return experimentListPromise
  experimentListPromise = (async () => {
  try {
    const response = await getExperimentList({ current: 1, size: 100 })
    experimentList.value = response.data.records
    return experimentList.value
  } catch (error) {
    console.error('加载实验项目列表失败:', error)
    return []
  } finally {
    experimentListPromise = null
  }
  })()
  return experimentListPromise
}

// 加载教师列表
const loadTeacherList = async () => {
  if (teacherList.value.length) return teacherList.value
  if (teacherListPromise) return teacherListPromise
  teacherListPromise = (async () => {
  try {
    const response = await getTeacherList({ current: 1, size: 100 })
    teacherList.value = response.data.records
    
    // 如果课表数据已经加载完成，重新填充教师标签
    if (scheduleList.value && scheduleList.value.length > 0) {
      hydrateTeacherTagsFromIds()
    }
    return teacherList.value
  } catch (error) {
    console.error('加载教师列表失败:', error)
    return []
  } finally {
    teacherListPromise = null
  }
  })()
  return teacherListPromise
}

// 加载小组列表（遗留）
const loadGroupList = async () => {
  if (groupList.value.length) return groupList.value
  if (groupListPromise) return groupListPromise
  groupListPromise = (async () => {
  try {
    const response = await getGroupList({ current: 1, size: 100 })
    groupList.value = Array.isArray(response.data) ? response.data : (response.data?.records || [])
    return groupList.value
  } catch (error) {
    console.error('加载小组列表失败:', error)
    return []
  } finally {
    groupListPromise = null
  }
  })()
  return groupListPromise
}

const ensureScheduleOptionsReady = async () => {
  const tasks = []
  if (!experimentList.value.length) tasks.push(loadExperimentList())
  if (!teacherList.value.length) tasks.push(loadTeacherList())
  if (!groupList.value.length) tasks.push(loadGroupList())
  if (tasks.length) await Promise.all(tasks)
}

// 格式化实验时间
const formatExperimentTime = (experimentTime) => {
  if (!experimentTime) return ''
  if (experimentTime.includes('第') && experimentTime.includes('周')) {
    return experimentTime
  }
  try {
    const data = JSON.parse(experimentTime)
    if (data.week && data.weekday && data.timeSlot) {
      return `第${data.week}周${data.weekday}${data.timeSlot}`
    }
  } catch (e) {
    return experimentTime
  }
  return experimentTime
}

// 刷新
const handleRefresh = () => {
  loadScheduleList()
}

// 新增安排
const handleAddSchedule = async () => {
  isEdit.value = false
  resetScheduleForm()
  // 确保下拉选项已加载，避免默认显示数字ID
  await ensureScheduleOptionsReady()
  scheduleOptionsReady.value = true
  scheduleDialogVisible.value = true
}

// 编辑安排
const handleEditSchedule = async (row) => {
  isEdit.value = true
  // 确保下拉选项已加载，避免默认显示数字ID
  await ensureScheduleOptionsReady()
  Object.assign(scheduleForm, {
    scheduleId: row.scheduleId,
    experimentId: row.experimentId,
    teacherIds: row.teacherIds || [],
    groupIds: row.groupIds || [],
    location: row.location
  })
  try {
    const timeData = JSON.parse(row.experimentTime)
    scheduleForm.week = timeData.week
    scheduleForm.weekday = timeData.weekday
    scheduleForm.timeSlot = timeData.timeSlot
  } catch (e) {
    scheduleForm.week = null
    scheduleForm.weekday = ''
    scheduleForm.timeSlot = ''
  }
  scheduleOptionsReady.value = true
  scheduleDialogVisible.value = true
}

watch(scheduleDialogVisible, (v) => {
  if (!v) scheduleOptionsReady.value = false
})

// 取消安排
const handleCancelSchedule = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要取消这个实验课表吗？`,
      '取消确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await deleteSchedule(row.scheduleId)
    ElMessage.success('取消成功')
    loadScheduleList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('取消失败')
    }
  }
}

// 查看详情
const handleViewDetail = (row) => {
  ElMessage.info('查看详情功能')
}

// 保存安排
const handleSaveSchedule = async () => {
  try {
    const experimentTime = JSON.stringify({
      week: scheduleForm.week,
      weekday: scheduleForm.weekday,
      timeSlot: scheduleForm.timeSlot
    })
    const scheduleData = {
      experimentId: scheduleForm.experimentId,
      teacherIds: scheduleForm.teacherIds,
      groupIds: scheduleForm.groupIds,
      location: scheduleForm.location,
      experimentTime: experimentTime
    }
    if (isEdit.value) {
      scheduleData.scheduleId = scheduleForm.scheduleId
      await updateSchedule(scheduleData)
      ElMessage.success('编辑成功')
    } else {
      await addSchedule(scheduleData)
      ElMessage.success('新增成功')
    }
    scheduleDialogVisible.value = false
    loadScheduleList()
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error('保存失败')
  }
}

// 重置安排表单
const resetScheduleForm = () => {
  Object.assign(scheduleForm, {
    scheduleId: null,
    experimentId: null,
    teacherIds: [],
    groupIds: [],
    location: '',
    week: null,
    weekday: '',
    timeSlot: ''
  })
}

// 分页
const handleSizeChange = (size) => {
  pagination.size = size
  pagination.current = 1
  loadScheduleList()
}
const handleCurrentChange = (current) => {
  pagination.current = current
  loadScheduleList()
}

// 实验套选项
const initSuiteSelection = async () => {
  try {
    // 优先取所有套
    const resp = await getAllSuites()
    const list = resp?.data || []
    suiteOptions.value = list
  } catch (e) {
    // 退化到分页接口
    try {
      const resp = await getSuiteList({ current: 1, size: 100 })
      suiteOptions.value = resp?.data?.records || []
    } catch (e2) {
      suiteOptions.value = []
    }
  }
  
  // 套件默认值：
  // - readonly：默认“全部实验套”（null），不强制选第一个
  // - 非 readonly：尽量沿用本地存储，否则选第一个
  if (suiteOptions.value.length > 0) {
    if (props.readonly) {
      if (selectedSuiteId.value !== null) {
        selectedSuiteId.value = null
        localStorage.removeItem('selectedSuiteId')
      }
    } else {
      if (selectedSuiteId.value == null || Number.isNaN(Number(selectedSuiteId.value))) {
        const stored = localStorage.getItem('selectedSuiteId')
        const storedNum = stored != null && stored !== '' ? Number(stored) : NaN
        if (!Number.isNaN(storedNum) && storedNum > 0) {
          selectedSuiteId.value = storedNum
        } else {
          const firstId = suiteOptions.value[0].suiteId || suiteOptions.value[0].experimentSuiteId
          selectedSuiteId.value = firstId != null ? Number(firstId) : null
          if (selectedSuiteId.value != null) {
            localStorage.setItem('selectedSuiteId', String(selectedSuiteId.value))
          }
        }
      }
    }
  } else {
    selectedSuiteId.value = null
    localStorage.removeItem('selectedSuiteId')
  }
}

const handleSemesterChange = async () => {
  if (semesterId.value) {
    localStorage.setItem('semesterId', String(semesterId.value))
    localStorage.setItem('currentSemesterId', String(semesterId.value))
  }
  clearPrintCache()
  studentScheduleData.value = {}
  await refreshWeekTypeBySemester()
  await loadScheduleList()
}

const handleSuiteChange = async () => {
  clearPrintCache()
  
  // 如果没有选择实验套，清空localStorage
  if (selectedSuiteId.value == null || selectedSuiteId.value === 0 || selectedSuiteId.value === '0') {
    localStorage.removeItem('selectedSuiteId')
  } else {
    localStorage.setItem('selectedSuiteId', String(selectedSuiteId.value))
  }
  
  // 重置日历状态，清空当前显示的数据
  weekdays.forEach(d => {
    if (!cellState[d]) cellState[d] = {}
    timeSlots.forEach(s => {
      cellState[d][s] = {
        classList: [],
        classInput: '',
        groupNames: [],
        grouped: false,
        loading: false,
        teacherIds: [],
        teacherTags: [],
        teacherInput: '',
        teacherPickerVisible: false,
        tempTeacherIds: []
      }
    })
  })
  // 确保教师列表已加载，然后重新加载课表数据
  if (teacherList.value.length === 0) {
    await loadTeacherList()
  }
  await loadScheduleList()
}

const handleWeekTypeChange = async () => {
  // 切换单双周时，重置当前显示状态并刷新
  weekdays.forEach(d => {
    timeSlots.forEach(s => {
      if (!cellState[d]) cellState[d] = {}
      cellState[d][s] = {
        classList: [],
        classInput: '',
        groupNames: [],
        grouped: false,
        loading: false,
        teacherIds: [],
        teacherTags: [],
        teacherInput: '',
        teacherPickerVisible: false,
        tempTeacherIds: []
      }
    })
  })
  await loadScheduleList()
}

// 自动监听选择变化，确保下拉改变即刷新
const batchDialogVisible = ref(false)
const batchContext = reactive({ weekday: '', slot: '' })
const batchForm = reactive({
  singleGroup: '',
  weekMode: 'odd',
  filterSuiteId: undefined,
})
const batchOptionsReady = ref(false)
const groupPlanEchoSeq = ref(0)
const batchDialogInstanceKey = ref(0)

// 教师设置相关
const teacherSettingDialogVisible = ref(false)
const teacherSettingForm = reactive({
  suiteId: null
})
const teacherAssignmentPlan = ref([])
const savingTeacherAssignment = ref(false)

// 根据实验套拉取实验（优先使用后端按套接口；失败再退化）
const ensureExperimentsForSuite = async () => {
  try {
    if (batchForm.filterSuiteId) {
      // 1) 专用接口：/experiment/by-suite?suiteId=...
      const r0 = await getExperimentsBySuite(batchForm.filterSuiteId)
      let list0 = r0?.data?.records || r0?.data || []
      if (!Array.isArray(list0)) list0 = []
      if (list0.length) {
        experimentList.value = list0
        return
      }
      // 2) fallback: getExperimentList 带 suiteId
      const r1 = await getExperimentList({ current: 1, size: 1000, suiteId: batchForm.filterSuiteId })
      const list1 = r1?.data?.records || r1?.data || []
      if (Array.isArray(list1) && list1.length) {
        experimentList.value = list1
        return
      }
      // 3) fallback: 全量
      const r2 = await getExperimentList({ current: 1, size: 1000 })
      experimentList.value = r2?.data?.records || r2?.data || []
      return
    }
    // 未选择套：拉取全量
    const res = await getExperimentList({ current: 1, size: 1000 })
    experimentList.value = res?.data?.records || res?.data || []
  } catch (e) {
    // 保持现有列表
  }
}

watch(() => batchForm.filterSuiteId, async () => {
  await ensureExperimentsForSuite()
  // 先清空启用行的实验选择，再按新套顺序重新分配
  clearEnabledExperiments()
  assignSequentialExperiments()
})

const groupPlan = ref([])

// 启用行切换：未启用则清空该行选择；启用时自动补默认老师/实验
const onTogglePlanRow = (row) => {
  if (!row.enabled) {
    row.experimentId = null
    row.teacherId = null
    return
  }
  // 默认教师：第一个老师（只有在没有选择教师时才设置）
  if (teacherList.value && teacherList.value.length && (row.teacherId == null || row.teacherId === '')) {
    row.teacherId = String(teacherList.value[0].userId)
  }
  // 若选择了实验套，根据剩余可选实验按顺序填一个
  if (batchForm.filterSuiteId) {
    const candidate = (availableExperimentsFor(row) || [])[0]
    if (candidate && row.experimentId == null) {
      row.experimentId = candidate.experimentId
    }
  }
}

const saveGroupPlan = async () => {
  if (!semesterId.value) await ensureSemesterId()
  if (!selectedSuiteId.value) {
    ElMessage.warning('请先选择实验套')
    return
  }
  if (!batchForm.singleGroup) {
    ElMessage.warning('请选择小组')
    return
  }
  const enabledRows = groupPlan.value.filter(r => r.enabled && (r.experimentId || r.teacherId))
  if (!enabledRows.length) {
    ElMessage.warning('请至少设置一周的实验或教师')
    return
  }
  try {
    const timeSlot = buildExperimentTime(batchContext.weekday, batchContext.slot, weekType.value)
    const currentWeeks = getBatchWeeksByMode(batchForm.weekMode)
    const items = currentWeeks.map(w => {
      const r = groupPlan.value.find(x => x.week === w)
      const enabled = !!r?.enabled
      return {
        week: `第${w}周`,
        enabled,
        experimentId: enabled && r?.experimentId != null ? Number(r.experimentId) : null,
        teacherId: enabled && r?.teacherId != null ? Number(r.teacherId) : null
      }
    })
    const payload = {
      semesterId: semesterId.value,
      suiteId: selectedSuiteId.value,
      groupName: batchForm.singleGroup,
      timeSlot,
      items
    }
    await batchAssignItems(payload)
    ElMessage.success('保存成功')
    
    // 重新加载回显数据，确保显示最新状态
    if (batchForm.singleGroup) {
      await loadExistingGroupExperiments(batchForm.singleGroup, batchContext.weekday, batchContext.slot)
    }
    
    // 更新课表显示的教师信息
    await loadTeachersFromGroupExperiments()
    
    // 不自动关闭对话框，方便继续调整
  } catch (e) {
    console.error(e)
    ElMessage.error('保存失败')
  }
}

const manualDialogVisible = ref(false)
const manualContext = reactive({ weekday: '', slot: '' })
const manualForm = reactive({ 
  classIds: [], 
  groupName: ''
})

// 批量选择相关状态
const rangeSelectMode = ref(false)
const rangeStart = ref(null)
const rangeEnd = ref(null)
const manualClassOptions = ref([])
const manualStudents = ref([])
const manualSelectedIds = ref([])
const manualLoading = ref(false)
const manualGroupingCompleted = ref(false)
const generatingSchedule = ref(false)
const manualStudentsTable = ref(null)

const openBatchDialog = async (weekday, slot) => {
  try {
    batchDialogInstanceKey.value++
    batchContext.weekday = weekday
    batchContext.slot = slot
    const cell = cellState[weekday][slot]
    
    // 自动填充实验套（当前选择的实验套）
    batchForm.filterSuiteId = selectedSuiteId.value
    
    // 自动设置周次范围（根据weekType）
    batchForm.weekMode = weekType.value === 0 ? 'odd' : 'even'
    
    // 先设置小组名称
    batchForm.singleGroup = (cell.groupNames && cell.groupNames[0]) || ''
    
    // 加载教师列表（使用已有的带进度方法）
    if (!teacherList.value.length) await loadTeacherListWithProgress(true)
    await ensureExperimentsForSuite()
    batchOptionsReady.value = true
    
    const defaultTeacher = (teacherList.value && teacherList.value.length)
      ? String(teacherList.value[0].userId)
      : ((cell.teacherIds && cell.teacherIds[0]) || (isTeacher.value ? String(userInfo.value.userId) : null))
    
    // 初始化周次计划（动态周数）
    const weeksCount = (totalWeeks.value && Number(totalWeeks.value) > 0) ? Number(totalWeeks.value) : 20
    groupPlan.value = Array.from({ length: weeksCount }, (_, i) => ({
      week: i + 1,
      enabled: false,
      experimentId: null,
      teacherId: defaultTeacher != null ? String(defaultTeacher) : null,
    }))
    
    // 回显已有的安排数据（优先回显；若无数据再按单双周默认启用）
    let hasExisting = false
    if (batchForm.singleGroup && semesterId.value && selectedSuiteId.value) {
      hasExisting = await loadExistingGroupExperiments(batchForm.singleGroup, weekday, slot)
    }
    if (!hasExisting) {
      applyWeekMode()
    }
  } catch (error) {
    console.error('打开批量设置对话框失败:', error)
    ElMessage.error('打开批量设置对话框失败: ' + (error.message || '未知错误'))
  }
  
  // 加载班级选项
  await loadManualClassOptions()
  
  manualForm.classIds = []
  manualForm.groupName = ''
  manualStudents.value = []
  manualSelectedIds.value = []
  batchDialogVisible.value = true
}

// 加载并回显已有的小组实验安排
const loadExistingGroupExperiments = async (groupName, weekday, slot) => {
  try {
    // 检查必要的参数
    if (!groupName || !semesterId.value || !selectedSuiteId.value) {
      return false
    }

    const mySeq = ++groupPlanEchoSeq.value
    
    // 查询该小组的所有安排
    const response = await getGroupExperimentByGroup(groupName)
    
    if (mySeq !== groupPlanEchoSeq.value) return

    if (response?.code === 200 && response.data && Array.isArray(response.data)) {
      const currentTimeSlot = buildExperimentTime(weekday, slot, weekType.value)

      // 确定当前周次类型对应的周次列表（规则B：动态教学周列表）
      const currentWeekType = getBatchWeeksByMode(batchForm.weekMode)
      
      const existingRecords = response.data.filter(record => {
        if (!record) return false
        
        // 过滤出属于当前学期和套件的记录
        const recordSemesterId = record.semesterId != null ? Number(record.semesterId) : null
        const recordSuiteId = record.suiteId != null ? Number(record.suiteId) : null
        const currentSemesterId = semesterId.value != null ? Number(semesterId.value) : null
        const currentSuiteId = selectedSuiteId.value != null ? Number(selectedSuiteId.value) : null
        
        if (recordSemesterId !== currentSemesterId || recordSuiteId !== currentSuiteId) {
          return false
        }

        // 严格按时间段过滤，防止跨时间段串数据
        if (!record.timeSlot || String(record.timeSlot).trim() === '') return false
        if (String(record.timeSlot) !== String(currentTimeSlot)) return false

        // 过滤出当前周次类型的记录
        const weekNumber = extractWeekNumber(record.experimentTime)
        return weekNumber > 0 && currentWeekType.includes(weekNumber)
      })

      // 先按当前周次范围重置（缺失的周即视为禁用），避免被默认模式/旧显示污染
      const maxWeek = (totalWeeks.value && Number(totalWeeks.value) > 0) ? Number(totalWeeks.value) : 20
      groupPlan.value.forEach(item => {
        if (!item) return
        if (item.week <= 0 || item.week > maxWeek) return
        if (!currentWeekType.includes(item.week)) return
        item.enabled = false
        item.experimentId = null
        item.teacherId = null
      })
      
      // 回显到 groupPlan 中
      if (mySeq !== groupPlanEchoSeq.value) return
      existingRecords.forEach(record => {
        const weekNumber = extractWeekNumber(record.experimentTime)
        
        if (weekNumber <= 0 || weekNumber > maxWeek) return

        const planItem = groupPlan.value.find(item => item.week === weekNumber)
        if (planItem) {
          // 先设置实验和教师ID，最后设置enabled状态
          planItem.experimentId = record.experimentId != null ? Number(record.experimentId) : null
          // 如果数据库中有教师ID就使用，如果没有就保持null（不要自动设置为默认教师，因为可能还没有分配教师）
          planItem.teacherId = record.teacherId != null ? String(record.teacherId) : null
          planItem.enabled = true  // 最后设置，避免触发onTogglePlanRow
        }
      })

      return existingRecords.length > 0
    }
  } catch (error) {
    console.error('加载已有安排失败:', error)
    // 不显示错误消息，避免干扰用户（如果没有数据就是空的）
  }

  return false
}

// 从时间字符串中提取周次数字
const extractWeekNumber = (timeStr) => {
  if (!timeStr) return 0
  
  try {
    // 尝试解析JSON格式
    if (typeof timeStr === 'string' && timeStr.startsWith('{')) {
      const parsed = JSON.parse(timeStr)
      if (parsed.week) {
        return parseInt(parsed.week)
      }
    }
    
    // 尝试匹配"第X周"格式
    const match = timeStr.match(/第(\d+)周/)
    if (match) {
      return parseInt(match[1])
    }
    
    // 尝试直接提取数字
    const digitMatch = timeStr.match(/(\d+)/)
    if (digitMatch) {
      return parseInt(digitMatch[1])
    }
    
    return 0
  } catch (error) {
    console.error('解析时间字符串失败:', timeStr, error)
    return 0
  }
}

// 重新加载现有数据
const reloadExistingData = async () => {
  if (batchForm.singleGroup && batchDialogVisible.value) {
    // 清空当前计划
    groupPlan.value.forEach(item => {
      item.enabled = false
      item.experimentId = null
      item.teacherId = null
    })
    
    // 重新加载数据
    await loadExistingGroupExperiments(batchForm.singleGroup, batchContext.weekday, batchContext.slot)
    ElMessage.success('数据已重新加载')
  }
}

watch(batchDialogVisible, (v) => {
  if (!v) batchOptionsReady.value = false
})

// 监听小组选择变化，重新加载回显数据
watch(() => batchForm.singleGroup, async (newGroupName) => {
  if (newGroupName && batchDialogVisible.value) {
    // 清空当前计划
    groupPlan.value.forEach(item => {
      item.enabled = false
      item.experimentId = null
      item.teacherId = null
    })
    
    // 重新加载新小组的安排数据
    await loadExistingGroupExperiments(newGroupName, batchContext.weekday, batchContext.slot)
  }
})

// 加载手动分组的班级选项
const loadManualClassOptions = async () => {
  try {
    const response = await getStudentList({ current: 1, size: 1 })
    if (response?.data?.classList) {
      manualClassOptions.value = response.data.classList.map(classId => ({
        value: classId,
        label: classId
      }))
    }
  } catch (error) {
    console.error('加载班级选项失败:', error)
  }
}

const loadManualStudents = async () => {
  try {
    manualLoading.value = true
    const classes = Array.isArray(manualForm.classIds) ? manualForm.classIds.map(String).filter(Boolean) : []
    const results = []
    if (!classes.length) {
      ElMessage.warning('请先选择班级')
      manualStudents.value = []
      return
    }
    const promises = classes.map(cls => getStudentList({ current: 1, size: 500, classId: cls }))
    const responses = await Promise.all(promises)
    responses.forEach(r => {
      const list = r?.data?.studentList?.records || r?.data?.page?.records || r?.data?.records || []
      results.push(...list)
    })
    const seen = new Set()
    manualStudents.value = results.filter(s => {
      const id = s.userId
      if (seen.has(id)) return false
      seen.add(id)
      return true
    })
    // 按学号升序排序（数值感知）
    manualStudents.value.sort((a, b) => String(a?.schoolId || '').localeCompare(String(b?.schoolId || ''), 'zh-Hans-CN', { numeric: true }))
    
    // 重置批量选择参数
    manualSelectedIds.value = []
    
    // 重置批量选择状态
    rangeSelectMode.value = false
    rangeStart.value = null
    rangeEnd.value = null
  } catch (e) {
    console.error(e)
    ElMessage.error('查询学生失败')
  } finally {
    manualLoading.value = false
  }
}

const onManualSelectionChange = (rows) => {
  manualSelectedIds.value = rows.map(r => r.userId)
}

const submitManualGrouping = async () => {
  try {
    if (!manualForm.groupName) {
      ElMessage.warning('请输入组号')
      return
    }
    if (!manualSelectedIds.value.length) {
      ElMessage.warning('请先勾选学生')
      return
    }
    
    // 检查必要参数
    if (!semesterId.value) {
      ElMessage.error('请先选择学期')
      return
    }
    if (!selectedSuiteId.value) {
      ElMessage.error('请先选择实验套')
      return
    }
    
    // 1. 调用assignGroup API，更新学生的分组信息
    await assignGroup({ 
      userIds: manualSelectedIds.value, 
      groupName: manualForm.groupName.trim() 
    })
    
    // 2. 创建或更新ExperimentSchedule记录（按照自动分组的流程）
    try {
      const experimentTime = buildExperimentTime(batchContext.weekday, batchContext.slot, weekType.value)
      
      // 调用updateGroupsByTime API创建/更新ExperimentSchedule记录
      await updateGroupsByTime({
        experimentTime,
        groupNames: manualForm.groupName.trim(),
        semesterId: semesterId.value,
        suiteId: selectedSuiteId.value || undefined,
        weekType: weekType.value
      })
      
    } catch (e3) {
      console.error('创建ExperimentSchedule记录失败', e3)
      // 不阻止主流程，只记录错误
    }
    
    ElMessage.success('分组成功')
    
    // 让当前日历格立即显示为"已分组"，并出现操作按钮
    const cell = cellState[batchContext.weekday]?.[batchContext.slot]
    if (cell) {
      const g = manualForm.groupName.trim()
      if (g && !(cell.groupNames || []).includes(g)) {
        cell.groupNames = [...(cell.groupNames || []), g]
      }
      cell.grouped = true
    }
    
    // 设置分组完成状态，显示课表生成选项
    manualGroupingCompleted.value = true
    
    // 刷新手动表格当前班级学生列表，看到最新组别
    await loadManualStudents()
  } catch (e) {
    console.error(e)
    ElMessage.error('分组失败')
  }
}

// 生成课表函数
const handleGenerateScheduleFromGroups = async () => {
  try {
    generatingSchedule.value = true
    
    if (!semesterId.value) {
      await ensureSemesterId()
    }
    if (!semesterId.value) {
      ElMessage.warning('请先选择学期')
      return
    }
    if (!selectedSuiteId.value) {
      ElMessage.warning('请先选择实验套')
      return
    }

    // 获取当前时间段的分组信息（只获取当前选中的时间段）
    const currentGroups = cellState[batchContext.weekday]?.[batchContext.slot]?.groupNames || []
    
    if (currentGroups.length === 0) {
      ElMessage.warning('当前时间段没有找到可用的分组信息')
      return
    }
    
    // 先将当前时段的小组写入 experiment_schedule，确保打印与后端学生课表有锚点
    try {
      const experimentTime = `${batchContext.weekday}${batchContext.slot}`
      await updateGroupsByTime({
        experimentTime,
        groupNames: currentGroups.join(','),
        semesterId: semesterId.value,
        suiteId: selectedSuiteId.value || undefined,
        weekType: weekType.value
      })
    } catch (e) {
      // 不阻断生成，只提示
      console.warn('更新当前时段分组到ExperimentSchedule失败:', e)
    }

    // 只生成当前周次类型的课表
    const response = await generateScheduleFromGroups({
      semesterId: semesterId.value,
      suiteId: selectedSuiteId.value,
      groups: currentGroups,
      weekType: weekType.value
    })
    
    if (response.code === 200) {
      const suiteName = getSuiteName(selectedSuiteId.value)
      const weekTypeText = weekType.value === 0 ? '单' : '双'
      const timeSlotText = `${batchContext.weekday}${batchContext.slot}`
      ElMessage.success(`${suiteName}${weekTypeText}周${timeSlotText}课表生成成功！`)
      manualGroupingCompleted.value = false
      // 刷新页面数据
      await loadScheduleList()
      batchDialogVisible.value = false
    } else {
      ElMessage.error(response.message || '课表生成失败')
    }
    
  } catch (error) {
    console.error('生成课表失败:', error)
    ElMessage.error('生成课表失败')
  } finally {
    generatingSchedule.value = false
  }
}

// 重置手动分组状态
const resetManualGrouping = () => {
  manualGroupingCompleted.value = false
  manualForm.groupName = ''
  manualSelectedIds.value = []
  manualStudents.value = []
  
  // 重置范围选择状态
  rangeSelectMode.value = false
  rangeStart.value = null
  rangeEnd.value = null
}

// 开始范围选择模式
const startRangeSelection = () => {
  rangeSelectMode.value = true
  rangeStart.value = null
  rangeEnd.value = null
  ElMessage.info('范围选择模式已开启，请点击第一个学生，然后点击最后一个学生')
}

// 取消范围选择模式
const cancelRangeSelection = () => {
  rangeSelectMode.value = false
  rangeStart.value = null
  rangeEnd.value = null
  ElMessage.info('已取消范围选择模式')
}

// 确认范围选择
const confirmRangeSelection = () => {
  if (rangeStart.value === null || rangeEnd.value === null || !manualStudentsTable.value) {
    ElMessage.warning('请先选择开始和结束位置')
    return
  }
  
  const startIndex = Math.min(rangeStart.value, rangeEnd.value)
  const endIndex = Math.max(rangeStart.value, rangeEnd.value)
  
  // 清空当前选择
  manualStudentsTable.value.clearSelection()
  
  // 选择指定范围的学生
  let selectedCount = 0
  const selectedRows = []
  
  for (let i = startIndex; i <= endIndex; i++) {
    const student = manualStudents.value[i]
    if (student) {
      try {
        manualStudentsTable.value.toggleRowSelection(student, true)
        selectedRows.push(student)
        selectedCount++
      } catch (error) {
        console.error(`选中学生失败: ${student.realName}`, error)
      }
    }
  }
  
  // 手动更新选择状态，确保同步
  setTimeout(() => {
    manualSelectedIds.value = selectedRows.map(r => r.userId)
  }, 100)
  
  ElMessage.success(`已选择第${startIndex + 1}到第${endIndex + 1}个学生，共${selectedCount}人`)
  
  // 退出范围选择模式
  rangeSelectMode.value = false
  rangeStart.value = null
  rangeEnd.value = null
}

// 全选所有学生
const selectAllStudents = () => {
  if (!manualStudentsTable.value) return
  
  manualStudentsTable.value.toggleAllSelection(true)
  ElMessage.success(`已全选所有${manualStudents.value.length}名学生`)
}

// 按班级选择学生
const selectByClass = () => {
  if (!manualForm.classIds || manualForm.classIds.length === 0) {
    ElMessage.warning('请先选择班级')
    return
  }
  
  if (!manualStudentsTable.value) return
  
  // 清空当前选择
  manualStudentsTable.value.clearSelection()
  
  // 选择指定班级的学生
  const selectedRows = []
  manualStudents.value.forEach(student => {
    if (manualForm.classIds.includes(student.classId)) {
      manualStudentsTable.value.toggleRowSelection(student, true)
      selectedRows.push(student)
    }
  })
  
  // 更新选择状态
  manualSelectedIds.value = selectedRows.map(r => r.userId)
  
  ElMessage.success(`已选择${selectedRows.length}名${manualForm.classIds.join(', ')}班的学生`)
}

// 范围选择行点击事件处理
const onRangeRowClick = (row, column, event) => {
  if (!rangeSelectMode.value) return
  
  const rowIndex = manualStudents.value.findIndex(student => student.userId === row.userId)
  if (rowIndex === -1) {
    console.error('找不到学生行索引:', row)
    return
  }
  
  if (rangeStart.value === null) {
    // 第一次点击，设置开始位置
    rangeStart.value = rowIndex
    ElMessage.info(`已选择开始位置：第${rowIndex + 1}个学生 (${row.realName})`)
  } else if (rangeEnd.value === null) {
    // 第二次点击，设置结束位置
    rangeEnd.value = rowIndex
    const startIndex = Math.min(rangeStart.value, rowIndex)
    const endIndex = Math.max(rangeStart.value, rowIndex)
    const count = endIndex - startIndex + 1
    ElMessage.success(`已选择结束位置：第${rowIndex + 1}个学生 (${row.realName})，将选择${count}人，请点击"确认范围"按钮`)
  } else {
    // 如果已经选择了开始和结束，重新开始选择
    rangeStart.value = rowIndex
    rangeEnd.value = null
    ElMessage.info(`重新开始选择：第${rowIndex + 1}个学生 (${row.realName})`)
  }
}

// 获取范围选择行的CSS类名
const getRangeRowClassName = ({ row, rowIndex }) => {
  if (!rangeSelectMode.value) return ''
  
  if (rangeStart.value === rowIndex) {
    return 'range-select-start'
  } else if (rangeEnd.value === rowIndex) {
    return 'range-select-end'
  }
  return ''
}

// 清空所有选择
const clearAllSelection = () => {
  if (manualStudentsTable.value) {
    manualStudentsTable.value.clearSelection()
  }
  manualSelectedIds.value = []
  manualForm.startIndex = null
  manualForm.endIndex = null
}

// 检查是否有现有分组的计算属性
const hasExistingGroups = computed(() => {
  return Object.values(cellState)
    .flatMap(slot => Object.values(slot))
    .some(cell => cell.grouped && cell.groupNames && cell.groupNames.length > 0)
})

// 从批量对话框生成课表
const generateScheduleFromBatchDialog = async () => {
  try {
    generatingSchedule.value = true
    
    if (!semesterId.value) {
      await ensureSemesterId()
    }
    if (!semesterId.value) {
      ElMessage.warning('请先选择学期')
      return
    }
    if (!selectedSuiteId.value) {
      ElMessage.warning('请先选择实验套')
      return
    }

    // 获取当前时间段的分组信息（只获取当前选中的时间段）
    const currentGroups = cellState[batchContext.weekday]?.[batchContext.slot]?.groupNames || []
    
    if (currentGroups.length === 0) {
      ElMessage.warning('当前时间段没有找到可用的分组信息')
      return
    }
    
    // 只生成当前周次类型的课表
    const response = await generateScheduleFromGroups({
      semesterId: semesterId.value,
      suiteId: selectedSuiteId.value,
      groups: currentGroups,
      weekType: weekType.value
    })
    
    if (response.code === 200) {
      const suiteName = getSuiteName(selectedSuiteId.value)
      const weekTypeText = weekType.value === 0 ? '单' : '双'
      const timeSlotText = `${batchContext.weekday}${batchContext.slot}`
      ElMessage.success(`${suiteName}${weekTypeText}周${timeSlotText}课表生成成功！`)
      // 刷新页面数据
      await loadScheduleList()
      batchDialogVisible.value = false
    } else {
      ElMessage.error(response.message || '课表生成失败')
    }
    
  } catch (error) {
    console.error('生成课表失败:', error)
    ElMessage.error('生成课表失败')
  } finally {
    generatingSchedule.value = false
  }
}

// 打开教师设置对话框
const openTeacherSettingDialog = async () => {
  try {
    // 设置实验套ID
    teacherSettingForm.suiteId = selectedSuiteId.value || batchForm.filterSuiteId
    
    if (!teacherSettingForm.suiteId) {
      ElMessage.warning('请先选择实验套')
      return
    }
    
    // 加载实验套的实验列表
    await loadExperimentsForTeacherSetting()
    
    // 显示对话框
    teacherSettingDialogVisible.value = true
    
  } catch (error) {
    console.error('打开教师设置对话框失败:', error)
    ElMessage.error('打开教师设置对话框失败')
  }
}

// 获取实验套名称
const getSuiteName = (suiteId) => {
  if (!suiteId) return ''
  const suite = suiteOptions.value.find(s => s.suiteId === suiteId || s.experimentSuiteId === suiteId)
  return suite ? suite.suiteName : ''
}

// 加载实验套的实验列表用于教师设置
const loadExperimentsForTeacherSetting = async () => {
  try {
    // 获取实验套信息，包含实验顺序
    const suiteResponse = await getSuiteList({ current: 1, size: 1000 })
    const suites = suiteResponse?.data?.records || suiteResponse?.data || []
    const currentSuite = suites.find(s => s.suiteId === teacherSettingForm.suiteId || s.experimentSuiteId === teacherSettingForm.suiteId)
    
    if (!currentSuite) {
      ElMessage.warning('未找到实验套信息')
      return
    }
    
    // 解析实验套中的实验ID顺序
    let experimentIds = []
    try {
      if (currentSuite.experimentIds) {
        experimentIds = JSON.parse(currentSuite.experimentIds)
      }
    } catch (e) {
      console.error('解析实验套实验ID失败:', e)
    }
    
    if (experimentIds.length === 0) {
      ElMessage.warning('该实验套没有实验')
      return
    }
    
    // 获取实验详细信息
    const response = await getExperimentList({ current: 1, size: 1000 })
    const allExperiments = response?.data?.records || response?.data || []
    
    // 按照实验套中的顺序排列实验
    const orderedExperiments = []
    for (const expId of experimentIds) {
      const exp = allExperiments.find(e => e.experimentId === expId)
      if (exp) {
        orderedExperiments.push(exp)
      }
    }
    
    if (orderedExperiments.length === 0) {
      ElMessage.warning('无法获取实验信息')
      return
    }
    
    // 按每2个实验分组
    const plan = []
    for (let i = 0; i < orderedExperiments.length; i += 2) {
      const groupIndex = Math.floor(i / 2)
      const startIndex = i
      const endIndex = Math.min(i + 1, orderedExperiments.length - 1)
      
      plan.push({
        groupIndex,
        startIndex,
        endIndex,
        experiments: orderedExperiments.slice(i, i + 2),
        teacherId: null,
        isIntroClass: false
      })
    }
    
        // 获取已有的教师分配信息以支持回显（按实际时间段匹配）
        if (semesterId.value && batchContext.weekday && batchContext.slot) {
          try {
            const params = {
              semesterId: semesterId.value,
              suiteId: teacherSettingForm.suiteId,
              weekType: weekType.value
            }
            
            const groupExperimentsResponse = await getAllGroupExperiments(params)
            const allGroupExperiments = groupExperimentsResponse.data || []
            
            // 筛选当前时间段的实验数据（使用与打印一致的时间匹配）
            const prefix = weekType.value === 0 ? '单' : '双'
            const fullTimeSlot = `${prefix}${batchContext.weekday}${batchContext.slot}`
            const currentTimeSlotExperiments = allGroupExperiments.filter(ge => {
              // 优先使用后端回填的 timeSlot 字段（group_experiment.time_slot），精确区分周一/周二等时段
              const ts = String(ge?.timeSlot || '')
              if (ts) return ts.replace(/\s+/g, '') === fullTimeSlot.replace(/\s+/g, '')
              // 兼容旧数据：没有 timeSlot 时退化为旧匹配（可能会串）
              const st = ge.experimentTime || ge.scheduleTime || ''
              return matchTime(st, prefix, batchContext.weekday, batchContext.slot, ge)
            })
            
            // 按实验ID分组，每个实验ID取第一个有教师ID的记录（确保同一实验在多个周次时取第一个）
            const experimentTeacherMap = new Map()
            const experimentIntroMap = new Map()
            
            currentTimeSlotExperiments.forEach(group => {
              if (group.experimentId && group.teacherId) {
                const expId = group.experimentId
                // 如果该实验ID还没有记录，或者当前记录有绪论课标记，则更新
                if (!experimentTeacherMap.has(expId) || group.isIntroCourse === 1) {
                  experimentTeacherMap.set(expId, String(group.teacherId))
                  experimentIntroMap.set(expId, group.isIntroCourse === 1)
                }
              }
            })
            
            // 更新教师分配计划中的教师ID和绪论课标记
            plan.forEach(planItem => {
              // 检查该planItem中的每个实验是否已有教师分配
              for (const exp of planItem.experiments) {
                if (experimentTeacherMap.has(exp.experimentId)) {
                  // 如果planItem还没有教师，或者这个实验有绪论课标记，则更新
                  if (!planItem.teacherId || experimentIntroMap.get(exp.experimentId)) {
                    planItem.teacherId = experimentTeacherMap.get(exp.experimentId)
                    planItem.isIntroClass = experimentIntroMap.get(exp.experimentId) || false
                    break // 一个planItem只需要设置一次
                  }
                }
              }
            })
          } catch (error) {
            console.error('获取已有的教师分配信息失败:', error)
            // 即使获取失败也继续，使用默认值
          }
        }
    
    teacherAssignmentPlan.value = plan
    
  } catch (error) {
    console.error('加载实验列表失败:', error)
    ElMessage.error('加载实验列表失败')
  }
}

// 保存教师分配
const handleSaveTeacherAssignment = async () => {
  try {
    // 验证是否所有组都分配了教师
    const unassignedGroups = teacherAssignmentPlan.value.filter(item => !item.teacherId)
    if (unassignedGroups.length > 0) {
      ElMessage.warning('请为所有实验组分配教师')
      return
    }
    
    // 设置保存中状态
    savingTeacherAssignment.value = true
    
    // 构建请求数据
    const requestData = {
      semesterId: semesterId.value,
      suiteId: teacherSettingForm.suiteId,
      timeSlot: `${batchContext.weekday}${batchContext.slot}`,
      weekType: weekType.value,
      assignments: teacherAssignmentPlan.value.flatMap(item => 
        item.experiments.map(exp => ({
          experimentId: exp.experimentId,
          teacherId: item.teacherId,
          isIntroCourse: item.isIntroClass ? 1 : 0
        }))
      )
    }
    
    // 调用后端API保存教师分配
    const response = await saveTeacherAssignment(requestData)
    
    if (response.code === 200) {
      ElMessage.success('教师分配保存成功')
      teacherSettingDialogVisible.value = false
      // 刷新课表数据
      await loadScheduleList()
      
      // 如果批量设置对话框是打开的，需要重新加载数据以显示更新后的教师信息
      if (batchDialogVisible.value && batchForm.singleGroup) {
        await loadExistingGroupExperiments(batchForm.singleGroup, batchContext.weekday, batchContext.slot)
      }
    } else {
      ElMessage.error(response.message || '教师分配保存失败')
    }
    
  } catch (error) {
    console.error('保存教师分配失败:', error)
    ElMessage.error('保存教师分配失败')
  } finally {
    // 无论成功失败都要关闭加载状态
    savingTeacherAssignment.value = false
  }
}

const filteredExperimentList = computed(() => {
  const all = experimentList.value || []
  if (!batchForm.filterSuiteId) return all
  const filtered = all.filter(e => {
    const sid = e.suiteId || e.experimentSuiteId
    return String(sid || '') === String(batchForm.filterSuiteId)
  })
  // 若后端数据缺少套ID或不匹配，回退显示全部，避免看起来"没有实验"
  return filtered.length ? filtered : all
})

// 计算某行可用实验：过滤掉其他启用行已选择的实验，实现"选过的不再出现"
const availableExperimentsFor = (row) => {
  const base = filteredExperimentList.value || []
  const pickedIds = new Set(
    (groupPlan.value || [])
      .filter(r => r !== row && r.enabled && r.experimentId != null)
      .map(r => Number(r.experimentId))
  )
  const currentId = row?.experimentId != null ? Number(row.experimentId) : null
  return base.filter(e => {
    const id = Number(e.experimentId)
    return !pickedIds.has(id) || (currentId != null && id === currentId)
  })
}

// 清空所有启用行的实验（用于切换套）
const clearEnabledExperiments = () => {
  (groupPlan.value || []).forEach(r => { if (r.enabled) r.experimentId = null })
}

// 当选择了实验套时，为"启用"的周按顺序分配默认实验；未选择实验套则不填默认值
const assignSequentialExperiments = () => {
  if (!batchForm.filterSuiteId) {
    // 没有选择套：不提供默认实验
    (groupPlan.value || []).forEach(r => { if (r.enabled) r.experimentId = null })
    return
  }
  const base = filteredExperimentList.value || []
  if (!base.length) return
  const enabledRows = (groupPlan.value || []).filter(r => r.enabled).sort((a,b) => a.week - b.week)
  const used = new Set()
  enabledRows.forEach(r => { if (r.experimentId != null) used.add(Number(r.experimentId)) })
  let idx = 0
  enabledRows.forEach(r => {
    if (r.experimentId != null) return
    while (idx < base.length && used.has(Number(base[idx].experimentId))) idx++
    if (idx < base.length) {
      r.experimentId = base[idx].experimentId
      used.add(Number(r.experimentId))
      idx++
    }
  })
}

const applyWeekMode = () => {
  const mode = batchForm.weekMode
  const enabledSet = new Set()
  if (mode === 'odd') {
    // 单周：使用后端动态教学周列表（无数据时回退旧逻辑）
    getTeachingWeeksByMode('odd').forEach(w => enabledSet.add(w))
  } else if (mode === 'even') {
    // 双周：使用后端动态教学周列表（无数据时回退旧逻辑）
    getTeachingWeeksByMode('even').forEach(w => enabledSet.add(w))
  }

  groupPlan.value.forEach(r => {
    const shouldEnable = enabledSet.has(r.week)
    if (r.enabled !== shouldEnable) {
      r.enabled = shouldEnable
      if (!r.enabled) {
        r.experimentId = null
        r.teacherId = null
      } else {
        onTogglePlanRow(r)
      }
    }
  })
}



onMounted(async () => {
  initCalendarState()
  await ensureSemesterId()
  await refreshWeekTypeBySemester()
  await initSuiteSelection()
  // 教师列表和课表数据互不依赖，首次进入并行加载，减少页面空等时间
  await Promise.all([
    loadTeacherList(),
    loadScheduleList()
  ])
  loadExperimentList()
  // 确保教师列表加载完成后再填充教师标签
  if (teacherList.value.length > 0) {
    hydrateTeacherTagsFromIds()
  }
  
  // 监听localStorage中学期ID的变化
  const handleStorageChange = (e) => {
    if (e.key === 'semesterId' || e.key === 'currentSemesterId') {
      // 重新获取当前学期并刷新页面
      ensureSemesterId().then(() => {
        loadScheduleList()
        studentScheduleData.value = {}
        dataCache.value.studentScheduleData = null
      })
    }
  }
  
  window.addEventListener('storage', handleStorageChange)
  
  // 页面卸载时移除事件监听器
  onUnmounted(() => {
    window.removeEventListener('storage', handleStorageChange)
  })
})


// 课表相关方法
// 按 (套件, 单双周, 时段, 星期) 定位课表记录，兼容多种 experimentTime 格式
const findCellSchedule = (suiteId, weekType, timeSlot, weekday) => {
  const weekTypeText = weekType === 0 ? '单周' : '双周'
  const weekdayNum = weekday.replace('周', '')
  const timeString = `${weekTypeText}${weekdayNum}${timeSlot === 'morning' ? '上午' : '下午'}`
  return scheduleList.value.find(s => {
    const sSuiteId = s.suiteId || s.experimentSuiteId
    const suiteMatch = sSuiteId === undefined || sSuiteId === null || String(sSuiteId) === String(suiteId)
    const weekTypeMatch = String(s.weekType) === String(weekType)
    const experimentTime = s.experimentTime || ''
    const timeMatch = experimentTime === timeString ||
                     experimentTime.replace(/^(单|双)周/, '周') === timeString ||
                     experimentTime.replace(/^(单|双)/, '') === timeString
    return suiteMatch && weekTypeMatch && timeMatch
  })
}

// 解析形如 "[1,2,3]" 或 "1,2,3" 的字符串为去空白后的非空字符串数组
const parseBracketedCsv = (raw) => {
  if (!raw) return []
  return String(raw).replaceAll(/[\[\]]/g, '').split(',').map(v => v.trim()).filter(Boolean)
}

const getGroupIdsForCell = (suiteId, weekType, timeSlot, weekday) => {
  try {
    const schedule = findCellSchedule(suiteId, weekType, timeSlot, weekday)
    
    if (schedule && schedule.groupIds) {
      // 解析groupIds字符串
      const groupIds = parseBracketedCsv(schedule.groupIds)
      if (groupIds.length > 0) {
        // 每行显示两个小组，确保格式正确
        let result = ''
        for (let i = 0; i < groupIds.length; i += 2) {
          if (i > 0) result += '\n'
          if (i + 1 < groupIds.length) {
            // 两个小组在同一行
            result += `${groupIds[i]}  ${groupIds[i + 1]}`
          } else {
            // 最后一个小组单独一行
            result += groupIds[i]
          }
        }
        return result
      }
    }
    return ''
  } catch (error) {
    console.error('获取小组ID失败:', error)
    return ''
  }
}

const getTeacherNamesForCell = (suiteId, weekType, timeSlot, weekday) => {
  try {
    const schedule = findCellSchedule(suiteId, weekType, timeSlot, weekday)
    
    // 优先方案：从schedule.teacherIds获取（每个时间段固定的5个教师）
    if (schedule && schedule.teacherIds) {
      const teacherIds = parseBracketedCsv(schedule.teacherIds)
      if (teacherIds.length > 0) {
        const teacherNames = teacherIds
          .map(id => teacherIdToName(id) || id)
          .filter(name => name)

        // 查找绪论课标记
        const introCourseFlags = []
        const weekTypeWeeks = getWeekTypeWeeksForIntro(weekType)

        allGroupExperimentsData.value.forEach(ge => {
          if (String(ge.suiteId) === String(suiteId) && ge.teacherId && ge.isIntroCourse === 1) {
            const weekMatch = ge.experimentTime?.match(/第(\d+)周/)
            if (weekMatch) {
              const week = parseInt(weekMatch[1])
              if (weekTypeWeeks.includes(week)) {
                const idx = teacherIds.findIndex(id => String(id) === String(ge.teacherId))
                if (idx >= 0 && !introCourseFlags[idx]) {
                  introCourseFlags[idx] = '绪'
                }
              }
            }
          }
        })

        // 补齐缺失的标记
        while (introCourseFlags.length < teacherNames.length) {
          introCourseFlags.push('')
        }

        return {
          teacherNames,
          introCourseFlags
        }
      }
    }
    
    // 回退方案：从小组数据收集教师（如果teacherIds不存在）
    if (schedule && schedule.groupIds) {
      // 解析小组列表
      const groupList = parseBracketedCsv(schedule.groupIds)
      
      if (groupList.length > 0) {
        // 从group_experiment数据中获取这些小组的教师信息
        const weekTypeWeeks = getWeekTypeWeeksForIntro(weekType)
        
        // 按小组收集教师信息（每个小组对应一个教师，不去重，显示所有教师）
        const collectedTeachers = [] // { teacherId, name, isIntroCourse, groupName }
        
        groupList.forEach(groupName => {
          // 查找该小组在该时间段（weekType对应的周次）的所有实验记录
          const groupRecords = allGroupExperimentsData.value.filter(ge => {
            const suiteIdMatch = !suiteId || suiteId === 0 || String(ge.suiteId) === String(suiteId) || 
                                String(ge.experimentSuiteId) === String(suiteId)
            if (!suiteIdMatch || ge.groupName !== groupName) return false
            
            const weekMatch = ge.experimentTime?.match(/第(\d+)周/)
            if (!weekMatch) return false
            
            const week = parseInt(weekMatch[1])
            return weekTypeWeeks.includes(week) && ge.teacherId != null
          })
          
          // 为每个小组收集教师信息（每个小组一个教师）
          if (groupRecords.length > 0) {
            // 按周次排序，取最早的
            groupRecords.sort((a, b) => {
              const weekA = parseInt((a.experimentTime?.match(/第(\d+)周/) || [])[1] || 999)
              const weekB = parseInt((b.experimentTime?.match(/第(\d+)周/) || [])[1] || 999)
              return weekA - weekB
            })
            
            const firstRecord = groupRecords[0]
            if (firstRecord.teacherId) {
              const teacherId = String(firstRecord.teacherId)
              
              let teacherName = firstRecord.teacherName
              if (!teacherName && teacherList.value && teacherList.value.length > 0) {
                teacherName = teacherIdToName(teacherId) || teacherId
              }
              if (!teacherName) {
                teacherName = teacherId
              }
              
              // 检查该小组是否有绪论课标记（只检查该小组的记录，不跨组）
              // 注意：只检查该小组在最早周次（第一个周次）的记录是否有绪论课标记
              const earliestWeek = Math.min(...groupRecords.map(ge => {
                const weekMatch = ge.experimentTime?.match(/第(\d+)周/)
                return weekMatch ? parseInt(weekMatch[1]) : 999
              }))
              const earliestRecord = groupRecords.find(ge => {
                const weekMatch = ge.experimentTime?.match(/第(\d+)周/)
                return weekMatch && parseInt(weekMatch[1]) === earliestWeek
              })
              const isIntroCourse = earliestRecord && earliestRecord.isIntroCourse === 1
              
              collectedTeachers.push({
                teacherId: teacherId,
                name: teacherName,
                isIntroCourse: isIntroCourse,
                groupName: groupName
              })
            }
          }
        })
        
        // 去重：按教师ID去重，但保留每个教师是否有绪论课的准确信息
        // 注意：如果一个老师负责多个小组，只要其中一个小组有绪论课，该老师就标记为有绪论课
        const teacherMap = new Map() // teacherId -> { name, hasIntroCourse }
        collectedTeachers.forEach(teacher => {
          const teacherId = teacher.teacherId
          if (!teacherMap.has(teacherId)) {
            teacherMap.set(teacherId, {
              name: teacher.name,
              hasIntroCourse: teacher.isIntroCourse
            })
          } else {
            // 如果该教师已被记录，只要任何一个小组有绪论课标记，就标记为有绪论课
            const existing = teacherMap.get(teacherId)
            if (!existing.hasIntroCourse && teacher.isIntroCourse) {
              existing.hasIntroCourse = true
            }
          }
        })
        
        // 将收集到的教师信息添加到cellState
        if (collectedTeachers.length > 0) {
          // 确保cellState中有teachers数组
          if (!cellState[weekday][slot].teachers) {
            cellState[weekday][slot].teachers = []
          }
          // 转换为数组，保持添加顺序
          const teacherData = Array.from(teacherMap.values()).map(t => ({
            name: t.name,
            isIntroCourse: t.hasIntroCourse
          }))
          cellState[weekday][slot].teachers.push(...teacherData)
        }
        
        // 转换为数组，保持添加顺序
        const teacherData = Array.from(teacherMap.values()).map(t => ({
          name: t.name,
          isIntroCourse: t.hasIntroCourse
        }))
        
        // 返回对象，包含教师名字数组和绪论课标记数组
        return {
          teacherNames: teacherData.map(t => t.name),
          introCourseFlags: teacherData.map(t => t.isIntroCourse ? '绪' : '')
        }
      }
    }
    
    return { teacherNames: [], introCourseFlags: [] }
  } catch (error) {
    console.error('获取教师姓名失败:', error)
    return { teacherNames: [], introCourseFlags: [] }
  }
}

const openPrintScheduleDialog = async () => {
  try {
    // 立即设置按钮加载状态
    printButtonLoading.value = true
    
    // 立即打开对话框，提供即时反馈
    showPrintScheduleDialog.value = true
    
    // 检查基本条件
    if (!semesterId.value) {
      ElMessage.warning('请先选择学期')
      return
    }
    
    if (suiteOptions.value.length === 0) {
      ElMessage.warning('请先加载实验套信息')
      return
    }
    
    // 如果有预加载的数据或缓存，立即可用；否则异步加载
    if (isCacheValid() || preloadPromise.value) {
      // 使用缓存数据或等待预加载完成
      if (preloadPromise.value) {
        preloadPromise.value.catch(() => {
          // 预加载失败，重新加载
          loadAllDataForPrint().catch(error => {
            console.error('加载数据失败:', error)
            ElMessage.error('加载数据失败，请重试')
          })
        })
      } else {
        // 使用缓存数据
        loadAllDataForPrint()
      }
    } else {
      // 没有缓存，需要重新加载
      loadAllDataForPrint().catch(error => {
        console.error('加载数据失败:', error)
        ElMessage.error('加载数据失败，请重试')
      })
    }
    
  } catch (error) {
    console.error('打开打印课表对话框失败:', error)
    ElMessage.error('打开对话框失败')
  } finally {
    // 确保按钮加载状态被清除
    printButtonLoading.value = false
  }
}

// 加载学生课表数据
const loadStudentScheduleData = async () => {
  try {
    if (!semesterId.value) {
      await ensureSemesterId()
    }
    
    if (!semesterId.value) {
      return {}
    }
    
    const response = await getStudentSchedule(semesterId.value)
    
    if (response.code === 200) {
      studentScheduleData.value = response.data || {}
      return response.data || {}
    } else {
      console.error('加载学生课表数据失败:', response.message)
      return {}
    }
  } catch (error) {
    console.error('加载学生课表数据失败:', error)
    return {}
  }
}

// 获取实验套的实验列表
const getExperimentsForSuite = (suiteId) => {
  if (!suiteId) return []
  
  // 从实验列表中过滤出属于该实验套的实验
  const suiteExperiments = experimentList.value.filter(exp => 
    exp.suiteId === suiteId || exp.experimentSuiteId === suiteId
  )
  
  // 如果没有找到实验，返回空数组
  if (suiteExperiments.length === 0) return []
  
  // 按实验ID排序，确保顺序一致
  return suiteExperiments.sort((a, b) => a.experimentId - b.experimentId)
}

// 获取打印列用的实验名称（固定最多10列，不足补空）
const getExperimentColumnNames = (suiteId) => {
  // 1) 优先从套件实验列表获取
  const suiteExperiments = getExperimentsForSuite(suiteId)
  let names = (suiteExperiments || []).map(e => String(e.experimentName || ''))
  // 2) 兜底：从学生课表数据聚合
  if (names.length === 0 && studentScheduleData.value) {
    const tryKeys = [`suite_${suiteId}`, `suite${suiteId}`, String(suiteId)]
    for (const key of tryKeys) {
      const suiteData = studentScheduleData.value[key]
      if (!suiteData) continue
      const arr = []
      if (Array.isArray(suiteData.oddWeek)) arr.push(...suiteData.oddWeek)
      if (Array.isArray(suiteData.evenWeek)) arr.push(...suiteData.evenWeek)
      names = [...new Set(arr.map(x => String(x.experimentName || '')).filter(Boolean))]
      break
    }
  }
  // 固定10列
  names = [...new Set(names)].slice(0, 10)
  while (names.length < 10) names.push('')
  return names
}

// 获取指定时间段的小组列表（使用 scheduleTime 精确匹配："单/双" + "周X上午/下午"）
// 统一时间匹配：兼容 空格/括号/AM-PM/"下 午" 等写法
const matchTime = (raw, prefix, weekday, slot, item) => {
  const st = String(raw || '')
  const stNorm = st.replace(/\s+/g, '')
  // week
  const weekTarget = prefix === '单' ? 0 : 1
  const weekOk = stNorm.startsWith(prefix) || stNorm.includes(prefix)
    || (item && (
      Number(item.weekType) === weekTarget ||
      String(item.weekType || '').toLowerCase().includes(prefix === '单' ? 'odd' : 'even') ||
      String(item.weekLabel || item.weekStr || '').includes(prefix)
    ))
  if (!weekOk) return false
  // day
  const dayChar = String(weekday).replace(/^[周星期]/, '')
  const dayOk = new RegExp(`(?:周|星期)?${dayChar}`).test(stNorm)
    || (item && (
      String(item.weekday || item.day || '') === String(weekday) ||
      String(item.weekday || item.day || '').includes(dayChar)
    ))
  if (!dayOk) return false
  // slot
  const slotOk = (slot === '上午'
    ? /(上午|早上|AM|am|上\(?午\)?)/.test(stNorm)
    : /(下午|午后|PM|pm|下\(?午\)?)/.test(stNorm))
    || (item && (
      String(item.timeSlot || item.slot || item.period || '').includes(slot)
      || (slot === '上午' && /(AM|am)/.test(String(item.timeSlot || item.slot || '')))
      || (slot === '下午' && /(PM|pm)/.test(String(item.timeSlot || item.slot || '')))
    ))
  return !!slotOk
}

const getGroupsForTimeBlock = (suiteId, weekType, weekday, slot) => {
  if (!suiteId || !studentScheduleData.value) return []

  // 根据套件ID获取数据 - 尝试多种可能的键格式
  let suiteData = null
  const possibleKeys = [
    `suite_${suiteId}`,
    `suite${suiteId}`,
    String(suiteId)
  ]
  for (const key of possibleKeys) {
    if (studentScheduleData.value[key]) { suiteData = studentScheduleData.value[key]; break }
  }
  if (!suiteData) return []

  const weekData = weekType === 0 ? suiteData.oddWeek : suiteData.evenWeek
  if (!Array.isArray(weekData)) return []

  const prefix = weekType === 0 ? '单' : '双'
  let filtered = weekData.filter(item => matchTime(item.scheduleTime, prefix, weekday, slot, item))
  // 若未匹配到下午，做一次更宽松的兜底：只要含有“下.*午”即可
  if (filtered.length === 0 && slot === '下午') {
    filtered = weekData.filter(item => {
      const st = String(item.scheduleTime || '').replace(/\s+/g, '')
      return st.startsWith(prefix) && /下.*午|下午|午后|PM|pm/.test(st)
        && new RegExp(`(?:周|星期)?${String(weekday).replace(/^[周星期]/,'')}`).test(st)
    })
    if (filtered.length === 0) {
      // debug: 记录一次未匹配下午的样本，便于定位格式
    }
  }

  // 如果仍为空，使用 scheduleList 的安排作为兜底，解析 groupIds
  if (filtered.length === 0) {
    try {
      const list = Array.isArray(scheduleList.value) ? scheduleList.value : []
      const fallback = list.filter(row => {
        const sid = row.suiteId || row.experimentSuiteId
        return String(sid) === String(suiteId) && matchTime(row.experimentTime || row.scheduleTime, prefix, weekday, slot, row)
      })
      const parseGroupIds = (raw) => {
        if (!raw) return []
        try {
          if (Array.isArray(raw)) return raw.map(String)
          let s = String(raw).trim()
          // 形如 "[231011A,231011B]" or "231011A,231011B"
          s = s.replace(/^[\[]|[\]]$/g, '')
          if (!s) return []
          return s.split(',').map(v => String(v).trim()).filter(Boolean)
        } catch { return [] }
      }
      const names = new Set()
      fallback.forEach(row => {
        parseGroupIds(row.groupIds).forEach(g => names.add(g))
      })
      if (names.size) {
        return [...names].sort().map(groupName => ({ groupName }))
      }
    } catch (e) {
      console.warn('fallback 使用 scheduleList 解析失败:', e)
    }
  }

  const groupNames = [...new Set(filtered.map(item => item.groupName).filter(Boolean))]
  // 若为学生用户，仅显示其所在小组
  let finalGroups = groupNames
  if (isStudent.value && studentGroupName.value) {
    finalGroups = groupNames.filter(g => String(g) === studentGroupName.value)
  }
  return finalGroups.sort().map(groupName => ({ groupName }))
}

// 获取小组在指定实验中的安排信息（返回"第X周"，不含日期）
const getExperimentScheduleForGroup = (groupName, experimentName, weekType, weekday, slot) => {
  if (!groupName || !experimentName || !studentScheduleData.value) {
    return ''
  }
  
  for (const suiteKey in studentScheduleData.value) {
    const suiteData = studentScheduleData.value[suiteKey]
    if (!suiteData) continue
    const weekData = weekType === 0 ? suiteData.oddWeek : suiteData.evenWeek
    if (!Array.isArray(weekData)) continue

    const prefix = weekType === 0 ? '单' : '双'
    let found = weekData.find(item =>
      item.groupName === groupName
      && String(item.experimentName || '') === String(experimentName)
      && matchTime(item.scheduleTime, prefix, weekday, slot, item)
    )
    if (!found && slot === '下午') {
      found = weekData.find(item => {
        const st = String(item.scheduleTime || '').replace(/\s+/g, '')
        const dayChar = String(weekday).replace(/^[周星期]/, '')
        return item.groupName === groupName
          && String(item.experimentName || '') === String(experimentName)
          && st.startsWith(prefix)
          && new RegExp(`(?:周|星期)?${dayChar}`).test(st)
          && /下.*午|下午|午后|PM|pm/.test(st)
      })
    }
    if (found) {
      // 仅返回周次字符串（例如："第3周"）
      return String(found.weekNumber || '').replace(/\s+/g, '')
    }
  }
  return '-'
}

const printSchedule = () => {
  try {
    const printContent = document.querySelector('.schedule-view-container')
    if (!printContent) {
      console.error('找不到课表内容元素')
      ElMessage.error('找不到课表内容，请先查看课表')
      return
    }
    
    // 准备打印内容
    const printHTML = `
      <!DOCTYPE html>
      <html>
        <head>
          <meta charset="UTF-8">
          <title>课表打印</title>
          <style>
            * { box-sizing: border-box; }
            body { 
              font-family: 'Microsoft YaHei', 'SimSun', Arial, sans-serif; 
              margin: 8px; 
              background: white;
              font-size: 11px;
              line-height: 1.4;
            }
            .suite-schedule { 
              margin-bottom: 30px; 
              page-break-after: always; 
            }
            .suite-title { 
              text-align: center; 
              font-size: 20px; 
              font-weight: bold; 
              margin-bottom: 15px; 
              color: #000;
            }
            .week-title { 
              font-size: 16px; 
              font-weight: bold; 
              margin: 12px 0; 
              color: #000;
              border-left: 3px solid #000;
              padding-left: 12px;
            }
            .schedule-table { 
              width: 100%; 
              margin: 10px 0; 
              border: 2px solid #000;
              border-radius: 8px;
              overflow: hidden;
            }
            .table-header { 
              display: flex; 
              background: #f8f9fa;
            }
            .header-cell { 
              flex: 1; 
              border-right: 2px solid #000; 
              padding: 8px 4px; 
              text-align: center; 
              font-weight: bold; 
              background: #f8f9fa; 
              font-size: 10px;
              color: #000;
            }
            .header-cell:first-child {
              background: #e3f2fd;
              color: #000;
              flex: 0 0 70px;
            }
            .header-cell:last-child {
              border-right: none;
            }
            .table-body { 
              display: flex;
              flex-direction: column;
            }
            .table-row { 
              display: flex; 
              border-bottom: 2px solid #000;
            }
            .table-row:last-child {
              border-bottom: none;
            }
            .time-cell { 
              flex: 0 0 70px;
              border-right: 2px solid #000; 
              padding: 8px 4px; 
              text-align: center; 
              background: #f3e5f5; 
              font-weight: bold; 
              color: #000;
              font-size: 10px;
            }
            .schedule-cell { 
              flex: 1; 
              border-right: 2px solid #000; 
              padding: 6px; 
              text-align: left; 
              vertical-align: top; 
              min-height: 80px; 
              background: white;
            }
            .schedule-cell:last-child {
              border-right: none;
            }
            .cell-content { 
              min-height: 80px; 
              display: flex;
              flex-direction: row;
              justify-content: space-between;
              align-items: stretch;
              gap: 0;
            }
            .left-section { 
              flex: 3; 
              min-width: 0; 
              display: flex;
              align-items: center;
              justify-content: center;
            }
            .right-section { 
              flex: 1; 
              min-width: 0; 
              text-align: center; 
              display: flex;
              align-items: center;
              justify-content: center;
            }
            .divider {
              width: 2px;
              background: #000;
              margin: 0 8px;
              align-self: stretch;
            }
            .group-ids { 
              margin-bottom: 4px; 
              font-size: 7px; 
              line-height: 1.2;
              color: #000;
              font-weight: normal;
              white-space: pre-line !important;
              word-break: keep-all !important;
              overflow-wrap: break-word;
              max-width: 100%;
            }
            .teacher-names, .experiment-info { 
              margin-bottom: 4px; 
              font-size: 7px; 
              line-height: 1.2;
              color: #000;
              font-weight: normal;
              white-space: pre-line !important;
              word-break: keep-all !important;
              overflow-wrap: break-word;
              max-width: 100%;
            }
            @media print {
              .suite-schedule { page-break-after: always; }
              body { margin: 5px; }
            }
          </style>
        </head>
        <body>
          ${printContent.innerHTML}
        </body>
      </html>
    `
    
    writePrintDocument(printWindow, printHTML)
    
    // 等待内容加载完成后打印
    printWindow.onload = () => {
      setTimeout(() => {
        printWindow.focus()
        printWindow.print()
        // 延迟关闭窗口，确保打印完成
        setTimeout(() => {
          printWindow.close()
        }, 1000)
      }, 500)
    }
  } catch (error) {
    console.error('打印失败:', error)
    ElMessage.error('打印失败: ' + error.message)
  }
}

// 打印教师课表
const printTeacherSchedule = async () => {
  // 立即打开窗口（在异步操作之前），避免浏览器拦截
  const printWindow = window.open('', '_blank', 'width=1600,height=900,resizable=yes,scrollbars=yes')
  if (!printWindow) {
    ElMessage.error('浏览器阻止了弹窗，请允许弹窗后重试')
    return
  }
  
  // 显示加载提示
  printWindow.document.write(`
    <html>
      <head><meta charset="UTF-8"><title>教师课表打印</title></head>
      <body style="display:flex;align-items:center;justify-content:center;height:100vh;font-size:16px;">
        <div>正在加载打印内容，请稍候...</div>
      </body>
    </html>
  `)
  printWindow.document.close()
  
  try {
    printButtonLoading.value = true
    
    // 立即显示加载状态
    ElMessage.info('正在准备打印教师课表...')
    
    // 确保有学期ID
    if (!semesterId.value) {
      printWindow.close()
      ElMessage.warning('请先选择学期')
      return
    }
    
    // 确保有实验套选项，如果没有则加载
    if (suiteOptions.value.length === 0) {
      try {
        const resp = await getAllSuites()
        suiteOptions.value = resp?.data || []
        if (suiteOptions.value.length === 0) {
          printWindow.close()
          ElMessage.warning('请先加载实验套信息')
          return
        }
      } catch (error) {
        console.error('加载实验套选项失败:', error)
        printWindow.close()
        ElMessage.warning('加载实验套信息失败')
        return
      }
    }
    
    // 在打印前，重新加载最新的数据
    ElMessage.info('正在加载最新数据...')
    
    // 使用 getScheduleForDisplay API 加载课表数据（与页面显示一致）
    try {
      const params = {}
      if (semesterId.value) {
        params.semesterId = semesterId.value
      }
      const response = await getScheduleForDisplay(params)
      scheduleList.value = response.data || []
    } catch (error) {
      console.error('加载课表数据失败:', error)
      ElMessage.warning('加载课表数据失败，将使用已有数据')
    }
    
    // 加载教师列表（如果还没有加载）
    if (!teacherList.value || teacherList.value.length === 0) {
      try {
        const teacherResponse = await getTeacherList({ current: 1, size: 100 })
        teacherList.value = teacherResponse.data.records || []
      } catch (error) {
        console.error('加载教师列表失败:', error)
      }
    }
    
    // 加载所有实验套的group_experiment数据
    try {
      const params = {
        semesterId: semesterId.value
      }
      const response = await getAllGroupExperiments(params)
      allGroupExperimentsData.value = response.data || []
    } catch (error) {
      console.error('加载group_experiment数据失败:', error)
      ElMessage.warning('加载实验安排数据失败，将使用已有数据')
    }
    
    // 显示准备打印的消息
    ElMessage.success('正在生成打印内容...')
    
    // 创建教师课表打印内容
    const printContent = generateTeacherScheduleHTML()
    
    // 准备打印内容
    const printHTML = `
      <!DOCTYPE html>
      <html>
        <head>
          <meta charset="UTF-8">
          <title>教师课表打印</title>
          <style>
            @page{size:A4 landscape;margin:10mm}
            *{box-sizing:border-box !important;margin:0 !important;padding:0 !important}
            html,body{width:100% !important;height:100% !important;transform:scale(1) !important;transform-origin:top left !important}
            body{font-family:'Microsoft YaHei','SimSun',Arial,sans-serif !important;margin:8px !important;background:#fff !important;font-size:13px !important;line-height:1.5 !important}
            .suite-schedule{margin-bottom:30px;page-break-after:always}
            .suite-title{text-align:center;font-size:20px;font-weight:bold;margin-bottom:15px;color:#000}
            .schedule-table{width:100%;max-width:277mm;margin:12px auto;border-collapse:collapse;table-layout:fixed;color:#000}
            .schedule-table>thead>tr>th,.schedule-table>tbody>tr>td{border:1px solid #000}
            .col-week{width:36px}
            .col-time{width:60px}
            .schedule-table thead th{background:#f2f2f2;font-weight:bold;font-size:14px;padding:4px;text-align:center}
            .week-label{background:#f2f2f2;font-weight:bold;text-align:center;font-size:14px}
            .time-cell{background:#f2f2f2;font-weight:bold;text-align:center;font-size:15px}
            .day-cell{padding:0;vertical-align:top;height:1px}
            /* 单元格内部小表：小组 | 教师 | 绪；填满整格，竖线借助 border-right + border-collapse 自动贯通整格并对齐 */
            .cell-table{width:100%;height:100%;border-collapse:collapse;table-layout:fixed;font-size:12px}
            .cell-table td{border:none;height:16px;line-height:16px;padding:0 4px;text-align:center;white-space:nowrap;color:#000}
            .cell-table .c-groups{border-right:1px solid #000}
            .cell-table .c-teacher{width:50px;border-right:1px solid #000}
            .cell-table .c-intro{width:16px}
            .legend{margin-top:8px;font-size:12px;color:#000;line-height:1.6}
            .legend-note{margin:0 0 2px}
            .legend-exps{margin:0}
            @media print{
              @page{size:A4 landscape;margin:10mm}
              html,body{width:100% !important;transform:scale(1) !important}
              .suite-schedule{page-break-after:always !important}
              body{margin:8px !important}
              *{print-color-adjust:exact !important;-webkit-print-color-adjust:exact !important}
            }
          </style>
        </head>
        <body>
          <div class="preview" style="background:white;padding:20px;width:100%;box-sizing:border-box">${printContent}</div>
        </body>
      </html>
    `
    
    writePrintDocument(printWindow, printHTML)
    
    printWhenLoaded(printWindow, {
      onReady: () => ElMessage.success('打印窗口已打开，请选择打印选项'),
      onAfterDialogOpen: () => ElMessage.info('打印对话框已打开，完成后请手动关闭打印窗口'),
      onError: (error) => {
        console.error('打印过程出错:', error)
        ElMessage.error('打印过程出错，请重试')
      }
    })
  } catch (error) {
    console.error('打印教师课表失败:', error)
    closePrintWindow(printWindow)
    ElMessage.error('打印教师课表失败: ' + error.message)
  } finally {
    printButtonLoading.value = false
  }
}

// 获取当前学期名称
const getSemesterName = () => {
  const sem = semesterOptions.value.find(s => Number(s.semesterId) === Number(semesterId.value))
  return sem?.semesterName || ''
}

// 生成教师课表HTML内容
const generateTeacherScheduleHTML = () => {
  let html = ''
  
  // 根据选择的实验套筛选要打印的实验套
  const suitesToPrint = selectedPrintSuiteId.value 
    ? suiteOptions.value.filter(suite => (suite.experimentSuiteId || suite.suiteId) === selectedPrintSuiteId.value)
    : suiteOptions.value
  
  if (suitesToPrint.length === 0) {
    return '<div class="no-data">没有找到要打印的实验套</div>'
  }
  
  const semesterName = getSemesterName()
  for (const suite of suitesToPrint) {
    const suiteId = suite.experimentSuiteId || suite.suiteId
    html += `<div class="suite-schedule">`
    html += `<h2 class="suite-title">${semesterName}${suite.suiteName}教师课表</h2>`
    html += generateTeacherFullTableHTML(suiteId)
    html += buildTeacherLegendHTML(suiteId)
    html += `</div>`
  }
  
  return html
}

// 解析小组字符串为扁平数组（兼容换行/空格分隔）
const parseFlatGroups = (groupIds) => groupIds
  ? groupIds.split('\n').filter(line => line.trim()).map(line => line.trim().split(/\s+/).filter(g => g)).flat()
  : []

// 生成单个星期格内部的小表：每行 [两个小组 | 教师 | 绪]
const buildTeacherDayCellHTML = (suiteId, weekType, slot, weekday) => {
  const flatGroups = parseFlatGroups(getGroupIdsForCell(suiteId, weekType, slot, weekday))
  const teacherData = getTeacherNamesForCell(suiteId, weekType, slot, weekday)
  const teacherNames = Array.isArray(teacherData?.teacherNames) ? teacherData.teacherNames : []
  const introFlags = Array.isArray(teacherData?.introCourseFlags) ? teacherData.introCourseFlags : []

  const rowCount = Math.max(Math.ceil(flatGroups.length / 2), teacherNames.length)
  if (rowCount === 0) return ''

  let html = '<table class="cell-table"><tbody>'
  for (let i = 0; i < rowCount; i++) {
    const g1 = flatGroups[i * 2] || ''
    const g2 = flatGroups[i * 2 + 1] || ''
    const groupText = g2 ? `${g1} ${g2}` : g1
    html += '<tr>'
    html += `<td class="c-groups">${groupText}</td>`
    html += `<td class="c-teacher">${teacherNames[i] || ''}</td>`
    html += `<td class="c-intro">${introFlags[i] || ''}</td>`
    html += '</tr>'
  }
  html += '</tbody></table>'
  return html
}

// 生成底部说明：标记说明 + 实验项目清单（按实验ID排序、编号）
const buildTeacherLegendHTML = (suiteId) => {
  const seen = new Set()
  const exps = []
  for (const ge of (allGroupExperimentsData.value || [])) {
    const sid = ge.suiteId ?? ge.experimentSuiteId
    if (String(sid) !== String(suiteId)) continue
    const eid = ge.experimentId
    if (eid == null || seen.has(String(eid))) continue
    seen.add(String(eid))
    exps.push({ experimentId: Number(eid), experimentName: ge.experimentName || '' })
  }
  exps.sort((a, b) => a.experimentId - b.experimentId)

  let html = '<div class="legend">'
  html += '<p class="legend-note">说明："*"号为值班教师，"绪"为绪论课教师</p>'
  if (exps.length > 0) {
    const items = exps.map((e, i) => `${i + 1}-${e.experimentName}`).join('\u3000')
    html += `<p class="legend-exps">${items}</p>`
  }
  html += '</div>'
  return html
}

// 生成某周次类型（单周/双周）的两行（上午/下午）；首行用 rowspan 合并周次标签
const buildTeacherWeekRows = (suiteId, weekType, weekLabel) => {
  const slots = [['morning', '上午'], ['afternoon', '下午']]
  let html = ''
  slots.forEach(([slot, slotLabel], idx) => {
    html += '<tr>'
    if (idx === 0) {
      html += `<td class="week-label" rowspan="2">${weekLabel}</td>`
    }
    html += `<td class="time-cell">${slotLabel}</td>`
    for (const weekday of weekdays) {
      html += `<td class="day-cell">${buildTeacherDayCellHTML(suiteId, weekType, slot, weekday)}</td>`
    }
    html += '</tr>'
  })
  return html
}

// 生成教师课表整表（单/双周合并为一张语义化 <table>）
const generateTeacherFullTableHTML = (suiteId) => {
  let html = '<table class="schedule-table">'
  html += '<colgroup><col class="col-week"><col class="col-time"><col><col><col><col><col></colgroup>'
  html += '<thead><tr>'
  html += '<th class="time-header" colspan="2">时间</th>'
  for (const label of ['星期一', '星期二', '星期三', '星期四', '星期五']) {
    html += `<th>${label}</th>`
  }
  html += '</tr></thead>'
  html += '<tbody>'
  html += buildTeacherWeekRows(suiteId, 0, '单周')
  html += buildTeacherWeekRows(suiteId, 1, '双周')
  html += '</tbody></table>'
  return html
}

// 打印学生课表
const printStudentSchedule = async () => {
  // 立即打开窗口（在异步操作之前），避免浏览器拦截
  const printWindow = window.open('', '_blank', 'width=800,height=600')
  if (!printWindow) {
    ElMessage.error('浏览器阻止了弹窗，请允许弹窗后重试')
    return
  }
  
  // 显示加载提示
  printWindow.document.write(`
    <html>
      <head><meta charset="UTF-8"><title>学生课表打印</title></head>
      <body style="display:flex;align-items:center;justify-content:center;height:100vh;font-size:16px;">
        <div>正在加载打印内容，请稍候...</div>
      </body>
    </html>
  `)
  printWindow.document.close()
  
  try {
    printButtonLoading.value = true
    
    // 立即显示加载状态
    ElMessage.info('正在准备打印学生课表...')
    
    // 确保有学期ID
    if (!semesterId.value) {
      printWindow.close()
      ElMessage.warning('请先选择学期')
      return
    }
    
    // 确保有实验套选项，如果没有则加载
    if (suiteOptions.value.length === 0) {
      try {
        const resp = await getAllSuites()
        suiteOptions.value = resp?.data || []
        if (suiteOptions.value.length === 0) {
          printWindow.close()
          ElMessage.warning('请先加载实验套信息')
          return
        }
      } catch (error) {
        console.error('加载实验套选项失败:', error)
        printWindow.close()
        ElMessage.warning('加载实验套信息失败')
        return
      }
    }
    
    // 在打印前，重新加载最新的数据
    ElMessage.info('正在加载最新数据...')
    
    // 加载学生课表数据
    try {
      if (!semesterId.value) {
        await ensureSemesterId()
      }
      
      if (!semesterId.value) {
        printWindow.close()
        ElMessage.warning('无法获取学期ID')
        return
      }
      
      const resp = await getStudentSchedule(semesterId.value)
      studentScheduleData.value = resp && resp.data ? resp.data : {}
    } catch (error) {
      console.error('加载学生课表数据失败:', error)
      ElMessage.warning('加载学生课表数据失败，将使用已有数据')
    }
    
    // 显示准备打印的消息
    ElMessage.success('正在生成打印内容...')
    
    // 创建学生课表打印内容
    const printContent = generateStudentScheduleHTML()
    
    // 准备打印内容
    const printHTML = `
      <!DOCTYPE html>
      <html>
        <head>
          <meta charset="UTF-8">
          <title>学生课表打印</title>
          <style>
            * { box-sizing: border-box; }
            body { 
              font-family: 'Microsoft YaHei', 'SimSun', Arial, sans-serif; 
              margin: 8px; 
              background: white;
              font-size: 13px;
              line-height: 1.5;
            }
            .suite-schedule { 
              margin-bottom: 24px; 
              page-break-after: always; 
            }
            .suite-title { 
              text-align: center; 
              font-size: 22px; 
              font-weight: bold; 
              margin-bottom: 15px; 
              color: #000;
            }
            .week-title { 
              font-size: 18px; 
              font-weight: bold; 
              margin: 12px 0; 
              color: #000;
              border-left: 3px solid #000;
              padding-left: 12px;
            }
            .time-block-header { 
              background: #e3f2fd; 
              color: #000; 
              font-weight: bold; 
              font-size: 14px; 
              padding: 8px; 
              text-align: center; 
              border: 1px solid #000;
            }
            .schedule-table { 
              width: 100%; 
              margin: 12px 0; 
              border: 1px solid #000;
              border-radius: 6px;
              overflow: hidden;
            }
            .table-header { 
              display: flex; 
              background: #f8f9fa;
            }
            .header-cell { 
              flex: 1; 
              border-right: 1px solid #000; 
              padding: 10px 6px; 
              text-align: center; 
              font-weight: bold; 
              background: #f8f9fa; 
              font-size: 13px;
              color: #000;
            }
            .header-cell:first-child {
              background: #e3f2fd;
              color: #000;
              flex: 0 0 120px;
            }
            .header-cell:nth-child(2) {
              background: #e8f5e8;
              color: #000;
              flex: 0 0 120px;
            }
            .header-cell:last-child {
              border-right: none;
            }
            .table-body { 
              display: flex;
              flex-direction: column;
            }
            .time-block-section { 
              border-bottom: 1px solid #000;
            }
            .time-block-section:last-child { 
              border-bottom: none;
            }
            .table-row { 
              display: flex; 
              border-bottom: 1px solid #000;
            }
            .table-row:last-child {
              border-bottom: none;
            }
            .group-cell { 
              flex: 0 0 120px;
              padding: 10px 6px; 
              text-align: center; 
              background: #e8f5e8; 
              border-right: 1px solid #000; 
              font-weight: bold; 
              color: #000;
              font-size: 13px;
              display: flex;
              align-items: center;
              justify-content: center;
            }
            .schedule-cell { 
              flex: 1; 
              border-right: 1px solid #000; 
              padding: 10px 6px; 
              text-align: center; 
              min-height: 64px; 
              background: white;
              display: flex;
              align-items: center;
              justify-content: center;
            }
            .schedule-cell:last-child {
              border-right: none;
            }
            .cell-content { 
              font-size: 11px;
              color: #000;
              font-weight: 500;
              text-align: center;
              font-family: 'Microsoft YaHei', 'SimSun', monospace;
              line-height: 1.4;
            }
            @media print {
              .suite-schedule { page-break-after: always; }
              body { margin: 8px; }
            }
          </style>
        </head>
        <body>
          ${printContent}
        </body>
      </html>
    `
    
    writePrintDocument(printWindow, printHTML)
    
    printWhenLoaded(printWindow, {
      onReady: () => ElMessage.success('打印窗口已打开，请选择打印选项'),
      onAfterDialogOpen: () => ElMessage.info('打印对话框已打开，完成后请手动关闭打印窗口'),
      onError: (error) => {
        console.error('打印过程出错:', error)
        ElMessage.error('打印过程出错，请重试')
      }
    })
  } catch (error) {
    console.error('打印学生课表失败:', error)
    closePrintWindow(printWindow)
    ElMessage.error('打印学生课表失败: ' + error.message)
  } finally {
    printButtonLoading.value = false
  }
}

// 生成学生课表HTML内容
const generateStudentScheduleHTML = () => {
  // 检查是否有学生课表数据
  if (!studentScheduleData.value || Object.keys(studentScheduleData.value).length === 0) {
    return `
      <div class="suite-schedule">
        <h2 class="suite-title">学生课表</h2>
        <div style="text-align: center; padding: 50px; color: #666;">
          <h3>暂无学生课表数据</h3>
          <p>请先确保已生成学生课表数据</p>
        </div>
      </div>
    `
  }
  
  let html = ''
  
  // 根据选择的实验套筛选要打印的实验套
  const suitesToPrint = selectedPrintSuiteId.value 
    ? suiteOptions.value.filter(suite => (suite.experimentSuiteId || suite.suiteId) === selectedPrintSuiteId.value)
    : suiteOptions.value
  
  if (suitesToPrint.length === 0) {
    return '<div class="no-data">没有找到要打印的实验套</div>'
  }
  
  for (const suite of suitesToPrint) {
    const suiteId = suite.experimentSuiteId || suite.suiteId
    
    html += `<div class="suite-schedule">`
    html += `<h2 class="suite-title">${suite.suiteName}学生课表</h2>`
    
    // 单周课表
    html += `<div class="week-schedule">`
    html += `<h3 class="week-title">单周</h3>`
    html += generateWeekScheduleHTML(suiteId, 0)
    html += `</div>`
    
    // 双周课表
    html += `<div class="week-schedule">`
    html += `<h3 class="week-title">双周</h3>`
    html += generateWeekScheduleHTML(suiteId, 1)
    html += `</div>`
    
    html += `</div>`
  }
  
  return html
}

// 生成周次课表HTML（打印为 table，时间使用 rowspan，一行表头，无分段标题）
const generateWeekScheduleHTML = (suiteId, weekType) => {
  const expNames = getExperimentColumnNames(suiteId)
  const weekdaysOrder = ['周一','周二','周三','周四','周五']
  const slots = ['上午','下午']

  const tableStyle = 'width:100%;border-collapse:collapse;border:2px solid #000;table-layout:fixed;font-size:10px;'
  const thStyle = 'border:1px solid #000;padding:6px 4px;text-align:center;background:#f8f9fa;'
  const tdStyle = 'border:1px solid #000;padding:8px 4px;text-align:center;vertical-align:middle;height:28px;'

  let html = `<table style="${tableStyle}"><thead><tr>`
  html += `<th style="${thStyle};width:80px">时间</th>`
  html += `<th style="${thStyle};width:100px">小组</th>`
  for (const name of expNames) {
    html += `<th style="${thStyle}">${name}</th>`
  }
  html += `</tr></thead><tbody>`

  let anyRow = false
  for (const weekday of weekdaysOrder) {
    for (const slot of slots) {
      const groups = getGroupsForTimeBlock(suiteId, weekType, weekday, slot)
      const timeText = `${weekType === 0 ? '单' : '双'}${weekday}${slot}`
      if (groups.length === 0) {
        // 渲染一个占位行，确保上午/下午都显示
        anyRow = true
        html += '<tr>'
        html += `<td style="${tdStyle}">${timeText}</td>`
        html += `<td style="${tdStyle}">-</td>`
        for (const name of expNames) {
          html += `<td style="${tdStyle}">-</td>`
        }
        html += '</tr>'
        continue
      }
      anyRow = true
      // 渲染每个小组行，首行带时间单元格 rowSpan
      groups.forEach((g, idx) => {
        html += '<tr>'
        if (idx === 0) {
          html += `<td style="${tdStyle}" rowspan="${groups.length}">${timeText}</td>`
        }
        html += `<td style="${tdStyle}">${g.groupName}</td>`
        for (const name of expNames) {
          const wk = name ? getExperimentScheduleForGroup(g.groupName, name, weekType, weekday, slot) : ''
          html += `<td style="${tdStyle}">${wk || '-'}</td>`
        }
        html += '</tr>'
      })
    }
  }

  if (!anyRow) {
    html += `<tr><td style="${tdStyle}" colspan="${12}">该周次暂无学生课表数据</td></tr>`
  }

  html += '</tbody></table>'
  return html
}

// 注意：行渲染已改为 table 行内生成，保留函数名占位已删除

  onMounted(() => {
    if (semesterId.value) {
      exportForm.semesterId = semesterId.value
    }
})




</script>

<style scoped>
.experiment-schedule {
  padding: 0;
  width: 100%;
  min-height: calc(100vh - 80px);
}

.full-width-card {
  width: 100%;
}

:deep(.el-card) {
  width: 100%;
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

.calendar-grid {
  display: grid;
  grid-template-columns: 140px repeat(5, 1fr);
  gap: 16px;
}

.calendar-header {
  font-weight: 600;
  text-align: center;
  padding: 14px 0;
  font-size: 16px;
}

.calendar-slot-label {
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  background: #fafafa;
  border: 1px solid #eee;
  font-size: 16px;
}

.calendar-cell {
  border: 1px solid #e6e8eb;
  padding: 16px;
  min-height: calc((100vh - 220px) / 2);
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}

.cell-inputs {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.cell-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  flex-wrap: wrap;
}
.cell-actions :deep(.el-button) { padding: 10px 16px; font-size: 14px; }
.cell-actions-left,
.cell-actions-right {
  display: flex;
  align-items: center;
}
.cell-actions-left :deep(.el-button),
.cell-actions-right :deep(.el-button) {
  white-space: nowrap;
}

.cell-readonly {
  color: #bbb;
  font-style: italic;
}

.group-results {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 12px;
}

.group-chip {
  border: 1px solid #dcdfe6;
  border-radius: 16px;
  padding: 4px 10px;
  line-height: 20px;
  text-align: center;
  background: #f6f8fb;
  color: #2f3640;
  font-size: 13px;
  font-weight: 500;
}

.schedule-section {
  margin-top: 20px;
  margin-bottom: 30px;
}

.schedule-section h3 {
  margin-bottom: 15px;
  color: #333;
  font-size: 16px;
  font-weight: 600;
}

.pagination-wrapper {
  margin-top: 20px;
  text-align: right;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.cell-columns {
  display: grid;
  grid-template-columns: 1fr 96px;
  gap: 12px;
  align-items: stretch;
}
.cell-columns.single { grid-template-columns: 1fr; }
.cell-columns.single .class-box { display: flex; flex-direction: column; align-items: center; }
.cell-columns.single .class-tags { justify-content: center; }
.cell-columns.single .cell-actions { justify-content: center; }
.cell-columns.single .class-input { max-width: 460px; font-size: 16px; }
.cell-columns.single :deep(.el-button) { padding: 10px 16px; font-size: 14px; }
.class-box {
  min-width: 0;
  height: 100%;
  overflow: hidden;
}
.teacher-box {
  border-left: 1px dashed #e6e8eb;
  padding-left: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow: hidden;
  height: 100%;
}
.teacher-title { /* deprecated in favor of .box-title */
  display: none;
}
.teacher-names {
  color: #606266;
}
/* readonly tag list uses same sizing */
.teacher-tags.readonly {
  display: grid;
  grid-template-columns: 1fr;
  row-gap: 10px;
}
.teacher-empty { color: #667085; font-style: italic; }

/* utility */
.btn-block {
  width: 100%;
}

/* tag input area */
.class-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  max-width: 100%;
}
.class-tags.big :deep(.el-tag) { padding: 8px 12px; font-size: 14px; }
.class-input { min-width: 140px; flex: 1 1 100%; max-width: 100%; }
.class-input.big :deep(.el-input__wrapper) { padding: 10px 14px; }
:deep(.el-tag) { padding: 4px 8px; font-size: 14px; }

/* teacher compact ui */
.teacher-inputs.compact { display: flex; flex-direction: column; gap: 8px; height: 100%; }
.teacher-tags {
  /* single column list to align one teacher per row */
  display: grid;
  grid-template-columns: 1fr;
  row-gap: 10px;
  flex: 1 1 auto;
  align-content: flex-start;
  overflow: auto;
}
/* teacher tag style same as group chip, fill row and center */
.teacher-tags :deep(.el-tag) {
  border: 1px solid #dcdfe6;
  border-radius: 16px;
  padding: 6px 12px;
  line-height: 24px;
  background: #f6f8fb;
  color: #2f3640;
  width: 100%;
  justify-content: center;
  font-size: 14px;
}
.teacher-picker { max-height: 320px; overflow: auto; }
.teacher-checks { display: grid; grid-template-columns: 1fr; row-gap: 6px; }
.picker-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }

/* spacing tweaks */
.cell-inputs { gap: 14px; }
.cell-actions { gap: 14px; }

.box-title { font-weight: 700; margin-bottom: 6px; color: #303133; white-space: nowrap; }
.teacher-label { font-size: 14px; letter-spacing: 1px; }

.calendar-cell { border: 1px solid #e6e8eb; padding: 16px; min-height: calc((100vh - 220px) / 2); background: #fff; border-radius: 8px; }
.group-results { 
  display: grid; 
  grid-template-columns: repeat(2, minmax(0, 1fr)); 
  gap: 6px 8px; 
  max-width: 100%;
  overflow: hidden;
}
.group-chip { 
  border: 1px solid #dcdfe6; 
  border-radius: 16px; 
  padding: 4px 10px; 
  line-height: 20px; 
  text-align: center; 
  background: #f6f8fb; 
  color: #2f3640; 
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
  font-size: 14px;
  font-weight: 500;
}

.cell-columns { display: grid; grid-template-columns: 1fr 96px; gap: 12px; }
.teacher-box { border-left: 1px dashed #e6e8eb; padding-left: 8px; display: flex; flex-direction: column; gap: 6px; overflow: hidden; height: 100%; }

/* make teacher select adapt to narrow column */
:deep(.teacher-box .el-select) { width: 100% !important; }
:deep(.teacher-box .el-select .el-select__wrapper) { padding: 0 6px; }

/* 只读模式样式 */
.group-display {
  background: #f0f9ff;
  border: 1px solid #bae6fd;
  border-radius: 16px;
  padding: 6px 12px;
  margin: 2px 0;
  font-size: 14px;
  color: #0369a1;
  text-align: center;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

.no-data-text {
  color: #667085;
  font-size: 14px;
  text-align: center;
  padding: 8px;
}

.schedule-generation-section {
  margin-top: 20px;
  padding: 10px;
  background-color: #f9f9f9;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
}

.generation-info {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.generation-actions {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.time-slot-display {
  font-weight: 600;
  color: #303133;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
}

.experiment-item {
  padding: 4px 0;
  border-bottom: 1px solid #f0f0f0;
}

.experiment-item:last-child {
  border-bottom: none;
}

/* 课表导出样式 */
.export-buttons {
  display: flex;
  gap: 16px;
  justify-content: center;
  margin-top: 20px;
}

.export-buttons .el-button {
  min-width: 160px;
}

/* 范围选择样式 */
.range-select-start {
  background-color: #e1f3d8 !important;
  border: 2px solid #67c23a !important;
}

.range-select-end {
  background-color: #fdf6ec !important;
  border: 2px solid #e6a23c !important;
}

/* 课表查看样式 */
.schedule-view-container {
  max-height: 70vh;
  overflow-y: auto;
  padding: 20px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.suite-schedule {
  margin-bottom: 40px;
  page-break-after: always;
}

.suite-title {
  text-align: center;
  font-size: 28px;
  font-weight: bold;
  margin-bottom: 25px;
  color: #2c3e50;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.week-schedule {
  margin-bottom: 30px;
}

.week-title {
  font-size: 24px;
  font-weight: bold;
  margin: 20px 0;
  color: #34495e;
  border-left: 5px solid #3498db;
  padding-left: 20px;
  position: relative;
}

.week-title::before {
  content: '';
  position: absolute;
  left: -5px;
  top: 0;
  bottom: 0;
  background: linear-gradient(135deg, #3498db, #2980b9);
  border-radius: 0 3px 3px 0;
}

.time-schedule {
  margin-bottom: 20px;
}

.time-title {
  font-size: 18px;
  font-weight: bold;
  margin: 12px 0;
  color: #7f8c8d;
}

.schedule-table {
  width: 100%;
  border: 2px solid #e9ecef;
  border-radius: 12px;
  overflow: hidden;
  margin: 15px 0;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.table-header {
  display: flex;
  background: linear-gradient(135deg, #f8f9fa, #e9ecef);
}

.header-cell {
  flex: 1;
  padding: 18px 12px;
  text-align: center;
  font-weight: bold;
  border-right: 2px solid #dee2e6;
  color: #495057;
  font-size: 16px;
  position: relative;
}

.header-cell:first-child {
  background: linear-gradient(135deg, #e3f2fd, #bbdefb);
  color: #1976d2;
  font-weight: 700;
  flex: 0 0 120px;
}

.header-cell:nth-child(2) {
  background: linear-gradient(135deg, #e8f5e8, #d4edda);
  color: #155724;
  font-weight: 700;
  flex: 0 0 120px;
}

.header-cell:last-child {
  border-right: none;
}

.table-body {
  display: flex;
  flex-direction: column;
}

.table-row {
  display: flex;
  border-bottom: 1px solid #dee2e6;
}

.table-row:last-child {
  border-bottom: none;
}

.time-cell {
  flex: 1;
  padding: 18px 12px;
  text-align: center;
  background: linear-gradient(135deg, #f3e5f5, #e1bee7);
  border-right: 2px solid #dee2e6;
  font-weight: bold;
  color: #7b1fa2;
  font-size: 16px;
  position: relative;
}

.time-cell::after {
  content: '';
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 60%;
  background: linear-gradient(135deg, #9c27b0, #7b1fa2);
  border-radius: 2px;
}

.schedule-cell {
  flex: 1;
  padding: 16px;
  border-right: 2px solid #dee2e6;
  min-height: 100px;
  display: flex;
  align-items: center;
  background: white;
  position: relative;
}

.schedule-cell:hover {
  background: #f8f9fa;
  transition: background-color 0.3s ease;
}

.schedule-cell:last-child {
  border-right: none;
}

.cell-content {
  width: 100%;
  min-height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: #2c3e50;
  font-weight: 500;
  text-align: center;
}

.left-section {
  flex: 3;
  min-width: 0;
  position: relative;
}

.right-section {
  flex: 1;
  min-width: 0;
  text-align: right;
  position: relative;
}

/* 分割线样式 - 垂直分割线 */
.divider {
  width: 2px;
  background: linear-gradient(180deg, transparent, #bdc3c7, transparent);
  margin: 0 8px;
  position: relative;
  align-self: stretch;
}

.divider::before {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 2px;
  background: linear-gradient(180deg, transparent, #95a5a6, transparent);
}

.group-ids {
  margin-bottom: 12px;
  font-size: 7px;
  color: #2c3e50;
  line-height: 1.6;
  word-break: break-all;
  white-space: pre-line;
  font-weight: 500;
}

.teacher-names,
.experiment-info {
  font-size: 7px;
  color: #e74c3c;
  line-height: 1.6;
  word-break: break-all;
  white-space: pre-line;
  font-weight: 500;
}

.schedule-notes {
  margin-top: 20px;
  padding: 15px;
  background-color: #f0f9ff;
  border: 1px solid #bae6fd;
  border-radius: 4px;
  font-size: 14px;
  color: #0369a1;
}

.schedule-notes p {
  margin: 0;
}

/* 打印课表选择对话框样式 */
.print-schedule-container {
  padding: 20px;
}

.print-loading-section {
  text-align: center;
  padding: 40px 20px;
}

.progress-text {
  margin-top: 15px;
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}

.schedule-type-selector {
  margin-bottom: 30px;
  text-align: center;
}

.teacher-schedule-print,
.student-schedule-print {
  text-align: center;
}

.schedule-print-info {
  margin: 20px 0;
  max-width: 500px;
  margin-left: auto;
  margin-right: auto;
}

.schedule-print-info .el-alert {
  text-align: left;
}

.schedule-print-info p {
  margin: 8px 0;
  line-height: 1.6;
}

.schedule-print-actions {
  margin: 30px 0;
  text-align: center;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 15px;
}

.refresh-btn {
  width: 120px;
}

.print-btn {
  width: 200px;
}

/* 打印按钮加载状态样式 */
.el-button.is-loading {
  position: relative;
  pointer-events: none;
  opacity: 0.8;
}

.el-button.is-loading::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.3);
  border-radius: inherit;
  z-index: 1;
}

/* 教师课表样式 */
.teacher-schedule-actions {
  display: flex;
  gap: 20px;
  justify-content: center;
  margin: 30px 0;
}

.teacher-schedule-info {
  margin: 20px 0;
  max-width: 800px;
  margin-left: auto;
  margin-right: auto;
}

.teacher-schedule-info .el-alert {
  text-align: left;
}

.teacher-schedule-info p {
  margin: 8px 0;
  line-height: 1.6;
}

/* 学生课表样式 */
.student-schedule-actions {
  display: flex;
  gap: 20px;
  justify-content: center;
  margin: 30px 0;
}

.student-schedule-info {
  margin: 20px 0;
  max-width: 800px;
  margin-left: auto;
  margin-right: auto;
}

.student-schedule-info .el-alert {
  text-align: left;
}

.student-schedule-info p {
  margin: 8px 0;
  line-height: 1.6;
}

/* 原有的学生课表表格样式（用于打印） */
.time-block-section {
  border-bottom: 2px solid #dee2e6;
}

.time-block-section:last-child {
  border-bottom: none;
}

.time-block-header {
  background: linear-gradient(135deg, #e3f2fd, #bbdefb);
  color: #1976d2;
  font-weight: bold;
  font-size: 16px;
  padding: 12px;
  text-align: center;
  border-bottom: 1px solid #dee2e6;
}

.group-cell {
  flex: 0 0 120px;
  padding: 16px 12px;
  text-align: center;
  background: linear-gradient(135deg, #e8f5e8, #d4edda);
  border-right: 2px solid #dee2e6;
  font-weight: bold;
  color: #155724;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.schedule-cell {
  flex: 1;
  padding: 16px 12px;
  border-right: 2px solid #dee2e6;
  min-height: 80px;
  display: flex;
  align-items: center;
  background: white;
  position: relative;
}

.schedule-cell:hover {
  background: #f8f9fa;
  transition: background-color 0.3s ease;
}

.schedule-cell:last-child {
  border-right: none;
}

/* 打印样式 */
@media print {
  .schedule-view-container {
    max-height: none;
    overflow: visible;
  }
  
  .suite-schedule {
    page-break-after: always;
  }
  
  .schedule-table {
    page-break-inside: avoid;
  }
}

/* 课表页面字体优化 */
.schedule-management .card-header span {
  font-size: 18px;
  font-weight: 600;
}

.schedule-management .calendar-title {
  font-size: 16px;
  font-weight: 700;
}

.schedule-management .calendar-header {
  font-size: 15px;
  font-weight: 600;
}

.schedule-management .calendar-slot-label {
  font-size: 15px;
  font-weight: 600;
}

.schedule-management .group-chip {
  font-size: 14px;
  font-weight: 500;
  padding: 4px 10px;
  line-height: 20px;
}

/* 绪论课相关样式 */
.intro-notice {
  margin-left: 15px;
}

.intro-label {
  font-size: 14px;
  color: #e6a23c;
  margin-left: 4px;
}

.el-tag.el-tag--warning .intro-label {
  color: #fff;
}

/* 绪论课教师标签特殊样式 */
.teacher-tags .el-tag--warning {
  background: linear-gradient(135deg, #f56c6c, #e6a23c);
  border-color: #e6a23c;
  color: #fff;
}

.teacher-tags .el-tag--warning .el-icon {
  color: #fff;
  margin-right: 4px;
}

.schedule-management .teacher-tag {
  font-size: 14px;
  font-weight: 500;
}

/* 课表表格字体优化 */
.schedule-management .el-table {
  font-size: 16px;
}

.schedule-management .el-table th {
  font-size: 16px;
  font-weight: 600;
}

.schedule-management .el-table td {
  font-size: 16px;
}

/* 时间块标题字体优化 */
.time-block-header {
  font-size: 18px !important;
  font-weight: 700 !important;
}

/* 小组单元格字体优化 */
.group-cell {
  font-size: 16px !important;
  font-weight: 600 !important;
}

/* 课表单元格字体优化 */
.schedule-cell {
  font-size: 15px;
}

/* 对话框标题字体优化 */
.schedule-management .el-dialog__title {
  font-size: 18px;
  font-weight: 600;
}
</style>
