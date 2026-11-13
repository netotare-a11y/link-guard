package kr.linkguard.app

object Rules {
    const val KAKAO = "com.kakao.talk"
    const val COUPANG = "com.coupang.mobile"

    /** 카톡 안 브라우저 화면인지. 이름은 추정값이라, 틀리면 진단 기록의 '카톡 화면' 줄을 보고 고친다. */
    fun isKakaoBrowser(cls: String) = cls.contains("browser", ignoreCase = true)

    /**
     * 쿠팡이 앞에 떴을 때 그냥 둘지 정한다.
     * 직접 켠 경우(홈 화면·검색·앱 선택 창·알림)와 쿠팡에서 다른 앱(결제 등)에 갔다 돌아온 경우만 둔다.
     */
    fun allowCoupang(last: String, fromCoupang: Boolean, trusted: Set<String>, sinceSystemUiMs: Long) =
        fromCoupang || last in trusted || sinceSystemUiMs < 5000
}
