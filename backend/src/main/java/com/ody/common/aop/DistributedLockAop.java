package com.ody.common.aop;

import com.ody.common.exception.OdyException;
import com.ody.common.exception.OdyServerErrorException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedLockAop {

    private static final String REDISSON_LOCK_PREFIX = "LOCK:";

    private final RedissonClient redissonClient;
    private final TransactionHandlerForAop transactionHandlerForAop;

    @Around("@annotation(distributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) {
        String lockName = REDISSON_LOCK_PREFIX + getDynamicValue(joinPoint, distributedLock.key());
        RLock rLock = redissonClient.getLock(lockName);
        return acquireLock(rLock, joinPoint, distributedLock);
    }

    private Object acquireLock(RLock rLock, ProceedingJoinPoint joinPoint, DistributedLock distributedLock) {
        String lockName = rLock.getName();
        try {
            log.debug("[분산락 시작] {} 획득 시도", lockName);
            boolean acquired = rLock.tryLock(distributedLock.waitTime(), distributedLock.leaseTime(),
                    distributedLock.timeUnit());
            if (!acquired) {
                log.warn("[분산락 획득 실패] {} {}초 대기 후 락 획득 실패", lockName, distributedLock.waitTime());
                throw new OdyServerErrorException("다른 요청을 처리 중 입니다. 잠시 후 다시 시도해주세요.");
            }
            log.debug("[분산락 획득 성공] {} (유효시간: {}초)", lockName, distributedLock.leaseTime());
            return transactionHandlerForAop.proceedInNewTx(joinPoint);
        } catch (OdyException exception) {
            throw exception;
        } catch (Throwable exception) {
            log.error("분산락 {} 획득 중 오류 발생", lockName, exception);
            throw new OdyServerErrorException("서버에 장애가 발생했습니다.");
        } finally {
            releaseLock(rLock);
        }
    }

    private void releaseLock(RLock rLock) {
        if (rLock.isHeldByCurrentThread()) {
            rLock.unlock();
            log.debug("[분산락 해제] {}", rLock.getName());
        }
    }

    private String getDynamicValue(ProceedingJoinPoint joinPoint, String key) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();

        for (int i = 0; i < signature.getParameterNames().length; i++) {
            context.setVariable(signature.getParameterNames()[i], joinPoint.getArgs()[i]);
        }
        return parser.parseExpression(key).getValue(context, String.class);
    }
}
