package com.physics.utils;

import com.physics.dto.StudentImportDTO;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ExcelUtils {
    
    /**
     * 解析学生导入Excel文件
     * @param file Excel文件
     * @return 学生数据列表
     * @throws IOException 文件读取异常
     */
    public static List<StudentImportDTO> parseStudentExcel(MultipartFile file) throws IOException {
        List<StudentImportDTO> students = new ArrayList<>();
        
        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            
            int totalRows = sheet.getLastRowNum();
            int skippedRows = 0;
            int validRows = 0;
            

            
            // 从第二行开始读取数据（第一行是标题）
            for (int i = 1; i <= totalRows; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    skippedRows++;
                    continue;
                }
                
                // 检查行是否为空（所有关键列都没有数据）
                if (isRowEmpty(row)) {
                    skippedRows++;
                    continue; // 跳过空行
                }
                
                StudentImportDTO student = new StudentImportDTO();
                
                // 读取学号（A列）
                Cell cell0 = row.getCell(0);
                if (cell0 != null) {
                    student.setSchoolId(getCellValueAsString(cell0));
                }
                
                // 读取姓名（B列）
                Cell cell1 = row.getCell(1);
                if (cell1 != null) {
                    student.setRealName(getCellValueAsString(cell1));
                }
                
                // 读取班号（C列）
                Cell cell2 = row.getCell(2);
                if (cell2 != null) {
                    student.setClassId(getCellValueAsString(cell2));
                }
                
                // 只有所有关键字段都有数据时才添加到列表
                if (isValidStudentData(student)) {
                    students.add(student);
                    validRows++;
                } else {
                    skippedRows++;
                }
            }
            

        }
        
        return students;
    }
    
    /**
     * 获取单元格的值并转换为字符串
     */
    private static String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // 避免科学计数法，转为整数
                    double value = cell.getNumericCellValue();
                    if (value == (long) value) {
                        return String.valueOf((long) value);
                    } else {
                        return String.valueOf(value);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }
    
    /**
     * 检查行是否为空（所有关键列都没有数据）
     */
    private static boolean isRowEmpty(Row row) {
        if (row == null) return true;
        
        // 检查A、B、C三列是否都为空
        Cell cell0 = row.getCell(0); // 学号列
        Cell cell1 = row.getCell(1); // 姓名列
        Cell cell2 = row.getCell(2); // 班号列
        
        String schoolId = getCellValueAsString(cell0);
        String realName = getCellValueAsString(cell1);
        String classId = getCellValueAsString(cell2);
        
        // 如果三列都为空或只包含空白字符，则认为行是空的
        return (schoolId == null || schoolId.trim().isEmpty()) &&
               (realName == null || realName.trim().isEmpty()) &&
               (classId == null || classId.trim().isEmpty());
    }
    
    /**
     * 验证学生数据是否有效（所有关键字段都有数据）
     */
    private static boolean isValidStudentData(StudentImportDTO student) {
        if (student == null) return false;
        
        String schoolId = student.getSchoolId();
        String realName = student.getRealName();
        String classId = student.getClassId();
        
        // 检查所有关键字段都不为空且不包含纯空白字符
        return schoolId != null && !schoolId.trim().isEmpty() &&
               realName != null && !realName.trim().isEmpty() &&
               classId != null && !classId.trim().isEmpty();
    }
}
