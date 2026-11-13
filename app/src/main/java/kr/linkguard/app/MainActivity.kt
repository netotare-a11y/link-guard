package kr.linkguard.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var log: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        status = TextView(this).apply { textSize = 18f }
        log = TextView(this).apply { setTextIsSelectable(true) }

        col.addView(status)
        col.addView(note("카톡에서 링크를 누르면 기본 브라우저로 넘기고, 링크 때문에 쿠팡 앱이 뜨면 바로 닫습니다.\n"))

        col.addView(button("① 접근성 설정 열기") { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        col.addView(note("설치된 앱 → 링크 지킴이 → 켜기\n"))

        col.addView(button("①이 '제한된 설정'으로 막히면 누르기") { open(appScreen(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageName)) })
        col.addView(note("열린 화면 오른쪽 위 ⋮ → 제한된 설정 허용 → 다시 ①\n"))

        col.addView(button("② 쿠팡 링크 설정 열기") {
            open(
                appScreen(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Rules.COUPANG),
                appScreen(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Rules.COUPANG),
            )
        })
        col.addView(note("'지원되는 링크 열기'를 끄기\n"))

        col.addView(button("③ 기본 브라우저 확인") { open(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) })
        col.addView(note("브라우저 앱이 삼성 인터넷인지 확인\n"))

        col.addView(button("진단 복사") {
            val info = "링크 지킴이 ${version(packageName)} / ${Build.MODEL} / 안드로이드 ${Build.VERSION.RELEASE}" +
                " / 카톡 ${version(Rules.KAKAO)} / 쿠팡 ${version(Rules.COUPANG)}\n" + Diag.text()
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("링크 지킴이 진단", info))
            Toast.makeText(this, "복사했어요", Toast.LENGTH_SHORT).show()
        })
        col.addView(log)
        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() {
        super.onResume()
        val on = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains("$packageName/") == true
        status.text = if (on) "켜져 있어요" else "꺼져 있어요. 아래 ①을 눌러 켜 주세요"
        log.text = Diag.text()
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun note(s: String) = TextView(this).apply { text = s }

    private fun appScreen(action: String, pkg: String) = Intent(action, Uri.parse("package:$pkg"))

    /** 앞에서부터 열리는 화면 하나를 연다. 폰마다 없는 설정 화면이 있어서 후보를 여러 개 받는다. */
    private fun open(vararg intents: Intent) {
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (e: Exception) {
                // 다음 후보로
            }
        }
        Toast.makeText(this, "이 화면을 열 수 없어요", Toast.LENGTH_SHORT).show()
    }

    private fun version(pkg: String) = try {
        packageManager.getPackageInfo(pkg, 0).let { "${it.versionName}(${it.longVersionCode})" }
    } catch (e: Exception) {
        "없음"
    }
}
