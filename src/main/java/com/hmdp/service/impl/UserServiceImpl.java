package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
//       1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
//            2.不符合返回错误信息
            return Result.fail("手机号错误");
        }
//        3.符合生成验证码
        String code = RandomUtil.randomString(6);
//        4.保存验证码到session
        session.setAttribute("code", code);
//        5.发送验证码
        log.debug("发送短信验证码成功，验证码：{}" + code);
//        返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
//       1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
//            2.不符合返回错误信息
            return Result.fail("手机号错误");
        }
//        2.校验验证码
        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();
//        3.不一致，返回错误。
        if (cacheCode == null || !cacheCode.toString().equals(code)) {
            return Result.fail("校验错误");
        }
//        4.一致，根据手机号查询用户。 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();
//        5.判断用户是否存在
        if (user == null) {
//        6.不存在，创建新用户并保存
            user = createUserWithholdPhone(phone);
        }

        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
//        7.存在
        return Result.ok();
    }

    private User createUserWithholdPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + phone);
        save(user);
        return user;
    }
}
