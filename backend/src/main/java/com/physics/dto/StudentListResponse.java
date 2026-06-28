package com.physics.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.physics.entity.User;
import lombok.Data;

import java.util.List;

@Data
public class StudentListResponse {
    
    private Page<User> studentList;
    private List<String> classList;
    private List<String> groupList;
    
    public StudentListResponse(Page<User> studentList, List<String> classList, List<String> groupList) {
        this.studentList = studentList;
        this.classList = classList;
        this.groupList = groupList;
    }
}
