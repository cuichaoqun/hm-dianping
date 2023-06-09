package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
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

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
          // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
          //      2.不符合返回错误信息
            return Result.fail("手机号错误");
        }
          //  3.符合生成验证码
        String code = RandomUtil.randomString(6);
          //  4.保存验证码到redis中  session
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
          //  5.发送验证码
        log.debug("发送短信验证码成功，验证码：{}" + code);
          //  返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
          // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
          //      2.不符合返回错误信息
            return Result.fail("手机号错误");
        }
          //  2.校验验证码  1.从session中获取， 2.从redis 中获取
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
            //  3.不一致，返回错误。
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("校验错误");
        }
          //  4.一致，根据手机号查询用户。 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();
          //  5.判断用户是否存在
        if (user == null) {
          //  6.不存在，创建新用户并保存
            user = createUserWithholdPhone(phone);
        }
          // 7.保存用户到redis
          // 7.1 随机字符串作为token 登陆令牌
        String token = UUID.randomUUID().toString(true);
          // 7.2 将user对象转化为hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((filedName, filedValue) -> filedValue.toString()));
          // 7.3存储
        String loginUserKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(loginUserKey, userMap);
          //  设置超时时间
        stringRedisTemplate.expire(loginUserKey, LOGIN_CODE_TTL, TimeUnit.MINUTES);
          //  7.存在返回token
        return Result.ok(token);
    }

    private User createUserWithholdPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + phone);
        save(user);
        return user;
    }
}
