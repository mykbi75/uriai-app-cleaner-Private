package com.kbi.appcleaner

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import java.io.File

/** 게임 판정 */
enum class GameKind {
    CONFIRMED,   // 앱이 스스로 "게임"으로 등록 (플레이스토어 카테고리)
    LIKELY,      // 제작사·이름으로 보아 게임으로 추정
    NONE
}

/** 어디서 설치됐는지 */
enum class Source {
    PLAY,         // 구글 플레이스토어
    GALAXY,       // 갤럭시스토어
    OTHER_STORE,  // 원스토어 등 다른 정식 스토어
    MOVED,        // 스마트스위치 등으로 옮겨온 앱
    AUTO,         // 개통·초기설정 때 통신사/제조사가 자동 설치
    UNKNOWN       // 출처 불명 (파일·브라우저·메신저·광고 링크 등으로 설치)
}

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val sizeBytes: Long,
    val installedAt: Long,
    val game: GameKind,
    val source: Source,
    val sourceLabel: String,
    val isRecent: Boolean,
    var selected: Boolean = false,
    var isProtected: Boolean = false
) {
    val isGame get() = game != GameKind.NONE
    /** '출처 불명' 탭에 들어갈 앱 */
    val isUnknownSource get() = source == Source.UNKNOWN || source == Source.AUTO
}

object AppScanner {

    /** 최근 설치로 보는 기간 */
    const val RECENT_DAYS = 7

    private val STORE_NAMES = mapOf(
        "com.android.vending" to "플레이스토어",
        "com.sec.android.app.samsungapps" to "갤럭시스토어",
        "com.skt.skaf.A000Z00040" to "원스토어",
        "com.kt.olleh.storefront" to "원스토어",
        "com.lguplus.appstore" to "원스토어",
        "com.amazon.venezia" to "아마존 앱스토어",
        "com.huawei.appmarket" to "화웨이 앱갤러리",
        "com.xiaomi.market" to "샤오미 앱스토어",
        "com.xiaomi.mipicks" to "샤오미 앱스토어",
        "com.oppo.market" to "오포 앱마켓",
        "com.heytap.market" to "오포 앱마켓",
        "com.vivo.appstore" to "비보 앱스토어"
    )

    private val MOVER_INSTALLERS = mapOf(
        "com.sec.android.easyMover" to "스마트스위치로 옮김",
        "com.google.android.apps.restore" to "백업에서 복원",
        "com.google.android.apps.pixelmigrate" to "기기 이동으로 옮김"
    )

    private val AUTO_INSTALLERS = setOf(
        "com.samsung.android.app.omcagent",
        "com.android.preconfiguration",
        "com.facebook.system",
        "com.google.android.feedback"
    )

    private val GAME_PUBLISHER_PREFIXES = listOf(
        // 국내
        "com.netmarble", "com.ncsoft", "com.nexon", "com.kakaogames", "com.com2us",
        "com.gamevil", "com.wemade", "com.neowiz", "com.smilegate", "com.krafton",
        "com.pearlabyss", "com.devsisters", "com.webzen", "com.gravity", "com.nhn.hangame",
        "com.linegames", "com.supercat", "com.sundaytoz", "com.gameduo", "com.cookapps",
        // 해외
        "com.supercell", "com.king.", "com.tencent.ig", "com.pubg", "com.mojang",
        "com.roblox", "com.mihoyo", "com.hoyoverse", "com.playrix", "com.innersloth",
        "com.activision", "com.ea.gp", "com.ea.game", "com.gameloft", "com.zynga",
        "com.rovio", "com.kiloo", "com.outfit7", "com.nianticlabs", "com.moonactive",
        "com.dts.freefire", "com.garena", "com.lilithgame", "com.yostar", "com.igg.",
        "com.funplus", "com.scopely", "com.voodoo", "com.ketchapp", "com.miniclip",
        "com.playtika", "com.bandainamcoent", "com.square_enix", "com.sega.",
        "com.konami", "jp.konami", "com.nintendo", "com.netease", "com.mobile.legends",
        "com.blizzard", "com.riotgames", "com.epicgames", "com.ubisoft", "com.gamehouse"
    )

    private val GAME_WORDS = listOf(".game.", ".games.", ".game", "games.", "game.")

    fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** 지울 수 있는(사용자가 설치한) 앱을 모두 읽어 분류합니다. 시스템 앱은 제외. */
    fun scan(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(launcherIntent, 0)

        val recentLimit = System.currentTimeMillis() - RECENT_DAYS * 24L * 60 * 60 * 1000
        val seen = HashSet<String>()
        val result = ArrayList<InstalledApp>()

        for (ri in resolved) {
            val pkg = ri.activityInfo?.packageName ?: continue
            if (pkg == context.packageName) continue
            if (!seen.add(pkg)) continue

            val ai: ApplicationInfo = try {
                pm.getApplicationInfo(pkg, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                continue
            }
            if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue

            val installedAt = try {
                pm.getPackageInfo(pkg, 0).firstInstallTime
            } catch (e: Exception) {
                0L
            }
            val (source, sourceLabel) = sourceOf(pm, pkg)

            result += InstalledApp(
                packageName = pkg,
                label = ai.loadLabel(pm).toString(),
                icon = ai.loadIcon(pm),
                sizeBytes = apkSize(ai),
                installedAt = installedAt,
                game = classifyGame(ai),
                source = source,
                sourceLabel = sourceLabel,
                isRecent = installedAt >= recentLimit
            )
        }
        return result
    }

    /** 설치한 주체(스토어/앱)를 알아냅니다 */
    private fun sourceOf(pm: PackageManager, pkg: String): Pair<Source, String> {
        val installer: String? = try {
            if (Build.VERSION.SDK_INT >= 30) {
                pm.getInstallSourceInfo(pkg).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(pkg)
            }
        } catch (e: Exception) {
            null
        }

        if (installer == null) return Source.UNKNOWN to "출처 불명"

        STORE_NAMES[installer]?.let { name ->
            return when (installer) {
                "com.android.vending" -> Source.PLAY to name
                "com.sec.android.app.samsungapps" -> Source.GALAXY to name
                else -> Source.OTHER_STORE to name
            }
        }
        MOVER_INSTALLERS[installer]?.let { return Source.MOVED to it }
        if (installer in AUTO_INSTALLERS) return Source.AUTO to "자동 설치(통신사·제조사)"

        // 패키지 설치 관리자 = 파일(APK)을 직접 눌러서 설치한 경우
        if (installer.contains("packageinstaller")) return Source.UNKNOWN to "출처 불명 (파일로 설치)"

        // 그 밖의 앱(브라우저·메신저·광고 앱 등)이 설치시킨 경우
        val via = try {
            pm.getApplicationLabel(pm.getApplicationInfo(installer, 0)).toString()
        } catch (e: Exception) {
            installer
        }
        return Source.UNKNOWN to "출처 불명 ($via 통해 설치)"
    }

    private fun classifyGame(ai: ApplicationInfo): GameKind {
        if (ai.category == ApplicationInfo.CATEGORY_GAME) return GameKind.CONFIRMED
        @Suppress("DEPRECATION")
        if ((ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0) return GameKind.CONFIRMED

        val lower = ai.packageName.lowercase()
        if (GAME_PUBLISHER_PREFIXES.any { lower.startsWith(it) }) return GameKind.LIKELY
        if (GAME_WORDS.any { lower.contains(it) }) return GameKind.LIKELY
        return GameKind.NONE
    }

    /** 설치 파일 크기 합계 (게임 추가 데이터는 미포함 — 대략값) */
    private fun apkSize(ai: ApplicationInfo): Long {
        var total = 0L
        try {
            ai.sourceDir?.let { total += File(it).length() }
            ai.splitSourceDirs?.forEach { total += File(it).length() }
        } catch (e: Exception) {
        }
        return total
    }
}
