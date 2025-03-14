package com.ody.common.redis;

import com.ody.common.aop.LeaderOnly;
import com.ody.common.exception.OdyServerErrorException;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LeaderManager {

    private final RedissonClient redissonClient;
    private final String serverInstanceId = UUID.randomUUID().toString();

    public <T> T executeIfLeader(Supplier<T> supplier, LeaderOnly leaderOnly) {
        if (!isLeader(leaderOnly)) {
            log.debug("서버 인스턴스 {}는 리더가 아니므로 작업을 실행하지 않습니다.", serverInstanceId);
            return null;
        }
        return supplier.get();
    }

    public boolean isLeader(LeaderOnly leaderOnly) {
        RLock lock = redissonClient.getLock(leaderOnly.key());
        if (lock.isHeldByCurrentThread()) {
            log.debug("현재 인스턴스가 락 보유 중: {}", leaderOnly.key());
            return true;
        }
        if (!lock.isHeldByCurrentThread() && lock.isLocked()) {
            log.debug("다른 인스턴스가 락 보유 중: {}", leaderOnly.key());
            return false;
        }
        return tryUpdateLeader(lock, leaderOnly);
    }

    private boolean tryUpdateLeader(RLock lock, LeaderOnly leaderOnly) {
        try {
            boolean acquired = lock.tryLock(leaderOnly.waitTime(), leaderOnly.leaseTime(), leaderOnly.timeUnit());
            if (acquired) {
                log.debug("서버 인스턴스 {}가 리더로 선출되었습니다.", serverInstanceId);
                return true;
            }
        } catch (Exception exception) {
            log.error("리더 선출 과정에서 오류 발생", exception);
            throw new OdyServerErrorException("서버에 장애가 발생했습니다.");
        }
        return false;
    }

    public void releaseLeadership(String lockName) {
        RLock lock = redissonClient.getLock(lockName);
        if (lock.isHeldByCurrentThread()) {
            try {
                lock.unlock();
                log.debug("서버 인스턴스 {} 리더 역할을 해제했습니다.", serverInstanceId);
            } catch (Exception exception) {
                log.error("리더십 해제 중 오류 발생", exception);
            }
        }
    }
}
