package com.ody.common.aop;

import com.ody.common.exception.OdyException;
import com.ody.common.exception.OdyServerErrorException;
import com.ody.common.redis.LeaderManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LeaderOnlyAop {


    private final LeaderManager leaderManager;

    @Around("@annotation(leaderOnly)")
    public Object executeIfLeader(ProceedingJoinPoint joinPoint, LeaderOnly leaderOnly) {

        return leaderManager.executeIfLeader(() -> {
            try {
                return joinPoint.proceed();
            } catch (OdyException exception) {
                throw exception;
            } catch (Throwable throwable) {
                log.error("리더 작업 처리중 에러 발생 : ", throwable);
                throw new OdyServerErrorException("서버에 장애가 발생했습니다.");
            }
        }, leaderOnly);
    }
}
