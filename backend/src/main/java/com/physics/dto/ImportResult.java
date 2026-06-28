package com.physics.dto;

import lombok.Data;

import java.util.List;

@Data
public class ImportResult {
    
    private int totalCount;           // 总记录数
    private int successCount;         // 成功数量
    private int failCount;            // 失败数量
    private List<String> errorMessages; // 错误信息列表
    private String message;           // 总体消息
    
    public ImportResult() {
        this.errorMessages = new java.util.ArrayList<>();
    }
    
    public void addErrorMessage(String errorMessage) {
        this.errorMessages.add(errorMessage);
    }
    
    public boolean isSuccess() {
        return failCount == 0;
    }
}
