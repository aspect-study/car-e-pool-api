package com.carpool.service.user;

import com.carpool.common.exception.UserNotFoundException;
import com.carpool.domain.entity.User;
import com.carpool.domain.enums.UserStatus;
import com.carpool.repository.UserRepository;
import com.carpool.service.dto.request.UpdateRoleRequest;
import com.carpool.service.dto.response.UserResponse;
import com.carpool.service.mapper.EntityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final EntityMapper   mapper;

    @Cacheable(value = "users", key = "#userId")
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        return userRepository.findById(userId)
                .map(mapper::toUserResponse)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    /**
     * Driver role upgrade — evicts user cache so the new role
     * is reflected on the next request without stale data.
     */
    @CacheEvict(value = "users", key = "#userId")
    @Transactional
    public UserResponse updateRole(Long userId, UpdateRoleRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        log.info("User role updated: userId={} {} → {}", userId, user.getRole(), request.role());
        user.setRole(request.role());
        return mapper.toUserResponse(userRepository.save(user));
    }

    /**
     * Admin: suspend or ban a user.
     */
    @CacheEvict(value = "users", key = "#userId")
    @Transactional
    public UserResponse updateStatus(Long userId, UserStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        log.warn("User status changed: userId={} {} → {}", userId, user.getStatus(), status);
        user.setStatus(status);
        return mapper.toUserResponse(userRepository.save(user));
    }
}
