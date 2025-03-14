package com.ody.common.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.ody.common.BaseServiceTest;
import com.ody.meeting.repository.MeetingRepository;
import com.ody.meeting.service.MeetingService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class LeaderManagerTest extends BaseServiceTest {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private MeetingService meetingService;

    @MockBean
    private MeetingRepository meetingRepository;

    @DisplayName("여러 인스턴스가 동시에 리더쉽을 얻으려고 하더라도 하나의 인스턴스만 리더가 된다.")
    @Test
    void electLeader() throws InterruptedException {
        MockLeaderOnly mockLeaderOnly = new MockLeaderOnly("ELECT_LEADER", 0, 3, TimeUnit.SECONDS);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch countDownLatch = new CountDownLatch(2);
        AtomicInteger leaderCount = new AtomicInteger(0);

        for (int i = 1; i <= 2; i++) {
            executorService.execute(() -> {
                try {
                    LeaderManager manager = new LeaderManager(redissonClient);
                    if (manager.isLeader(mockLeaderOnly)) {
                        leaderCount.incrementAndGet();
                    }
                } finally {
                    countDownLatch.countDown();
                }
            });
        }
        countDownLatch.await(3, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(3, TimeUnit.SECONDS);

        assertThat(leaderCount.get()).isEqualTo(1);
    }

    @DisplayName("리더가 리더쉽 해제 시 팔로워가 새로운 리더가 된다.")
    @Test
    void electNewLeader() throws InterruptedException {
        MockLeaderOnly mockLeaderOnly = new MockLeaderOnly("NEW_LEADER", 0, 3, TimeUnit.SECONDS);
        CountDownLatch leaderLatch = new CountDownLatch(1);
        CountDownLatch followerLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(1);
        AtomicBoolean isFollowerInitialLeader = new AtomicBoolean();
        AtomicBoolean isFollowerLastLeader = new AtomicBoolean();

        Thread leaderThread = new Thread(() -> {
            try {
                LeaderManager initialLeader = new LeaderManager(redissonClient);
                initialLeader.isLeader(mockLeaderOnly); // 최초 리더로 선출
                leaderLatch.countDown(); // 팔로워 스레드가 시작할 수 있도록 알림
                followerLatch.await(); // 팔로워 스레드가 리더십 확인을 마칠 때까지 대기
                initialLeader.releaseLeadership(mockLeaderOnly.key());
                completionLatch.countDown(); // 테스트가 완료됨을 알림
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        });

        Thread followerThread = new Thread(() -> {
            try {
                leaderLatch.await(); // 리더 스레드가 리더십을 획득할 때까지 대기
                LeaderManager follower = new LeaderManager(redissonClient);
                isFollowerInitialLeader.set(follower.isLeader(mockLeaderOnly));
                followerLatch.countDown(); // 리더 스레드에게 리더십 확인을 마쳤다고 알림
                completionLatch.await(); // 리더십 해제 및 테스트 완료를 기다림
                isFollowerLastLeader.set(follower.isLeader(mockLeaderOnly));
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        });

        // 스레드 실행
        leaderThread.start();
        followerThread.start();

        // 모든 스레드 완료 대기
        leaderThread.join();
        followerThread.join();

        assertThat(isFollowerInitialLeader.get()).isFalse();
        assertThat(isFollowerLastLeader.get()).isTrue();
    }

    @DisplayName("모임 논리 삭제 스케줄링 작업이 동시 실행 시 하나의 리더 인스턴스에서만 작업이 실행된다.")
    @Test
    void 스케줄링은_하나만() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch countDownLatch = new CountDownLatch(2);
        AtomicInteger executionCount = new AtomicInteger(0);

        Mockito.doAnswer(invocation -> {
            executionCount.incrementAndGet();
            return null;
        }).when(meetingRepository).updateAllByNotOverdueMeetings();

        for (int i = 1; i <= 2; i++) {
            executorService.execute(() -> {
                try {
                    meetingService.scheduleOverdueMeetings();
                } finally {
                    countDownLatch.countDown();
                }
            });
        }
        countDownLatch.await(3, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(3, TimeUnit.SECONDS);

        assertThat(executionCount.get()).isEqualTo(1);
    }
}
