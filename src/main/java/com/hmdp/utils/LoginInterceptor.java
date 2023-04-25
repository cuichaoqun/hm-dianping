package com.hmdp.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {


    //    前置拦截器
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//判断是否需要进行拦截
        if (UserHolder.getUser() == null) {
            response.setStatus(401);
            return false;
        }
//       有用户 放行
        return true;
    }

}
