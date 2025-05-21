package com.ody.route.service;

import com.ody.route.domain.RouteClientKey;
import com.ody.route.repository.RouteClientRedisTemplate;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RouteClientCircuitBreaker {

    private static final int MAX_FAIL_COUNT = 3;
    private static final String BLOCK = "1";
    public static final Duration FAIL_MINUTES_TTL = Duration.ofMinutes(30);
    public static final Duration BLOCK_HOUR_TTL = Duration.ofHours(3);

    private final RouteClientRedisTemplate redisTemplate;

    public void recordFailCountInMinutes(RouteClient routeClient) {
        String failClientKey = RouteClientKey.getFailKey(routeClient);
        int failCount = redisTemplate.increment(failClientKey);
        if (Boolean.FALSE.equals(redisTemplate.hasKey(failClientKey))) {
            redisTemplate.expire(failClientKey, FAIL_MINUTES_TTL);
            log.warn("{} 첫 요청 실패, {}분 TTL 시작", failClientKey, FAIL_MINUTES_TTL);
        }
        log.warn("{} 요청 실패 횟수 : {}", failClientKey, failCount);
    }

    public void determineBlock(RouteClient routeClient) {
        String failClientKey = RouteClientKey.getFailKey(routeClient);
        String blockKey = RouteClientKey.getBlockKey(routeClient);
        if (exceedFailCount(failClientKey)) {
            block(blockKey);
            clearFailCount(failClientKey);
        }
    }

    private boolean exceedFailCount(String failCountKey) {
        return redisTemplate.getKeyCount(failCountKey) >= MAX_FAIL_COUNT;
    }

    private void block(String blockKey) {
        redisTemplate.opsForValue().set(blockKey, BLOCK);
        redisTemplate.expire(blockKey, BLOCK_HOUR_TTL);
        log.warn("{}가 차단되었습니다. 해제 예정 시간 : {}", blockKey, LocalDateTime.now().plus(BLOCK_HOUR_TTL));
    }

    private void clearFailCount(String failCountKey) {
        redisTemplate.unlink(failCountKey);
    }

    public boolean isBlocked(RouteClient routeClient) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(RouteClientKey.getBlockKey(routeClient)));
    }
}
