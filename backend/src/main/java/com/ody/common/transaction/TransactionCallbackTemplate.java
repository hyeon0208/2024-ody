package com.ody.common.transaction;

import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionCallbackTemplate {

    private final TransactionTemplate transactionTemplate;

    public <T> T executeWithAfterCommitAction(Supplier<T> action, Runnable afterCommitAction) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                afterCommitAction.run();
                log.debug("트랜잭션 커밋 후 추가 작업 실행 완료");
            }
        });
        return transactionTemplate.execute(transactionStatus -> action.get());
    }
}
