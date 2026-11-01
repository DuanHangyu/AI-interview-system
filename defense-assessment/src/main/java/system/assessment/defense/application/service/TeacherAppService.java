package system.assessment.defense.application.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import system.assessment.defense.application.dto.*;
import system.assessment.defense.infrastructure.repository.dao.po.UserPO;
import system.assessment.defense.infrastructure.repository.dao.service.UserService;

import java.util.List;
import java.util.Objects;

/**
 * @USER taoHouChao
 * @DATE 06:47 2025/8/14
 */
@Service
@RequiredArgsConstructor
public class TeacherAppService {

    private final UserService userService;
    public IPage<TeacherDTO> pageList(TeacherPageQuery query) {
        IPage<UserPO> page = userService.page(new Page<>(query.getPage(), query.getSize()),
                Wrappers.lambdaQuery(UserPO.class)
                        .eq(UserPO::getType, 1)
                        .like(Objects.nonNull(query.getName()), UserPO::getName, query.getName())
                        .like(Objects.nonNull(query.getPhone()), UserPO::getPhone, query.getPhone())
                        .like(Objects.nonNull(query.getAccount()), UserPO::getAccount, query.getAccount()));
        return page.convert(studentPO -> BeanUtil.copyProperties(studentPO, TeacherDTO.class));
    }

    public Boolean createStudent(TeacherCreateCmd createCmd) {
        UserPO userPO = BeanUtil.copyProperties(createCmd, UserPO.class);
        userPO.setType(1);
        return userService.save(userPO);
    }

    public Boolean modifyStudent(TeacherModifyCmd modifyCmd) {
        UserPO userPO = BeanUtil.copyProperties(modifyCmd, UserPO.class);
        userPO.setType(1);
        return userService.updateById(userPO);
    }

    public Boolean deleteStudent(TeacherRemoveCmd removeCmd) {
        return userService.removeById(removeCmd.getId());
    }

    public List<TeacherDTO> allTeacher() {
        int teacherId = StpUtil.getLoginIdAsInt();
        return userService.list(Wrappers.lambdaQuery(UserPO.class)
                .eq(UserPO::getType, 1))
                .stream()
                .filter(item -> item.getId() != teacherId)
                .map(teacherPO -> BeanUtil.copyProperties(teacherPO, TeacherDTO.class))
                .toList();
    }
}
