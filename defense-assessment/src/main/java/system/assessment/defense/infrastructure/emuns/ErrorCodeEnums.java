package system.assessment.defense.infrastructure.emuns;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCodeEnums {
    GET_RESPONSE_ERROR(99, "获取响应失败"),
    READ_EXCEL_ERROR(100, "读取Excel失败"),
    IMPORT_STUDENT_NAME_EMPTY(101, "导入学生姓名不能为空"),
    IMPORT_STUDENT_PASSWORD_EMPTY(103, "导入学生姓名不能为空"),
    PASSWORD_ERROR(106, "密码错误"),
    ASSESSMENT_SETTING_NOT_FOUND(107, "考核设置不存在"),
    USER_NOT_FOUND(108, "用户不存在"),
    PASSWORD_SAME(117, "密码相同"),
    STUDENT_ASSESSMENT_NOT_FOUND(118, "学生考核不存在"),
    ANALYSIS_CHECK_FAIL(128, "考核失败"),
    IMPORT_STUDENT_EXIST(129, "导入学生数据已存在"),
    EXIST_STUDENT(130, "已存在学生"),
    RECORD_NOT_FOUND(131, "记录未找到"),
    ASSESSMENT_SETTING_NOT_CAN_APPOINTMENT(132, "当前无法预约"),
    ASSESSMENT_SETTING_APPOINTMENT_LIMIT_EXCEED(133, "预约人数已满"),
    ASSESSMENT_APPOINTMENT_SETTING_NOT_FOUND(134, "考核预约设置未找到"),
    ASSESSMENT_SETTING_APPOINTMENT_EXIST(135, "考核预约设置已存在"),
    UNABLE_APPOINT(137, "当前无法预约"),
    ;

    private final int code;

    private final String message;
}
