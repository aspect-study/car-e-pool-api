package com.carpool.service.donate;

import com.carpool.domain.entity.DonateClick;
import com.carpool.repository.DonateClickRepository;
import com.carpool.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tracks donate-button taps (curiosity/intent signal) — never the actual
 * transfer, which happens manually outside the app and can't be tracked.
 */
@Service
@RequiredArgsConstructor
public class DonateService {

    private final DonateClickRepository donateClickRepository;
    private final UserRepository        userRepository;

    @Transactional
    @CacheEvict(cacheNames = "adminStats", key = "'global'")
    public void recordClick(Long userId, String channel) {
        donateClickRepository.save(DonateClick.builder()
                .user(userRepository.getReferenceById(userId))
                .channel(channel)
                .build());
    }
}
