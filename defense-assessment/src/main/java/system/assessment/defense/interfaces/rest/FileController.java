package system.assessment.defense.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import system.assessment.defense.application.dto.UploadDTO;
import system.assessment.defense.infrastructure.exception.BusinessException;
import system.assessment.defense.infrastructure.storage.FileStorage;
import system.assessment.defense.infrastructure.storage.LocalFileStorage;

import java.io.IOException;

@RestController
@RequestMapping("/file")
@Tag(name = "文件管理", description = "文件管理")
@RequiredArgsConstructor
public class FileController {

    private final FileStorage fileStorage;

    @PostMapping("/upload")
    @Operation(summary = "上传文件")
    public UploadDTO uploadFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "文件为空");
        }
        String key = fileStorage.put("uploads", file.getOriginalFilename(), file.getBytes(), file.getContentType());
        return new UploadDTO(fileStorage.signedUrl(key), key, file.getOriginalFilename());
    }

    @GetMapping("/signed-url")
    @Operation(summary = "获取文件签名URL")
    public String getSignedUrl(@RequestParam("url") String url) {
        return fileStorage.signedUrl(url);
    }

    @GetMapping("/preview")
    @Operation(summary = "预览文件")
    public void preview(@RequestParam("fileUrl") String fileUrl,
                        HttpServletRequest request,
                        HttpServletResponse response) throws IOException {
        fileStorage.serve(fileUrl, request, response);
    }

    /**
     * 本地存储的签名直链（无需登录，靠签名和过期时间鉴权），供 audio / iframe 直接加载
     */
    @GetMapping("/raw")
    @Operation(summary = "本地存储文件直链")
    public void raw(@RequestParam("key") String key, @RequestParam("expires") long expires,
                    @RequestParam("sig") String sig,
                    HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!(fileStorage instanceof LocalFileStorage local) || !local.verify(key, expires, sig)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        fileStorage.serve(key, request, response);
    }
}
