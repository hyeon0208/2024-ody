package com.ody.common.aop;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TransactionHandlerForAop {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Object proceedInNewTx(ProceedingJoinPoint joinPoint) throws Throwable {
        return joinPoint.proceed();
    }
}
