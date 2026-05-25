package system.assessment.defense.application.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import system.assessment.defense.application.dto.ChangePasswordDTO;
import system.assessment.defense.application.dto.LoginDTO;
import system.assessment.defense.application.dto.UserDetailDTO;
import system.assessment.defense.infrastructure.emuns.ErrorCodeEnums;
import system.assessment.defense.infrastructure.emuns.UserRoleEnums;
import system.assessment.defense.infrastructure.exception.BusinessException;
import system.assessment.defense.infrastructure.repository.dao.po.UserPO;
import system.assessment.defense.infrastructure.repository.dao.service.UserService;

import java.util.Objects;
import java.util.Optional;

/**
 * @USER taoHouChao
 * @DATE 17:21 2025/8/12
 */
@Service
@RequiredArgsConstructor
public class LoginService {

    private final UserService userService;

    public String login(LoginDTO loginDTO) {
        Optional<UserPO> userOp = userService.findByAccount(loginDTO.getAccount());
        UserPO userPO = userOp.orElseThrow(() -> new BusinessException(ErrorCodeEnums.USER_NOT_FOUND));
        if (!Objects.equals(userPO.getPassword(), loginDTO.getPassword())) {
            throw new BusinessException(ErrorCodeEnums.PASSWORD_ERROR);
        }
        StpUtil.login(userPO.getId());
        return StpUtil.getTokenInfo().getTokenValue();
    }

    public UserDetailDTO userDetail(int userId) {
        Optional<UserPO> userOp = userService.findById(userId);
        UserPO userPO = userOp.orElseThrow(() -> new BusinessException(ErrorCodeEnums.USER_NOT_FOUND));
        return UserDetailDTO.builder()
                .id(userPO.getId())
                .name(userPO.getName())
                .account(userPO.getAccount())
                .role(UserRoleEnums.getByRole(userPO.getType()).name())
                .build();
    }

    public Boolean changePassword(int userId, ChangePasswordDTO changePassword) {
        Optional<UserPO> userOp = userService.findById(userId);
        UserPO userPo = userOp.orElseThrow(() -> new BusinessException(ErrorCodeEnums.USER_NOT_FOUND));
        if (Objects.equals(changePassword.getPassword(), userPo.getPassword())) {
            throw new BusinessException(ErrorCodeEnums.PASSWORD_SAME);
        }
        return userService.update(Wrappers.lambdaUpdate(UserPO.class)
                .set(UserPO::getPassword, changePassword.getPassword())
                .eq(UserPO::getId, userId));
    }
}
