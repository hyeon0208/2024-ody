package com.ody.common;

import static org.mockito.ArgumentMatchers.anyString;

import com.google.firebase.messaging.FirebaseMessaging;
import com.ody.auth.service.apple.AppleRevokeTokenClient;
import com.ody.auth.service.kakao.KakaoAuthUnlinkClient;
import com.ody.notification.config.FcmConfig;
import com.ody.notification.service.FcmEventListener;
import com.ody.route.repository.RouteClientRedisTemplate;
import com.ody.route.service.RouteClientCircuitBreaker;
import com.ody.route.service.RouteClientManager;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@Import({TestRouteConfig.class, TestAuthConfig.class, FixtureGeneratorConfig.class, RedisTestContainersConfig.class})
@ActiveProfiles("test")
@RecordApplicationEvents
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
public abstract class BaseServiceTest {

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @MockBean
    private FcmConfig fcmConfig;

    @MockBean
    protected FirebaseMessaging firebaseMessaging;

    @MockBean
    protected FcmEventListener fcmEventListener;

    @MockBean
    protected RouteClientCircuitBreaker routeClientCircuitBreaker;

    @SpyBean
    protected RouteClientManager routeClientManager;

    @SpyBean
    protected KakaoAuthUnlinkClient kakaoAuthUnlinkClient;

    @SpyBean
    protected AppleRevokeTokenClient appleRevokeTokenClient;

    @Autowired
    protected FixtureGenerator fixtureGenerator;

    @Autowired
    protected RouteClientRedisTemplate redisTemplate;

    @Autowired
    protected ApplicationEvents applicationEvents;

    protected DtoGenerator dtoGenerator = new DtoGenerator();

    @BeforeEach
    void setUp() {
        databaseCleaner.cleanUp();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        Mockito.doNothing().when(kakaoAuthUnlinkClient).unlink(anyString());
        Mockito.doNothing().when(appleRevokeTokenClient).unlink(anyString());
    }
}

