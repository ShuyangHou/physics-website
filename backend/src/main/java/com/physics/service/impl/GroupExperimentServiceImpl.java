package com.physics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.physics.entity.GroupExperiment;
import com.physics.entity.Experiment;
import com.physics.mapper.GroupExperimentMapper;
import com.physics.service.GroupExperimentService;
import com.physics.service.ExperimentService;
import com.physics.service.SemesterService;
import com.physics.service.UserService;
import com.physics.entity.User;
import com.physics.entity.Semester;
import com.physics.dto.TeacherAssignmentRequest;
import com.physics.entity.ExperimentSchedule;
import com.physics.mapper.ExperimentScheduleMapper;
import com.physics.utils.WeekUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GroupExperimentServiceImpl extends ServiceImpl<GroupExperimentMapper, GroupExperiment> implements GroupExperimentService {

    @Autowired
    private ExperimentService experimentService;

    @Autowired
    private UserService userService;

    @Autowired
    private SemesterService semesterService;

    @Autowired
    private ExperimentScheduleMapper experimentScheduleMapper;

    @Override
    public List<GroupExperiment> getByGroupName(String groupName) {
        // 参数验证
        if (groupName == null || groupName.trim().isEmpty()) {
            log.warn("getByGroupName: groupName为空");
            return new ArrayList<>();
        }
        
        try {
            // 查询该小组的所有记录
            QueryWrapper<GroupExperiment> q = new QueryWrapper<>();
            q.eq("group_name", groupName.trim());
            List<GroupExperiment> list = this.list(q);
            if (list == null || list.isEmpty()) {
                return new ArrayList<>();
            }

            // 按周次数字排序（experiment_time: 第X周），处理null值
            list.sort((a, b) -> {
                int weekA = (a != null && a.getExperimentTime() != null) ? extractWeekNumber(a.getExperimentTime()) : 0;
                int weekB = (b != null && b.getExperimentTime() != null) ? extractWeekNumber(b.getExperimentTime()) : 0;
                return Integer.compare(weekA, weekB);
            });

            // 批量填充实验名称
            List<Long> expIds = list.stream()
                    .map(GroupExperiment::getExperimentId)
                    .filter(id -> id != null)
                    .distinct()
                    .collect(Collectors.toList());
            if (!expIds.isEmpty()) {
                List<Experiment> exps = experimentService.listByIds(expIds);
                if (exps != null && !exps.isEmpty()) {
                    Map<Long, String> idToName = exps.stream()
                            .filter(e -> e != null && e.getExperimentId() != null && e.getExperimentName() != null)
                            .collect(Collectors.toMap(Experiment::getExperimentId, Experiment::getExperimentName, (a, b) -> a));
                    Map<Long, String> idToLocation = exps.stream()
                            .filter(e -> e != null && e.getExperimentId() != null)
                            .collect(Collectors.toMap(Experiment::getExperimentId, 
                                    e -> e.getLocation() != null ? e.getLocation() : "", 
                                    (a, b) -> a));
                    for (GroupExperiment ge : list) {
                        if (ge != null && ge.getExperimentId() != null) {
                            ge.setExperimentName(idToName.get(ge.getExperimentId()));
                            ge.setLocation(idToLocation.get(ge.getExperimentId()));
                        }
                    }
                }
            }

            // 批量填充任课教师姓名
            List<Long> teacherIds = list.stream()
                    .map(GroupExperiment::getTeacherId)
                    .filter(id -> id != null)
                    .distinct()
                    .collect(Collectors.toList());
            if (!teacherIds.isEmpty()) {
                List<User> teachers = userService.listByIds(teacherIds);
                if (teachers != null && !teachers.isEmpty()) {
                    Map<Long, String> idToTeacherName = teachers.stream()
                            .filter(t -> t != null && t.getUserId() != null && t.getRealName() != null)
                            .collect(Collectors.toMap(User::getUserId, User::getRealName, (a, b) -> a));
                    for (GroupExperiment ge : list) {
                        if (ge != null && ge.getTeacherId() != null) {
                            ge.setTeacherName(idToTeacherName.get(ge.getTeacherId()));
                        }
                    }
                }
            }
            return list;
        } catch (Exception e) {
            log.error("getByGroupName处理异常, groupName={}", groupName, e);
            throw new RuntimeException("查询小组实验安排失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public boolean batchAssignItems(Long semesterId, Long suiteId, String groupName, String timeSlot, List<Item> items) {
        if (groupName == null || groupName.trim().isEmpty() || timeSlot == null || timeSlot.trim().isEmpty() || items == null || items.isEmpty()) return false;
        String g = groupName.trim();
        String ts = timeSlot.trim();

        // full sync: enabled=false -> delete that week under this timeSlot
        java.util.List<String> toDeleteWeeks = new java.util.ArrayList<>();

        // 预取该小组在本学期/套件下的所有记录，减少数据库往返
        QueryWrapper<GroupExperiment> allQ = new QueryWrapper<>();
        allQ.eq("group_name", g)
              .eq(semesterId != null, "semester_id", semesterId)
              .eq(suiteId != null, "suite_id", suiteId)
              .and(x -> x.eq("time_slot", ts).or().isNull("time_slot").or().eq("time_slot", ""));
        List<GroupExperiment> existsAll = this.list(allQ);
        java.util.Map<String, GroupExperiment> key2ge = existsAll.stream().collect(java.util.stream.Collectors.toMap(
                ge -> compositeKey(ge.getGroupName(), ge.getExperimentTime(), ge.getSemesterId(), ge.getSuiteId()),
                ge -> ge,
                (a, b) -> a
        ));

        java.util.List<GroupExperiment> toInsert = new java.util.ArrayList<>();
        java.util.List<GroupExperiment> toUpdate = new java.util.ArrayList<>();

        // 预取本次涉及的实验地点，避免循环内逐条查库
        Map<Long, String> locationMap = loadLocationMap(items.stream()
                .filter(it -> it != null)
                .map(it -> it.experimentId)
                .collect(Collectors.toList()));

        for (Item it : items) {
            if (it == null) {
                continue;
            }
            String w = normalizeWeek(it.week);
            if (w.isEmpty()) continue;

            boolean enabled = Boolean.TRUE.equals(it.enabled);
            if (!enabled) {
                toDeleteWeeks.add(w);
                continue;
            }

            String key = compositeKey(g, w, semesterId, suiteId);
            GroupExperiment ge = key2ge.get(key);
            if (ge == null) {
                // 不允许插入没有 experiment_id 的记录，避免 DB 约束报错
                if (it.experimentId == null) {
                    // enabled=true 但没有 experimentId：无法插入，跳过
                    continue;
                }
                GroupExperiment ne = new GroupExperiment();
                ne.setGroupName(g);
                ne.setExperimentTime(w);
                ne.setSemesterId(semesterId);
                ne.setSuiteId(suiteId);
                ne.setTimeSlot(ts);
                // 设置实验与位置（必有experimentId）
                ne.setExperimentId(it.experimentId);
                if (locationMap.containsKey(it.experimentId)) {
                    ne.setLocation(locationMap.get(it.experimentId));
                }
                if (it.teacherId != null) {
                    ne.setTeacherId(it.teacherId);
                }
                toInsert.add(ne);
            } else {
                // safety: do not mutate rows that are already bound to other timeSlots
                if (ge.getTimeSlot() != null && !ge.getTimeSlot().trim().isEmpty() && !ts.equals(ge.getTimeSlot().trim())) {
                    continue;
                }
                // legacy compatibility: if existing row has no time_slot, bind it to current timeSlot when user edits
                if (ge.getTimeSlot() == null || ge.getTimeSlot().trim().isEmpty()) {
                    ge.setTimeSlot(ts);
                }
                if (it.experimentId != null) {
                    ge.setExperimentId(it.experimentId);
                    // 设置实验地点
                    if (locationMap.containsKey(it.experimentId)) {
                        ge.setLocation(locationMap.get(it.experimentId));
                    }
                }
                if (it.teacherId != null) {
                    ge.setTeacherId(it.teacherId);
                }
                toUpdate.add(ge);
            }
        }

        // delete disabled weeks for this timeSlot (and legacy empty time_slot)
        boolean okDelete = true;
        if (!toDeleteWeeks.isEmpty()) {
            QueryWrapper<GroupExperiment> dq = new QueryWrapper<>();
            dq.eq("group_name", g)
              .eq(semesterId != null, "semester_id", semesterId)
              .eq(suiteId != null, "suite_id", suiteId)
              .in("experiment_time", toDeleteWeeks)
              .and(x -> x.eq("time_slot", ts).or().isNull("time_slot").or().eq("time_slot", ""));
            // 对于“本来就没有该周次记录”的场景，删除影响行数为0是正常的，应该视为成功。
            // MyBatis-Plus 的 remove(...) 在部分情况下可能返回 false，导致前端误判失败。
            int deleted = this.baseMapper.delete(dq);
            okDelete = deleted >= 0;
        }

        boolean okInsert = toInsert.isEmpty() || this.saveBatch(toInsert);

        // updateBatchById 在部分环境下可能返回 false（例如未开启某些批处理配置），
        // 但实际 SQL 已执行更新。为避免前端误判“批量更新失败”，这里改为逐条 updateById
        // 并统计成功数，保证返回值语义准确。
        boolean okUpdate = true;
        int updated = 0;
        if (!toUpdate.isEmpty()) {
            for (GroupExperiment ge : toUpdate) {
                if (ge == null || ge.getId() == null) {
                    okUpdate = false;
                    continue;
                }
                boolean one = this.updateById(ge);
                if (one) {
                    updated++;
                } else {
                    okUpdate = false;
                    log.warn("batchAssignItems: updateById失败, id={}, groupName={}, week={}, timeSlot={}",
                            ge.getId(), ge.getGroupName(), ge.getExperimentTime(), ts);
                }
            }
        }

        if (!okDelete) {
            log.warn("batchAssignItems: 删除失败, groupName={}, semesterId={}, suiteId={}, timeSlot={}, weeks={}", g, semesterId, suiteId, ts, toDeleteWeeks);
        }
        if (!okInsert) {
            log.warn("batchAssignItems: 插入失败, groupName={}, semesterId={}, suiteId={}, timeSlot={}, insertCount={}", g, semesterId, suiteId, ts, toInsert.size());
        }
        if (!okUpdate) {
            log.warn("batchAssignItems: 更新存在失败, groupName={}, semesterId={}, suiteId={}, timeSlot={}, updateCount={}, successUpdate={}", g, semesterId, suiteId, ts, toUpdate.size(), updated);
        }

        return okDelete && okInsert && okUpdate;
    }

    private int extractWeekNumber(String experimentTime) {
        if (experimentTime == null) return 0;
        try {
            String digits = experimentTime.replaceAll("[^0-9]", "");
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    @Transactional
    public boolean batchCreateGroupExperiments(List<GroupExperiment> groupExperiments) {
        return (groupExperiments != null && !groupExperiments.isEmpty()) && this.saveBatch(groupExperiments);
    }

    @Override
    @Transactional
    public List<GroupExperiment> createGroupExperimentsIfNotExists(String groupName) {
        QueryWrapper<GroupExperiment> q = new QueryWrapper<>();
        q.eq("group_name", groupName);
        return this.list(q);
    }

    @Override
    @Transactional
    public boolean batchAssignExperimentsAndTeachers(Long semesterId, Long suiteId, List<String> groupNames, List<String> weeks, Long experimentId, Long teacherId) {
        if (groupNames == null || groupNames.isEmpty() || weeks == null || weeks.isEmpty()) return false;

        // 规范化
        List<String> normGroups = groupNames.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        List<String> normWeeks = weeks.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .map(this::normalizeWeek)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        if (normGroups.isEmpty() || normWeeks.isEmpty()) return false;

        // 查询已存在的记录
        QueryWrapper<GroupExperiment> q = new QueryWrapper<>();
        q.in("group_name", normGroups)
         .in("experiment_time", normWeeks)
         .eq(semesterId != null, "semester_id", semesterId)
         .eq(suiteId != null, "suite_id", suiteId);
        List<GroupExperiment> exists = this.list(q);

        java.util.Map<String, GroupExperiment> key2ge = exists.stream().collect(java.util.stream.Collectors.toMap(
                ge -> compositeKey(ge.getGroupName(), ge.getExperimentTime(), ge.getSemesterId(), ge.getSuiteId()),
                ge -> ge,
                (a, b) -> a
        ));

        java.util.List<GroupExperiment> toInsert = new java.util.ArrayList<>();
        java.util.List<GroupExperiment> toUpdate = new java.util.ArrayList<>();

        // 实验地点只需查一次（experimentId 全程不变），避免在双重循环里反复查库
        String location = null;
        if (experimentId != null) {
            Experiment exp = experimentService.getById(experimentId);
            if (exp != null) {
                location = exp.getLocation();
            }
        }

        for (String g : normGroups) {
            for (String w : normWeeks) {
                String key = compositeKey(g, w, semesterId, suiteId);
                GroupExperiment ge = key2ge.get(key);
                if (ge == null) {
                    GroupExperiment ne = new GroupExperiment();
                    ne.setGroupName(g);
                    ne.setExperimentTime(w); // “第X周”
                    ne.setSemesterId(semesterId);
                    ne.setSuiteId(suiteId);
                    if (experimentId != null) {
                        ne.setExperimentId(experimentId);
                        if (location != null) ne.setLocation(location);
                    }
                    if (teacherId != null) ne.setTeacherId(teacherId);
                    toInsert.add(ne);
                } else {
                    if (experimentId != null) {
                        ge.setExperimentId(experimentId);
                        if (location != null) ge.setLocation(location);
                    }
                    if (teacherId != null) ge.setTeacherId(teacherId);
                    toUpdate.add(ge);
                }
            }
        }

        boolean okInsert = toInsert.isEmpty() || this.saveBatch(toInsert);
        boolean okUpdate = toUpdate.isEmpty() || this.updateBatchById(toUpdate);
        return okInsert && okUpdate;
    }

    /**
     * 批量预取实验地点（仅保留 location 非空的实验），避免循环内逐条查库。
     */
    private Map<Long, String> loadLocationMap(List<Long> experimentIds) {
        List<Long> ids = experimentIds == null ? new ArrayList<>() : experimentIds.stream()
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return new java.util.HashMap<>();
        }
        return experimentService.listByIds(ids).stream()
                .filter(e -> e != null && e.getExperimentId() != null && e.getLocation() != null)
                .collect(Collectors.toMap(Experiment::getExperimentId, Experiment::getLocation, (a, b) -> a));
    }

    // 组合键：用于内存中判重 (group_name, experiment_time, semester_id, suite_id)
    private String compositeKey(String groupName, String week, Long semesterId, Long suiteId) {
        String g = groupName == null ? "" : groupName.trim();
        String w = week == null ? "" : week.trim();
        String sem = String.valueOf(semesterId);
        String suite = String.valueOf(suiteId);
        return g + "||" + w + "||" + sem + "||" + suite;
    }

    // 统一周次为"第X周"，支持传入纯数字或包含数字的字符串
    private String normalizeWeek(String w) {
        if (w == null) return "";
        String s = w.trim();
        if (s.isEmpty()) return s;
        if (s.matches("^\\d+$")) {
            return "第" + s + "周";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(s);
        return m.find() ? ("第" + m.group(1) + "周") : s;
    }
    
    @Override
    public List<GroupExperiment> getByTimeSlot(String timeSlot) {
        QueryWrapper<GroupExperiment> wrapper = new QueryWrapper<>();
        wrapper.eq("time_slot", timeSlot);
        return this.list(wrapper);
    }

    @Override
    @Transactional
    public boolean bindGroupsToTimeSlot(Long semesterId, Long suiteId, Integer weekType, String timeSlot, List<String> groupNames) {
        if (semesterId == null || suiteId == null || weekType == null || timeSlot == null || timeSlot.trim().isEmpty()) return false;
        if (groupNames == null || groupNames.isEmpty()) return false;
        try {
            Semester semester = semesterService.getById(semesterId);
            if (semester == null || semester.getStartDate() == null || semester.getEndDate() == null) return false;

            List<Integer> weeks = WeekUtils.getTeachingWeeksRuleB(semester.getStartDate(), semester.getEndDate(), weekType);
            if (weeks == null || weeks.isEmpty()) return false;

            List<String> weekTimeStrings = new ArrayList<>();
            for (Integer w : weeks) {
                weekTimeStrings.add("第" + w + "周");
            }

            UpdateWrapper<GroupExperiment> uw = new UpdateWrapper<>();
            uw.eq("semester_id", semesterId)
              .eq("suite_id", suiteId)
              .in("group_name", groupNames)
              .in("experiment_time", weekTimeStrings)
              .set("time_slot", timeSlot);
            return this.update(uw);
        } catch (Exception e) {
            log.error("bindGroupsToTimeSlot失败: semesterId={}, suiteId={}, weekType={}, timeSlot={}, groups={}", semesterId, suiteId, weekType, timeSlot, groupNames, e);
            return false;
        }
    }
    
    @Override
    public boolean updateTeacherAssignment(TeacherAssignmentRequest request) {
        try {
            log.info("开始更新GroupExperiment表教师分配: semesterId={}, suiteId={}, timeSlot={}, weekType={}",
                    request.getSemesterId(), request.getSuiteId(), request.getTimeSlot(), request.getWeekType());

            if (request.getSemesterId() == null || request.getSuiteId() == null) {
                log.warn("semesterId或suiteId为空");
                return false;
            }
            if (request.getWeekType() == null) {
                log.warn("weekType为空");
                return false;
            }
            if (request.getTimeSlot() == null || request.getTimeSlot().trim().isEmpty()) {
                log.warn("timeSlot为空");
                return false;
            }
            if (request.getAssignments() == null || request.getAssignments().isEmpty()) {
                log.warn("教师分配列表为空");
                return false;
            }

            // fullTimeSlot: 单周一上午 / 双周二下午
            String weekTypeText = (request.getWeekType() == 0) ? "单" : "双";
            String fullTimeSlot = weekTypeText + request.getTimeSlot().trim();

            // 读取该时间段已绑定的小组列表（来自 experiment_schedule.group_ids）
            List<String> boundGroups = new ArrayList<>();
            try {
                QueryWrapper<ExperimentSchedule> sw = new QueryWrapper<>();
                sw.eq("semester_id", request.getSemesterId())
                  .eq("suite_id", request.getSuiteId())
                  .eq("week_type", request.getWeekType())
                  .eq("experiment_time", fullTimeSlot);
                ExperimentSchedule schedule = experimentScheduleMapper.selectOne(sw);
                if (schedule != null && schedule.getGroupIds() != null && !schedule.getGroupIds().trim().isEmpty()) {
                    String clean = schedule.getGroupIds().replaceAll("[\\[\\]\"]", "");
                    for (String g : clean.split(",")) {
                        String v = g == null ? "" : g.trim();
                        if (!v.isEmpty()) boundGroups.add(v);
                    }
                }
            } catch (Exception e) {
                log.warn("读取experiment_schedule.group_ids失败，将仅按time_slot过滤: {}", e.getMessage());
            }

            // 绪论课唯一性：同一 timeSlot 下，只允许一个 teacher_id 具备 is_intro_course=1。
            // 若请求中有任意项设置为绪论课，则先清空该 timeSlot 下所有记录的 is_intro_course，再按本次请求设置。
            boolean hasIntroFlag = false;
            if (request.getAssignments() != null) {
                for (TeacherAssignmentRequest.TeacherAssignmentItem item : request.getAssignments()) {
                    if (item != null && item.getIsIntroCourse() != null && item.getIsIntroCourse() == 1) {
                        hasIntroFlag = true;
                        break;
                    }
                }
            }
            if (hasIntroFlag) {
                try {
                    UpdateWrapper<GroupExperiment> clearUw = new UpdateWrapper<>();
                    clearUw.eq("semester_id", request.getSemesterId())
                           .eq("suite_id", request.getSuiteId())
                           .eq("time_slot", fullTimeSlot)
                           .set("is_intro_course", 0);
                    if (!boundGroups.isEmpty()) {
                        clearUw.in("group_name", boundGroups);
                    }
                    this.update(clearUw);

                    // 兼容旧数据：time_slot 为空的同周次记录也可能携带 is_intro_course，需要一起清理
                    if (!boundGroups.isEmpty()) {
                        UpdateWrapper<GroupExperiment> clearLegacy = new UpdateWrapper<>();
                        clearLegacy.eq("semester_id", request.getSemesterId())
                                  .eq("suite_id", request.getSuiteId())
                                  .in("group_name", boundGroups)
                                  .and(x -> x.isNull("time_slot").or().eq("time_slot", ""))
                                  .set("is_intro_course", 0);
                        this.update(clearLegacy);
                    }
                } catch (Exception ex) {
                    log.warn("清理绪论课标记失败，将继续尝试设置目标绪论课: {}", ex.getMessage());
                }
            }

            // 计算该 weekType 的周次列表
            Semester semester = semesterService.getById(request.getSemesterId());
            if (semester == null || semester.getStartDate() == null || semester.getEndDate() == null) {
                log.warn("学期信息不完整，无法动态生成周次: semesterId={}", request.getSemesterId());
                return false;
            }
            List<Integer> weeks = WeekUtils.getTeachingWeeksRuleB(semester.getStartDate(), semester.getEndDate(), request.getWeekType());
            if (weeks == null || weeks.isEmpty()) {
                log.warn("动态生成周次为空: semesterId={}, weekType={}", request.getSemesterId(), request.getWeekType());
                return false;
            }

            // 预先生成本周次集合的 experiment_time 文案，供 IN 一次匹配所有周次
            List<String> weekStrs = new ArrayList<>();
            for (Integer w : weeks) {
                weekStrs.add(String.format("第%d周", w));
            }

            // 对每个实验项更新：用 experiment_time IN (所有周次) 一条 UPDATE 取代“逐周一条”，
            // 大幅减少对远程库的往返。where 必须带 time_slot，且可选用 group_name IN boundGroups。
            for (TeacherAssignmentRequest.TeacherAssignmentItem item : request.getAssignments()) {
                if (item == null || item.getExperimentId() == null || item.getTeacherId() == null) {
                    continue;
                }

                UpdateWrapper<GroupExperiment> uw = new UpdateWrapper<>();
                uw.eq("semester_id", request.getSemesterId())
                  .eq("suite_id", request.getSuiteId())
                  .eq("experiment_id", item.getExperimentId())
                  .in("experiment_time", weekStrs)
                  .eq("time_slot", fullTimeSlot)
                  .set("teacher_id", item.getTeacherId());
                if (item.getIsIntroCourse() != null) {
                    uw.set("is_intro_course", item.getIsIntroCourse());
                }
                if (!boundGroups.isEmpty()) {
                    uw.in("group_name", boundGroups);
                }
                this.update(uw);

                // 兼容旧数据：如果历史记录没有 time_slot，则在限定 group_name 的前提下回填并更新
                if (!boundGroups.isEmpty()) {
                    UpdateWrapper<GroupExperiment> legacy = new UpdateWrapper<>();
                    legacy.eq("semester_id", request.getSemesterId())
                          .eq("suite_id", request.getSuiteId())
                          .eq("experiment_id", item.getExperimentId())
                          .in("experiment_time", weekStrs)
                          .in("group_name", boundGroups)
                          .and(x -> x.isNull("time_slot").or().eq("time_slot", ""))
                          .set("time_slot", fullTimeSlot)
                          .set("teacher_id", item.getTeacherId());
                    if (item.getIsIntroCourse() != null) {
                        legacy.set("is_intro_course", item.getIsIntroCourse());
                    }
                    this.update(legacy);
                }
            }

            log.info("GroupExperiment表教师分配更新完成，共处理 {} 个分配", request.getAssignments().size());
            return true;

        } catch (Exception e) {
            log.error("更新GroupExperiment表教师分配失败", e);
            return false;
        }
    }
    
}
