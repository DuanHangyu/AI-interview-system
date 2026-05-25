package system.assessment.defense.infrastructure.emuns;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCodeEnums {
    SUCCESS(0, "成功"),
    GET_RESPONSE_ERROR(99, "获取响应失败"),
    READ_EXCEL_ERROR(100, "读取Excel失败"),
    IMPORT_STUDENT_NAME_EMPTY(101, "导入学生姓名不能为空"),
    IMPORT_STUDENT_ACCOUNT_EMPTY(102, "导入学生姓名不能为空"),
    IMPORT_STUDENT_PASSWORD_EMPTY(103, "导入学生姓名不能为空"),
    STUDENT_NOT_EXIST(104, "学生不存在"),
    PASSWORD_ERROR(106, "密码错误"),
    LOGIN_ERROR(105, "登录失败"),
    ASSESSMENT_SETTING_NOT_FOUND(107, "考核设置不存在"),
    USER_NOT_FOUND(108, "用户不存在"),
    USER_ALREADY_EXISTS(109, "用户已存在"),
    USER_PASSWORD_ERROR(110, "用户密码错误"),
    USER_LOGIN_ERROR(111, "用户登录失败"),
    USER_LOGOUT_ERROR(112, "用户登出失败"),
    USER_LOGOUT_SUCCESS(113, "用户登出成功"),
    USER_LOGIN_SUCCESS(114, "用户登录成功"),
    USER_REGISTER_SUCCESS(115, "用户注册成功"),
    USER_REGISTER_ERROR(116, "用户注册失败"),
    PASSWORD_SAME(117, "密码相同"),
    STUDENT_ASSESSMENT_NOT_FOUND(118, "学生考核不存在"),
    STUDENT_ASSESSMENT_FILE_NOT_FOUND(119, "学生考核文件不存在"),
    STUDENT_ASSESSMENT_FILE_PARSE_ERROR(201, "学生考核文件解析错误"),
    STUDENT_ASSESSMENT_FILE_DOWNLOAD_ERROR(120, "学生考核文件下载错误"),
    ASSESSMENT_FILE_DOWNLOAD_ERROR(122, "考核文件下载错误"),
    FILE_DOWNLOAD_ERROR(123, "文件下载错误"),
    STUDENT_ASSESSMENT_QUESTION_COUNT_ERROR(123, "学生考核问题数量错误"),
    VOICE_DATA_NOT_FOUND(124, "语音数据未找到"),
    NO_PERMISSION(125, "无权限"),

    PROJECT_TYPE_NOT_FOUND(127, "项目类型未找到"),
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
