package com.vidoc.service;

import cn.dev33.satoken.stp.StpUtil;
import com.vidoc.dto.AuthDtos;
import com.vidoc.entity.User;
import com.vidoc.exception.ApiException;
import com.vidoc.repository.UserRepository;
import com.vidoc.security.PasswordHasher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final PointsService pointsService;

    public UserService(UserRepository userRepository, PasswordHasher passwordHasher, PointsService pointsService) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.pointsService = pointsService;
    }

    /**
     * 登录/注册
     */
    public AuthDtos.AuthResponse loginOrRegister(AuthDtos.LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            user = createUser(email, request.password());
        }
        if (!passwordHasher.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "邮箱或密码不正确");
        }
        return toAuthResponse(user);
    }

    /** 并发提交同一新邮箱时唯一索引会先拦下一个，按「已注册」继续走登录校验 */
    private User createUser(String email, String rawPassword) {
        User created;
        try {
            created = userRepository.save(new User(email, passwordHasher.encode(rawPassword)));
        } catch (DataIntegrityViolationException e) {
            return userRepository.findByEmail(email)
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "邮箱或密码不正确"));
        }
        // 开户即建积分行
        pointsService.provision(created.getId());
        return created;
    }

    public AuthDtos.UserResponse currentUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "登录状态已失效，请重新登录"));
        return toUserResponse(user);
    }

    private AuthDtos.AuthResponse toAuthResponse(User user) {
        StpUtil.login(user.getId());
        return new AuthDtos.AuthResponse(StpUtil.getTokenValue(), toUserResponse(user));
    }

    private AuthDtos.UserResponse toUserResponse(User user) {
        return new AuthDtos.UserResponse(user.getId(), user.getEmail(), user.getCreatedAt());
    }
}
