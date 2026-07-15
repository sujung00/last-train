/* eslint-disable no-restricted-globals */
/* global clients */
/**
 * Service Worker: 웹 푸시 알림 수신 및 처리
 *
 * 역할:
 *   1. push 이벤트: 백엔드에서 전송한 알림 수신 → 브라우저 알림창 표시
 *   2. notificationclick 이벤트: 사용자가 알림을 클릭 → 앱 포커싱 또는 새 탭 열기
 */

// ── push 이벤트: 웹 푸시 알림 수신 및 표시 ──────────────────────────────────────
self.addEventListener('push', (event) => {
  // 푸시 메시지에서 데이터 파싱
  let notificationData = {
    title: '막차 알리미 🚂',
    body: '알림이 도착했습니다',
    icon: '/icon-192x192.png', // public 폴더의 아이콘
    badge: '/badge-72x72.png',
    tag: 'last-train-notification',
  }

  try {
    if (event.data) {
      const data = event.data.json()
      notificationData = {
        ...notificationData,
        ...data, // 백엔드에서 보낸 title, body 등으로 덮어쓰기
      }
    }
  } catch {
    // JSON 파싱 실패 시 기본값 사용
    if (event.data) {
      notificationData.body = event.data.text()
    }
  }

  // 브라우저 알림창 표시
  event.waitUntil(
    self.registration.showNotification(notificationData.title, {
      body: notificationData.body,
      icon: notificationData.icon,
      badge: notificationData.badge,
      tag: notificationData.tag,
      data: notificationData, // 클릭 이벤트에서 참조할 데이터
    })
  )
})

// ── notificationclick 이벤트: 알림 클릭 시 앱으로 이동 ───────────────────────────
self.addEventListener('notificationclick', (event) => {
  event.notification.close()

  const urlToOpen = '/' // 앱 홈페이지로 이동

  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      // 기존 창이 열려 있으면 포커싱
      for (let i = 0; i < clientList.length; i++) {
        const client = clientList[i]
        if (client.url === urlToOpen && 'focus' in client) {
          return client.focus()
        }
      }
      // 기존 창이 없으면 새 창 열기
      if (clients.openWindow) {
        return clients.openWindow(urlToOpen)
      }
    })
  )
})

// ── notificationclose 이벤트: 알림이 닫힐 때 처리 (선택사항) ────────────────────
self.addEventListener('notificationclose', () => {
  // 필요시 백엔드에 알림 닫힘 로그 전송 가능
})
