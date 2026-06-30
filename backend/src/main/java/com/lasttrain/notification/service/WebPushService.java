package com.lasttrain.notification.service;

import com.lasttrain.notification.domain.NotificationSubscription;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Security;

/**
 * VAPID(Voluntary Application Server Identification) 방식으로
 * 브라우저에 Web Push 알림을 발송하는 서비스입니다.
 *
 * ── VAPID란? ────────────────────────────────────────────────────────────────
 * Push 알림을 보내는 서버가 "나는 신뢰할 수 있는 서버입니다"를 증명하는 방식입니다.
 * 서버는 미리 만들어둔 공개키/비공개키 쌍으로 메시지에 서명을 붙입니다.
 * 브라우저는 이 서명을 검증해서 신뢰할 수 없는 서버의 알림을 차단합니다.
 *
 * ── 발송 흐름 ───────────────────────────────────────────────────────────────
 *   1. 사용자가 브라우저에서 알림 권한을 허용합니다.
 *   2. 브라우저는 Push 서비스(Google FCM 등)에 등록하고 endpoint, auth, p256dh를 반환합니다.
 *   3. 서버는 이 세 값을 저장합니다 (NotificationSubscription).
 *   4. 알림 발송 시 서버가 VAPID 키로 서명 → Push 서비스 → 브라우저 → 사용자 화면에 표시됩니다.
 * ────────────────────────────────────────────────────────────────────────────
 */
@Slf4j
@Service
public class WebPushService {

    // application.yml의 vapid.* 값을 주입받습니다.
    // VAPID 키는 npx web-push generate-vapid-keys 명령어로 생성합니다.
    @Value("${vapid.public-key}")
    private String vapidPublicKey;

    @Value("${vapid.private-key}")
    private String vapidPrivateKey;

    // 알림 발송 주체를 Push 서비스에 알리는 식별자입니다.
    // mailto: 형식의 이메일 주소를 사용합니다. (예: "mailto:admin@example.com")
    @Value("${vapid.subject}")
    private String vapidSubject;

    // 실제 Push 알림 발송을 담당하는 라이브러리 객체입니다.
    private PushService pushService;

    /**
     * 앱 시작 시 PushService를 초기화합니다.
     *
     * @PostConstruct: Spring 빈이 생성되고 @Value 주입이 완료된 직후 자동으로 실행됩니다.
     *
     * BouncyCastle을 먼저 등록하는 이유:
     *   Web Push는 P-256 타원곡선 암호화를 사용합니다.
     *   Java 기본 JCA(보안 라이브러리)는 P-256을 지원하지 않아서
     *   BouncyCastle을 추가 보안 프로바이더로 등록해야 합니다.
     */
    @PostConstruct
    public void init() {
        // BouncyCastle을 JCA 보안 프로바이더로 등록합니다.
        // 이미 등록된 경우 중복 등록되지 않습니다.
        Security.addProvider(new BouncyCastleProvider());

        try {
            // VAPID 공개키, 비공개키, subject로 PushService를 초기화합니다.
            // 이 객체가 이후 모든 알림 발송에 재사용됩니다.
            pushService = new PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
            log.info("[WebPushService] PushService 초기화 완료");
        } catch (Exception e) {
            // 초기화 실패 시 앱 전체를 중단시키지 않고 로그만 남깁니다.
            // 이 경우 send() 호출 시 NullPointerException이 발생하므로
            // 환경변수 설정(VAPID_PUBLIC_KEY 등)을 확인해야 합니다.
            log.error("[WebPushService] PushService 초기화 실패 — 환경변수(VAPID_*) 설정을 확인하세요: {}",
                    e.getMessage(), e);
        }
    }

    /**
     * 특정 구독자에게 Web Push 알림을 발송합니다.
     *
     * 발송 실패 시 예외를 던지지 않고 로그만 남기는 이유:
     *   한 건의 발송 실패가 Worker 전체(다른 사람 알림)를 중단시키면 안 됩니다.
     *   실패는 기록하고, 나머지 알림은 계속 처리합니다.
     *
     * @param subscription 알림을 받을 사용자의 구독 정보 (endpoint, p256dh, auth)
     * @param message      알림 메시지 내용 (예: "막차까지 30분 남았어요!")
     */
    public void send(NotificationSubscription subscription, String message) {
        try {
            // ── Step 1. Notification 객체 생성 ─────────────────────────────────────────
            //
            // endpoint : 이 브라우저로 메시지를 보낼 수 있는 Push 서비스 주소
            // p256dh   : 메시지를 암호화할 때 사용하는 사용자의 공개키 (Base64)
            // auth     : 메시지를 암호화할 때 사용하는 인증 비밀값 (Base64)
            // message  : 알림 본문 (브라우저 Service Worker가 이 값을 받아 알림으로 표시)
            Notification notification = new Notification(
                    subscription.getEndpoint(),
                    subscription.getP256dh(),
                    subscription.getAuth(),
                    message
            );

            // ── Step 2. Push 서비스로 알림 발송 ────────────────────────────────────────
            //
            // VAPID 서명을 붙여 Push 서비스(Google FCM 등)에 HTTP 요청을 보냅니다.
            // Push 서비스는 서명을 검증한 뒤 사용자의 브라우저로 알림을 전달합니다.
            pushService.send(notification);

            log.info("[WebPush 발송 성공] subscriptionId={}", subscription.getSubscriptionId());

        } catch (Exception e) {
            // 발송 실패 시 로그를 남기고 정상 종료합니다.
            // 예외를 던지면 NotificationScheduler의 루프가 중단될 수 있어 방어적으로 처리합니다.
            log.error("[WebPush 발송 실패] subscriptionId={}, 사유={}",
                    subscription.getSubscriptionId(), e.getMessage(), e);
        }
    }
}
