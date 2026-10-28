package com.kbi.appcleaner

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.text.format.Formatter
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.ArrayDeque
import java.util.Calendar
import java.util.Date
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    /** 상단 탭 */
    private enum class Tab { NEW, GAME, UNKNOWN, ALL }

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs

    private lateinit var toolbar: MaterialToolbar
    private lateinit var chipGroup: ChipGroup
    private lateinit var chipGame: Chip
    private lateinit var chipNew: Chip
    private lateinit var chipUnknown: Chip
    private lateinit var chipAll: Chip
    private lateinit var periodButton: Button
    private lateinit var snapshotButton: Button
    private lateinit var tabHint: TextView
    private lateinit var list: RecyclerView
    private lateinit var loading: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var summary: TextView
    private lateinit var selectAll: CheckBox
    private lateinit var deleteButton: Button
    private lateinit var progressPanel: LinearLayout
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var stopButton: Button

    private val adapter = AppAdapter(
        onSelectionChanged = { updateBottom() },
        onLongPress = { toggleProtect(it) }
    )

    private var allApps: List<InstalledApp> = emptyList()
    private var tab = Tab.NEW

    // ── 일괄 삭제 대기열 ──
    private val queue = ArrayDeque<InstalledApp>()
    private var current: InstalledApp? = null
    private var batchTotal = 0
    private var removedCount = 0
    private var skippedCount = 0
    private var freedBytes = 0L
    private var batchRunning = false

    private val uninstallLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // 삭제 확인 창에서 돌아옴 → 실제로 지워졌는지 잠시 뒤 확인
            mainHandler.postDelayed({ afterOneUninstall() }, 500)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        toolbar = findViewById(R.id.toolbar)
        chipGroup = findViewById(R.id.tabChips)
        chipGame = findViewById(R.id.chipGame)
        chipNew = findViewById(R.id.chipNew)
        chipUnknown = findViewById(R.id.chipUnknown)
        chipAll = findViewById(R.id.chipAll)
        periodButton = findViewById(R.id.periodButton)
        snapshotButton = findViewById(R.id.snapshotButton)
        tabHint = findViewById(R.id.tabHint)
        list = findViewById(R.id.appList)
        loading = findViewById(R.id.loading)
        emptyView = findViewById(R.id.emptyView)
        summary = findViewById(R.id.summaryText)
        selectAll = findViewById(R.id.selectAll)
        deleteButton = findViewById(R.id.deleteButton)
        progressPanel = findViewById(R.id.progressPanel)
        progressText = findViewById(R.id.progressText)
        progressBar = findViewById(R.id.progressBar)
        stopButton = findViewById(R.id.stopButton)

        setSupportActionBar(toolbar)

        // 상태바·내비게이션바와 겹치지 않게
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = bars.bottom, left = bars.left, right = bars.right)
            toolbar.updatePadding(top = bars.top)   // 상태바 뒤까지 파란색으로
            insets
        }

        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        chipGroup.setOnCheckedStateChangeListener { _, ids ->
            tab = when (ids.firstOrNull()) {
                R.id.chipGame -> Tab.GAME
                R.id.chipUnknown -> Tab.UNKNOWN
                R.id.chipAll -> Tab.ALL
                else -> Tab.NEW
            }
            refreshList()
        }

        selectAll.setOnClickListener {
            val checked = selectAll.isChecked
            visibleApps().forEach { if (!it.isProtected) it.selected = checked }
            adapter.submit(visibleApps())
            updateBottom()
        }

        periodButton.setOnClickListener { choosePeriod() }
        snapshotButton.setOnClickListener { confirmSnapshot() }
        deleteButton.setOnClickListener { confirmDelete() }
        stopButton.setOnClickListener { stopBatch() }

        scan()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)  // 화면이 닫힌 뒤 예약된 작업이 실행되지 않도록
        executor.shutdownNow()
    }

    // ───────────────────────── 메뉴 ─────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_refresh -> { if (!batchRunning) scan(); true }
        R.id.action_snapshot -> { confirmSnapshot(); true }
        R.id.action_help -> { showHelp(); true }
        R.id.action_about -> { showAbout(); true }
        else -> super.onOptionsItemSelected(item)
    }

    // ───────────────────────── 목록 ─────────────────────────

    private fun scan() {
        loading.visibility = View.VISIBLE
        list.visibility = View.GONE
        emptyView.visibility = View.GONE
        executor.execute {
            val apps: List<InstalledApp> = try {
                AppScanner.scan(applicationContext)
            } catch (e: Exception) {
                emptyList()
            }
            val mine = prefs.protectedApps
            apps.forEach { it.isProtected = it.packageName in mine }
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                allApps = apps.sortedByDescending { it.installedAt }   // 최근 설치순
                loading.visibility = View.GONE
                refreshList()
            }
        }
    }

    /** '새로 깔린 앱' 탭 조건을 한 번 계산해 돌려줌 */
    private fun newAppFilter(): (InstalledApp) -> Boolean {
        val days = prefs.newPeriodDays
        if (days == 0 && prefs.hasSnapshot) {
            val before = prefs.snapshotApps
            return { it.packageName !in before }
        }
        val effectiveDays = if (days == 0) 7 else days
        val cal = Calendar.getInstance()
        if (effectiveDays == 1) {  // 오늘: 오늘 0시 이후
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        } else {
            cal.add(Calendar.DAY_OF_YEAR, -effectiveDays)
        }
        val since = cal.timeInMillis
        return { it.installedAt >= since }
    }

    private fun appsFor(t: Tab): List<InstalledApp> = when (t) {
        Tab.GAME -> allApps.filter { it.isGame }.sortedWith(
            compareBy<InstalledApp> { it.game.ordinal }.thenByDescending { it.sizeBytes }
        )
        Tab.NEW -> allApps.filter(newAppFilter())
        Tab.UNKNOWN -> allApps.filter { it.isUnknownSource }
        Tab.ALL -> allApps
    }

    private fun visibleApps(): List<InstalledApp> = appsFor(tab)

    private fun refreshList() {
        chipGame.text = getString(R.string.tab_game, appsFor(Tab.GAME).size)
        chipNew.text = getString(R.string.tab_new, appsFor(Tab.NEW).size)
        chipUnknown.text = getString(R.string.tab_unknown, appsFor(Tab.UNKNOWN).size)
        chipAll.text = getString(R.string.tab_all, allApps.size)

        periodButton.visibility = if (tab == Tab.NEW) View.VISIBLE else View.GONE
        snapshotButton.text = if (prefs.hasSnapshot) getString(
            R.string.snapshot_button_again, DateFormat.format("M/d HH:mm", Date(prefs.snapshotTime))
        ) else getString(R.string.snapshot_button)
        periodButton.text = periodLabel()
        tabHint.text = when (tab) {
            Tab.GAME -> getString(R.string.hint_game)
            Tab.NEW -> getString(R.string.hint_new)
            Tab.UNKNOWN -> getString(R.string.hint_unknown)
            Tab.ALL -> getString(R.string.hint_all)
        }

        val shown = visibleApps()
        adapter.submit(shown)
        val isEmpty = shown.isEmpty()
        list.visibility = if (isEmpty) View.GONE else View.VISIBLE
        emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        emptyView.text = when (tab) {
            Tab.GAME -> getString(R.string.empty_game)
            Tab.NEW -> getString(R.string.empty_new)
            Tab.UNKNOWN -> getString(R.string.empty_unknown)
            Tab.ALL -> getString(R.string.empty_all)
        }
        updateBottom()
    }

    /** 체크한 앱 (탭을 옮겨도 체크는 유지됨) */
    private fun selectedApps(): List<InstalledApp> =
        allApps.filter { it.selected && !it.isProtected }

    private fun updateBottom() {
        val sel = selectedApps()
        val bytes = sel.sumOf { it.sizeBytes }
        val shownSelectable = visibleApps().filter { !it.isProtected }
        selectAll.isChecked = shownSelectable.isNotEmpty() && shownSelectable.all { it.selected }
        summary.text = getString(
            R.string.selected_summary, sel.size, Formatter.formatShortFileSize(this, bytes)
        )
        deleteButton.isEnabled = sel.isNotEmpty() && !batchRunning
        deleteButton.text = if (sel.isEmpty()) getString(R.string.delete_none)
        else getString(R.string.delete_n, sel.size)
    }

    // ───────────────────────── 기간 · 기억하기 ─────────────────────────

    private fun periodLabel(): String {
        val d = prefs.newPeriodDays
        return when {
            d == 0 && prefs.hasSnapshot -> getString(
                R.string.period_since, DateFormat.format("M/d HH:mm", Date(prefs.snapshotTime))
            )
            d == 1 -> getString(R.string.period_today)
            d == 0 -> getString(R.string.period_days, 7)
            else -> getString(R.string.period_days, d)
        }
    }

    private fun choosePeriod() {
        val labels = ArrayList<String>()
        val values = ArrayList<Int>()
        if (prefs.hasSnapshot) {
            labels += getString(
                R.string.period_since, DateFormat.format("M/d HH:mm", Date(prefs.snapshotTime))
            ); values += 0
        }
        labels += getString(R.string.period_today); values += 1
        for (d in listOf(3, 7, 30)) { labels += getString(R.string.period_days, d); values += d }

        val currentIndex = values.indexOf(prefs.newPeriodDays).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.period_title)
            .setSingleChoiceItems(labels.toTypedArray(), currentIndex) { dlg, which ->
                prefs.newPeriodDays = values[which]
                dlg.dismiss()
                refreshList()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmSnapshot() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.snapshot_title)
            .setMessage(R.string.snapshot_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.snapshot_do) { _, _ ->
                prefs.snapshotApps = allApps.map { it.packageName }.toSet()
                prefs.snapshotTime = System.currentTimeMillis()
                prefs.newPeriodDays = 0
                Toast.makeText(this, R.string.snapshot_done, Toast.LENGTH_LONG).show()
                refreshList()
            }
            .show()
    }

    // ───────────────────────── 내 앱 보호 ─────────────────────────

    private fun toggleProtect(app: InstalledApp) {
        val mine = prefs.protectedApps.toMutableSet()
        if (app.isProtected) {
            mine.remove(app.packageName)
            app.isProtected = false
            Toast.makeText(this, getString(R.string.unprotected, app.label), Toast.LENGTH_SHORT).show()
        } else {
            mine.add(app.packageName)
            app.isProtected = true
            app.selected = false
            Toast.makeText(this, getString(R.string.protected_msg, app.label), Toast.LENGTH_SHORT).show()
        }
        prefs.protectedApps = mine
        adapter.submit(visibleApps())
        updateBottom()
    }

    // ───────────────────────── 일괄 삭제 ─────────────────────────

    private fun confirmDelete() {
        val sel = selectedApps()
        if (sel.isEmpty()) return
        val names = sel.take(10).joinToString("\n") { "• ${it.label}" } +
                if (sel.size > 10) "\n… 외 ${sel.size - 10}개" else ""

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.confirm_title, sel.size))
            .setMessage(getString(R.string.confirm_message, names))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.start_delete) { _, _ -> startBatch(sel) }
            .show()
    }

    private fun startBatch(sel: List<InstalledApp>) {
        queue.clear()
        queue.addAll(sel)
        batchTotal = sel.size
        removedCount = 0
        skippedCount = 0
        freedBytes = 0L
        batchRunning = true
        progressPanel.visibility = View.VISIBLE
        progressBar.max = batchTotal
        progressBar.progress = 0
        updateBottom()
        launchNext()
    }

    private fun launchNext() {
        if (!batchRunning) { finishBatch(); return }
        val next = queue.pollFirst()
        if (next == null) { finishBatch(); return }
        current = next

        if (!AppScanner.isInstalled(this, next.packageName)) {  // 이미 지워짐
            afterOneUninstall(); return
        }

        val done = removedCount + skippedCount
        progressBar.progress = done
        progressText.text = getString(R.string.progress_text, done + 1, batchTotal, next.label)

        @Suppress("DEPRECATION")
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${next.packageName}"))
            .putExtra(Intent.EXTRA_RETURN_RESULT, true)  // 실제 삭제가 끝난 뒤에 돌아오도록
        try {
            uninstallLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            current = null
            skippedCount++
            launchNext()
        }
    }

    private fun afterOneUninstall() {
        val app = current ?: return
        current = null
        if (AppScanner.isInstalled(this, app.packageName)) {
            skippedCount++          // '취소'를 눌렀거나 삭제 실패
        } else {
            removedCount++
            freedBytes += app.sizeBytes
        }
        progressBar.progress = removedCount + skippedCount
        launchNext()
    }

    /** 중지: 지금 떠 있는 확인창까지만 처리하고 멈춤 */
    private fun stopBatch() {
        queue.clear()
        batchRunning = false
        if (current == null) finishBatch()
    }

    private var showingResult = false
    private fun finishBatch() {
        if (showingResult) return
        showingResult = true
        batchRunning = false
        progressPanel.visibility = View.GONE
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.done_title)
            .setMessage(
                getString(
                    R.string.done_message, removedCount, skippedCount,
                    Formatter.formatShortFileSize(this, freedBytes)
                )
            )
            .setPositiveButton(R.string.ok, null)
            .setOnDismissListener { showingResult = false }
            .show()
        scan()
    }

    // ───────────────────────── 도움말 · 정보 ─────────────────────────

    private fun showHelp() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.help_title)
            .setMessage(R.string.help_message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_name)
            .setMessage(getString(R.string.about_message, BuildConfig.VERSION_NAME))
            .setPositiveButton(R.string.ok, null)
            .setNeutralButton(R.string.send_mail) { _, _ ->
                val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:mykbi@daum.net"))
                    .putExtra(Intent.EXTRA_SUBJECT, "[우리아이 앱청소] 문의")
                try {
                    startActivity(mail)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this, "mykbi@daum.net", Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }
}
