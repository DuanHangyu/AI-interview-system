package system.assessment.defense.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import system.assessment.defense.application.dto.UploadDTO;
import system.assessment.defense.application.manage.OssManager;

import java.io.IOException;

/**
 * @USER taoHouChao
 * @DATE 07:43 2025/8/13
 */
@RestController
@RequestMapping("/file")
@Tag(name = "文件管理", description = "文件管理")
public class FileController {

    @Resource
    private OssManager ossClientUtils;

    @PostMapping("/upload")
    @Operation(summary = "上传文件")
    public UploadDTO uploadFile(MultipartFile file) {
        String fileUrl = ossClientUtils.uploadFile(file);
        String signedUrl = ossClientUtils.getSignedUrl(fileUrl);
        return new UploadDTO(signedUrl, fileUrl, file.getOriginalFilename());
    }

    @GetMapping("/signed-url")
    @Operation(summary = "获取文件签名URL")
    public String getSignedUrl(@RequestParam("url") String url){
        return ossClientUtils.getSignedUrl(url);
    }

    @GetMapping("/generate-upload-url")
    @Operation(summary = "生成上传URL")
    public UploadDTO generateUploadUrl(@RequestParam("fileName") String fileName,
                                       @RequestParam("contentType") String contentType) {
        String fileUrl = ossClientUtils.getFileName(fileName);
        String signedUrl = ossClientUtils.getSignedPutUrl(fileUrl, contentType);
        return new UploadDTO(signedUrl, fileUrl, fileName);
    }

    @GetMapping("/preview")
    @Operation(summary = "预览文件")
    public void preview(@RequestParam("fileUrl") String fileUrl,
                        HttpServletRequest request,
                        HttpServletResponse response) throws IOException {
        ossClientUtils.preview(fileUrl, request, response);
    }
}
