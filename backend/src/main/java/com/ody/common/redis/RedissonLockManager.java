package com.ody.common.redis;

import com.ody.common.aop.DistributedLock;
import com.ody.common.exception.OdyServerErrorException;
import com.ody.common.transaction.TransactionCallbackTemplate;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonLockManager {

    private final RedissonClient redissonClient;
    private final TransactionCallbackTemplate transactionCallbackTemplate;

    public <T> T lock(Supplier<T> supplier, String lockName, DistributedLock distributedLock) {
        RLock rLock = redissonClient.getLock(lockName); // lockName으로 분산 락 객체를 생성
        acquireLock(rLock, lockName, distributedLock);
        return transactionCallbackTemplate.executeWithAfterCommitAction(
                supplier,
                () -> releaseLock(rLock, lockName)
        );
    }

    private void acquireLock(RLock rLock, String lockName, DistributedLock distributedLock) {
        try {
            log.debug("[분산락 시작] {} 획득 시도", lockName);
            boolean acquired = rLock.tryLock(distributedLock.waitTime(), distributedLock.leaseTime(),
                    distributedLock.timeUnit());
            if (!acquired) {
                log.warn("[분산락 획득 실패] {} {}초 대기 후 락 획득 실패", lockName, distributedLock.waitTime());
                throw new OdyServerErrorException("다른 요청을 처리 중 입니다. 잠시 후 다시 시도해주세요.");
            }
            log.debug("[분산락 획득 성공] {} (유효시간: {}초)", lockName, distributedLock.leaseTime());
        } catch (Exception exception) {
            log.error("분산락 {} 획득 중 오류 발생", lockName, exception);
            throw new OdyServerErrorException("서버에 장애가 발생했습니다.");
        }
    }

    private void releaseLock(RLock rLock, String lockName) {
        if (rLock.isHeldByCurrentThread()) {
            rLock.unlock();
            log.debug("[분산락 해제] {}", lockName);
        }
    }
}
