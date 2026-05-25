package system.assessment.defense.interfaces.rest;

import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import system.assessment.defense.application.dto.ChangePasswordDTO;
import system.assessment.defense.application.dto.LoginDTO;
import system.assessment.defense.application.dto.UserDetailDTO;
import system.assessment.defense.application.service.LoginService;

/**
 * @USER taoHouChao
 * @DATE 14:16 2025/8/12
 */
@RestController
@RequestMapping
@Tag(name = "登录管理", description = "登录管理")
public class LoginController {

    @Resource
    private LoginService loginService;

    @PostMapping("/login")
    @Operation(summary = "登录")
    public String login(@RequestBody LoginDTO loginDTO){
        return loginService.login(loginDTO);
    }

    @GetMapping("/user-detail")
    @Operation(summary = "获取用户详情")
    public UserDetailDTO userDetail(){
        int userId = StpUtil.getLoginIdAsInt();
        return loginService.userDetail(userId);
    }

    @PostMapping("/change-password")
    @Operation(summary = "修改密码")
    public Boolean changePassword(@RequestBody ChangePasswordDTO changePasswordDTO){
        int userId = StpUtil.getLoginIdAsInt();
        return loginService.changePassword(userId, changePasswordDTO);
    }
}
