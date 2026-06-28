package com.physics.controller;

import com.physics.common.Result;
import com.physics.dto.LoginRequest;
import com.physics.dto.LoginResponse;
import com.physics.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/auth")
public class AuthController {
    
    @Autowired
    private UserService userService;
    
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        try {
            LoginResponse loginResponse = userService.login(request);
            
            // 设置cookie
            Cookie tokenCookie = new Cookie("token", loginResponse.getToken());
            tokenCookie.setPath("/");
            tokenCookie.setMaxAge(24 * 60 * 60); // 24小时
            response.addCookie(tokenCookie);

            // 新增：设置 userId cookie
            Cookie userIdCookie = new Cookie("userId", String.valueOf(loginResponse.getUserId()));
            userIdCookie.setPath("/");
            userIdCookie.setMaxAge(24 * 60 * 60); // 24小时
            response.addCookie(userIdCookie);
            
            return Result.success(loginResponse);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/logout")
    public Result<String> logout(@CookieValue("userId") Long userId, HttpServletResponse response) {
        try {
            // 从Redis中删除token
            userService.logout(userId);
            
            // 清除cookie
            Cookie tokenCookie = new Cookie("token", "");
            tokenCookie.setPath("/");
            tokenCookie.setMaxAge(0);
            response.addCookie(tokenCookie);
            
            Cookie userIdCookie = new Cookie("userId", "");
            userIdCookie.setPath("/");
            userIdCookie.setMaxAge(0);
            response.addCookie(userIdCookie);
            
            return Result.success("登出成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/userInfo")
    public Result<Object> getUserInfo() {
        try {
            return Result.success(userService.getCurrentUser());
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/changePassword")
    public Result<Boolean> changePassword(@CookieValue("userId") Long userId, @RequestBody java.util.Map<String, String> body) {
        try {
            String oldPassword = body.get("oldPassword");
            String newPassword = body.get("newPassword");
            if (oldPassword == null || newPassword == null || oldPassword.isEmpty() || newPassword.isEmpty()) {
                return Result.error("原密码和新密码不能为空");
            }
            boolean result = userService.changePassword(userId, oldPassword, newPassword);
            return result ? Result.success(true) : Result.error("修改密码失败，请检查原密码是否正确");
        } catch (Exception e) {
            return Result.error("修改密码失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/checkPasswordStatus")
    public Result<Boolean> checkPasswordStatus(@CookieValue("userId") Long userId) {
        try {
            boolean isDefaultPassword = userService.isDefaultPassword(userId);
            return Result.success(isDefaultPassword);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
} 