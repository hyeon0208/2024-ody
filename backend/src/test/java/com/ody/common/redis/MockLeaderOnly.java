package com.ody.common.redis;

import com.ody.common.aop.LeaderOnly;
import java.lang.annotation.Annotation;
import java.util.concurrent.TimeUnit;

public record MockLeaderOnly(String key, long waitTime, long leaseTime, TimeUnit timeUnit) implements LeaderOnly {

    @Override
    public Class<? extends Annotation> annotationType() {
        return LeaderOnly.class;
    }
}
