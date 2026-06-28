package com.physics.interceptor;

import com.physics.common.AuthTokenStore;
import com.physics.common.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    
    @Autowired
    private AuthTokenStore authTokenStore;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        
        // 跳过OPTIONS预检请求
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }
        
        // 跳过登录接口
        if (request.getRequestURI().equals("/api/auth/login")) {
            return true;
        }
        
        // 从cookie中获取userId和token
        Cookie[] cookies = request.getCookies();
        
        String userId = null;
        String token = null;
        
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("userId".equals(cookie.getName())) {
                    userId = cookie.getValue();
                } else if ("token".equals(cookie.getName())) {
                    token = cookie.getValue();
                }
            }
        }
        
        // 验证userId和token
        if (userId != null && token != null) {
            try {
                Long userIdLong = Long.parseLong(userId);
                if (authTokenStore.validate(userIdLong, token)) {
                    // token验证成功
                    UserContext.setCurrentUserId(userIdLong);
                    UserContext.setToken(token);
                    return true;
                }
            } catch (NumberFormatException e) {
                // 用户ID解析失败
            } catch (Exception e) {
                // Redis验证异常
            }
        }
        response.setStatus(401);
        return false;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
} 
