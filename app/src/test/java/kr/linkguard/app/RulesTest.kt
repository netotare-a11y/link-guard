package kr.linkguard.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    private val home = "com.sec.android.app.launcher"
    private val trusted = setOf(home)

    @Test
    fun kakaoBrowser() {
        assertTrue(Rules.isKakaoBrowser("com.kakao.talk.activity.browser.InAppBrowserActivity"))
        assertFalse(Rules.isKakaoBrowser("com.kakao.talk.activity.chatroom.ChatRoomActivity"))
    }

    @Test
    fun coupang() {
        // 카톡·브라우저에서 갑자기 뜬 쿠팡은 막는다
        assertFalse(Rules.allowCoupang("com.kakao.talk", false, trusted, 60_000))
        // 홈 화면에서 직접 켠 쿠팡은 둔다
        assertTrue(Rules.allowCoupang(home, false, trusted, 60_000))
        // 알림을 눌러 켠 쿠팡은 둔다
        assertTrue(Rules.allowCoupang("com.kakao.talk", false, trusted, 1_000))
        // 쿠팡에서 결제 앱에 갔다 돌아오면 둔다
        assertTrue(Rules.allowCoupang("com.some.pay", true, trusted, 60_000))
    }
}
