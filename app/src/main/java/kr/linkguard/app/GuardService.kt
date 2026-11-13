package kr.linkguard.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val WEB = "android.webkit.WebView"

class GuardService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var home: String? = null
    private var keyboard: String? = null
    private var trusted = emptySet<String>()
    private var last = ""              // 직전에 앞에 있던 앱
    private var fromCoupang = false    // 쿠팡을 직접 켠 뒤 아직 홈 화면을 거치지 않았는지
    private var systemUiAt = -10_000L  // 알림창 같은 시스템 화면이 마지막으로 뜬 때
    private var kickUntil = 0L
    private var ejecting = false
    private var ejectedWindow = -1     // 이미 밖으로 넘긴 카톡 브라우저 창. 같은 창을 두 번 넘기지 않는다.

    override fun onServiceConnected() {
        home = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName
        keyboard = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')
        trusted = setOfNotNull(home, "android", "com.android.intentresolver", "com.samsung.android.app.galaxyfinder")
        Diag.log("켜짐 (홈 화면 앱: $home)")
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        val now = SystemClock.uptimeMillis()
        when (pkg) {
            "com.android.systemui" -> systemUiAt = now
            keyboard -> Unit // 키보드가 뜨는 것은 앱 이동으로 치지 않는다
            Rules.COUPANG -> onCoupang(now)
            else -> {
                last = pkg
                if (pkg == home) fromCoupang = false
                if (pkg == Rules.KAKAO) onKakao(e.className?.toString().orEmpty(), e.windowId)
            }
        }
    }

    private fun onCoupang(now: Long) {
        if (Rules.allowCoupang(last, fromCoupang, trusted, now - systemUiAt)) {
            fromCoupang = true
            return
        }
        if (now < kickUntil) return // 이미 닫는 중
        kickUntil = now + 2000
        Diag.log("쿠팡 자동 실행 막음 (연 곳: $last)")
        Toast.makeText(this, "쿠팡 자동 실행을 막았어요", Toast.LENGTH_SHORT).show()
        // shortcut: 뒤로 3번 뒤 홈으로 보내는 간격은 실제 폰에서 맞춘 값이 아니다. 쿠팡이 남으면 간격을 조정한다.
        for (i in 0..3) handler.postDelayed({
            if (active() == Rules.COUPANG) {
                performGlobalAction(if (i < 3) GLOBAL_ACTION_BACK else GLOBAL_ACTION_HOME)
            }
        }, 150L + 350L * i)
    }

    private fun onKakao(cls: String, windowId: Int) {
        Diag.log("카톡 화면: $cls")
        if (!Rules.isKakaoBrowser(cls)) return
        if (windowId == ejectedWindow) close()
        else if (!ejecting) {
            ejecting = true
            eject(windowId, 0, false)
        }
    }

    /** 카톡 브라우저의 메뉴(⋮)를 열어 '다른 브라우저로 열기'를 대신 누른다. 0.2초마다, 4초까지. */
    private fun eject(windowId: Int, tries: Int, menuOpened: Boolean) {
        if (click(find("다른 브라우저"))) {
            ejecting = false
            ejectedWindow = windowId
            Diag.log("카톡 브라우저 → 기본 브라우저로 넘김")
            return
        }
        if (tries >= 20) {
            ejecting = false
            Diag.log("카톡 브라우저에서 '다른 브라우저로 열기'를 못 찾음 (메뉴 누름: $menuOpened)")
            record()
            return
        }
        val opened = menuOpened || click(find("더보기", "메뉴"))
        handler.postDelayed({ eject(windowId, tries + 1, opened) }, 200)
    }

    /** 넘긴 뒤 카톡에 남은 브라우저를 닫는다. */
    private fun close() {
        // shortcut: 닫기 단추를 못 찾으면 뒤로 한 번만 누른다. 페이지 기록이 있으면 안 닫히니, 그때는 진단 기록의 단추 이름으로 고친다.
        handler.postDelayed({
            if (active() == Rules.KAKAO && !click(find("닫기"))) performGlobalAction(GLOBAL_ACTION_BACK)
        }, 300)
    }

    private fun active() = rootInActiveWindow?.packageName?.toString()

    private fun kakaoRoots() = (windows.mapNotNull { it.root } + listOfNotNull(rootInActiveWindow))
        .filter { it.packageName?.toString() == Rules.KAKAO }
        .distinct()

    /** 카톡 화면에서 글자나 설명에 label이 들어간 단추를 찾는다. 웹 페이지 안의 글자는 건드리지 않는다. */
    private fun find(vararg labels: String): AccessibilityNodeInfo? {
        for (root in kakaoRoots()) for (label in labels) {
            root.findAccessibilityNodeInfosByText(label).firstOrNull { !inWeb(it) }?.let { return it }
        }
        return null
    }

    private fun inWeb(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.className?.toString() == WEB) return true
            n = n.parent
        }
        return false
    }

    private fun click(node: AccessibilityNodeInfo?): Boolean {
        var n = node
        while (n != null) {
            if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent
        }
        return false
    }

    /** 진단용: 넘기기에 실패한 카톡 브라우저의 단추 구조를 적어 둔다. 웹 페이지 내용은 적지 않는다. */
    private fun record() {
        val sb = StringBuilder()
        for (root in kakaoRoots()) walk(root, 0, sb)
        Diag.dump = sb.toString()
    }

    private fun walk(n: AccessibilityNodeInfo, depth: Int, sb: StringBuilder) {
        val web = n.className?.toString() == WEB
        if (sb.length < 6000 && (web || n.isClickable || !n.text.isNullOrEmpty() || !n.contentDescription.isNullOrEmpty())) {
            sb.append(" ".repeat(depth)).append(n.className?.toString()?.substringAfterLast('.'))
                .append(" id=").append(n.viewIdResourceName?.substringAfterLast('/'))
                .append(" 글=").append(n.text?.take(20))
                .append(" 설명=").append(n.contentDescription?.take(20))
                .append(if (n.isClickable) " [누름]" else "").append('\n')
        }
        if (web) return
        for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1, sb) }
    }
}

/** 화면에 보여 주고 '진단 복사'로 내보내는 기록. shortcut: 메모리에만 있어 앱이 꺼지면 사라진다. 오래 남겨야 하면 파일로 바꾼다. */
object Diag {
    private val lines = ArrayDeque<String>()
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.KOREA)
    private var lastText = ""
    var dump = ""

    fun log(s: String) {
        if (s == lastText) return // 같은 줄이 연달아 쌓이지 않게
        lastText = s
        lines.addLast(clock.format(Date()) + " " + s)
        if (lines.size > 80) lines.removeFirst()
    }

    fun text() = lines.joinToString("\n") + if (dump.isEmpty()) "" else "\n--- 카톡 브라우저 화면 구조 ---\n$dump"
}
