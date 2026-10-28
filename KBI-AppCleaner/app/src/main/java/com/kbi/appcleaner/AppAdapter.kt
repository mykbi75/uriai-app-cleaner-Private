package com.kbi.appcleaner

import android.text.format.DateFormat
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.Date

class AppAdapter(
    private val onSelectionChanged: () -> Unit,
    private val onLongPress: (InstalledApp) -> Unit
) : RecyclerView.Adapter<AppAdapter.Holder>() {

    private var items: List<InstalledApp> = emptyList()

    fun submit(list: List<InstalledApp>) {
        items = list
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.appIcon)
        val name: TextView = v.findViewById(R.id.appName)
        val info: TextView = v.findViewById(R.id.appInfo)
        val badgeGame: TextView = v.findViewById(R.id.badgeGame)
        val badgeSource: TextView = v.findViewById(R.id.badgeSource)
        val badgeRecent: TextView = v.findViewById(R.id.badgeRecent)
        val badgeMine: TextView = v.findViewById(R.id.badgeMine)
        val check: CheckBox = v.findViewById(R.id.appCheck)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: Holder, position: Int) {
        val app = items[position]
        val ctx = h.itemView.context

        h.icon.setImageDrawable(app.icon)
        h.name.text = app.label

        val size = Formatter.formatShortFileSize(ctx, app.sizeBytes)
        val date = if (app.installedAt > 0)
            DateFormat.getDateFormat(ctx).format(Date(app.installedAt)) else "-"
        h.info.text = ctx.getString(R.string.item_info, size, date)

        // 게임 배지
        when (app.game) {
            GameKind.CONFIRMED -> {
                h.badgeGame.visibility = View.VISIBLE
                h.badgeGame.setText(R.string.badge_game)
                h.badgeGame.background = ContextCompat.getDrawable(ctx, R.drawable.badge_game)
                h.badgeGame.setTextColor(ContextCompat.getColor(ctx, R.color.badge_game_fg))
            }
            GameKind.LIKELY -> {
                h.badgeGame.visibility = View.VISIBLE
                h.badgeGame.setText(R.string.badge_likely)
                h.badgeGame.background = ContextCompat.getDrawable(ctx, R.drawable.badge_likely)
                h.badgeGame.setTextColor(ContextCompat.getColor(ctx, R.color.badge_likely_fg))
            }
            GameKind.NONE -> h.badgeGame.visibility = View.GONE
        }

        // 설치 출처 배지
        h.badgeSource.text = app.sourceLabel
        h.badgeSource.background = ContextCompat.getDrawable(
            ctx,
            if (app.isUnknownSource) R.drawable.badge_danger else R.drawable.badge_other
        )
        h.badgeSource.setTextColor(
            ContextCompat.getColor(
                ctx, if (app.isUnknownSource) R.color.badge_danger_fg else R.color.badge_other_fg
            )
        )

        // 최근 설치 배지
        h.badgeRecent.visibility = if (app.isRecent) View.VISIBLE else View.GONE

        // 내 앱(보호) — 체크 불가
        h.badgeMine.visibility = if (app.isProtected) View.VISIBLE else View.GONE
        if (app.isProtected) app.selected = false
        h.itemView.alpha = if (app.isProtected) 0.55f else 1f

        h.check.setOnCheckedChangeListener(null)
        h.check.isChecked = app.selected
        h.check.isEnabled = !app.isProtected
        h.check.setOnCheckedChangeListener { _, checked ->
            app.selected = checked
            onSelectionChanged()
        }
        h.itemView.setOnClickListener { if (!app.isProtected) h.check.toggle() }
        h.itemView.setOnLongClickListener { onLongPress(app); true }
    }
}
