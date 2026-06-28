package com.physics.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.physics.entity.Semester;
import com.physics.mapper.SemesterMapper;
import com.physics.service.SemesterService;
import org.springframework.stereotype.Service;

@Service
public class SemesterServiceImpl extends ServiceImpl<SemesterMapper, Semester> implements SemesterService {
} 