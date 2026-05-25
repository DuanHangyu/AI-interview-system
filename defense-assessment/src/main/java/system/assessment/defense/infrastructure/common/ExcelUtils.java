package system.assessment.defense.infrastructure.common;

import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import system.assessment.defense.infrastructure.emuns.ErrorCodeEnums;
import system.assessment.defense.infrastructure.exception.BusinessException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @USER taoHouChao
 * @DATE 10:06 2025/8/8
 */
@Slf4j
public class ExcelUtils {

    public static <T> void exportTemplate(List<T> data, String excelName){
        HttpServletResponse response = getResponse();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String fileName = URLEncoder.encode(excelName + ".xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);

        // 创建ExcelWriter
        ExcelWriter writer = ExcelUtil.getWriter(true);
        // 只导出设置了别名的字段
        writer.setOnlyAlias(true);
        try {
            // 写入空数据，只保留表头
            writer.write(data, true);
            writer.flush(response.getOutputStream(), true);
            writer.close();
        } catch (IOException e) {
            log.error("导出模板失败", e);
        }
    }

    public static <T> void exportExcel(List<T> data) throws IOException {
        HttpServletResponse response = getResponse();

        // 设置内容类型
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        // 设置文件名（UTF-8 编码，兼容中文）
        String filename = "学生信息.xlsx";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFilename);

        try (ExcelWriter writer = ExcelUtil.getWriter(true)) {
            writer.setOnlyAlias(true);
            writer.write(data, true);

            ServletOutputStream out = response.getOutputStream();
            writer.flush(out, true);
        }
    }

    public static <T> List<T> readExcel(MultipartFile file, Class<T> tClass){
        try {
            InputStream inputStream = file.getInputStream();
            ExcelReader reader = ExcelUtil.getReader(inputStream);
            return reader.readAll(tClass);
        } catch (IOException e) {
            log.error("读取Excel失败", e);
            throw new BusinessException(ErrorCodeEnums.READ_EXCEL_ERROR);
        }
    }

    private static HttpServletResponse getResponse(){
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new BusinessException(ErrorCodeEnums.GET_RESPONSE_ERROR);
        }
        return attributes.getResponse();
    }
}
