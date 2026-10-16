package com.example.employeepos2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import android.widget.ImageView
import android.widget.LinearLayout
import com.bumptech.glide.Glide
import org.json.JSONObject
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray

/** Team tab: everyone at the company, grouped by role, with badges and the year they joined. */
class TeamFragment : Fragment(R.layout.fragment_team) {

    private data class Person(
        val name: String,
        val role: String,
        val isLead: Boolean,
        val has5Star: Boolean,
        val hasNoRecalls: Boolean,
        val joinYear: Int?,
        val tenureTier: Int
    )

    private sealed class Row {
        data class Section(val title: String) : Row()
        data class Member(val person: Person) : Row()
    }

    private lateinit var rv: RecyclerView
    private lateinit var loader: View
    private lateinit var errorBox: View
    private lateinit var errorText: TextView
    private val adapter = TeamAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rv = view.findViewById(R.id.rvTeam)
        loader = view.findViewById(R.id.teamLoader)
        errorBox = view.findViewById(R.id.teamError)
        errorText = view.findViewById(R.id.tvTeamError)

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        view.findViewById<View>(R.id.btnTeamRetry).setOnClickListener { load() }
        load()

        view.findViewById<View>(R.id.tabRoster).setOnClickListener { showTab(true) }
        view.findViewById<View>(R.id.tabBoards).setOnClickListener {
            showTab(false)
            if (!boardsRequested) loadBoards()
        }
        view.findViewById<View>(R.id.cbRetry).setOnClickListener { loadCallbacks() }
        view.findViewById<View>(R.id.invRetry).setOnClickListener { loadStrikes() }
    }

    // ── Tabs ──────────────────────────────────────────────────────────────────

    private var boardsRequested = false

    private fun showTab(roster: Boolean) {
        val v = view ?: return
        val show = v.findViewById<View>(if (roster) R.id.paneRoster else R.id.paneBoards)
        val hide = v.findViewById<View>(if (roster) R.id.paneBoards else R.id.paneRoster)
        if (show.visibility != View.VISIBLE) {
            hide.visibility = View.GONE
            show.visibility = View.VISIBLE
            if (motionOn()) {
                show.alpha = 0f
                show.translationX = (if (roster) -24 else 24) * resources.displayMetrics.density
                show.animate().alpha(1f).translationX(0f).setDuration(220).start()
            }
            // Replay the podium entrance each time the tab is opened.
            if (!roster) {
                listOf("cb", "inv").forEach { prefix ->
                    val pod = v.findViewById<LinearLayout>(idOf(prefix, "Podium"))
                    if (pod.visibility == View.VISIBLE) animatePodium(pod)
                }
            }
        }
        // Underline slides in under the selected tab.
        v.findViewById<View>(if (roster) R.id.tabRosterLine else R.id.tabBoardsLine).let { line ->
            if (motionOn()) { line.scaleX = 0.2f; line.animate().scaleX(1f).setDuration(220).start() }
        }
        fun style(textId: Int, lineId: Int, on: Boolean) {
            v.findViewById<TextView>(textId).setTextColor(Color.parseColor(if (on) "#CC0007" else "#6B7280"))
            v.findViewById<View>(lineId).visibility = if (on) View.VISIBLE else View.INVISIBLE
        }
        style(R.id.tabRosterText, R.id.tabRosterLine, roster)
        style(R.id.tabBoardsText, R.id.tabBoardsLine, !roster)
    }

    // ── Leaderboards ──────────────────────────────────────────────────────────

    private data class Entry(val name: String, val imageUrl: String, val main: String, val sub: String)

    private fun loadBoards() {
        boardsRequested = true
        loadCallbacks()
        loadStrikes()
    }

    private fun getJson(path: String): Result<JSONObject> = try {
        val request = Request.Builder().url("https://integrity-backend-cr02.onrender.com$path").build()
        ApiClient.client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) Result.failure(Exception("Server error (${resp.code})."))
            else Result.success(JSONObject(body))
        }
    } catch (e: Exception) {
        Result.failure(Exception("No connection. Check your internet and try again."))
    }

    private fun loadCallbacks() {
        setBoardState("cb", loading = true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                getJson("/api/tech_stats?year=${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)}").mapCatching { json ->
                    val techs = json.optJSONArray("techs") ?: JSONArray()
                    (0 until techs.length()).map { techs.getJSONObject(it) }
                        .map { t ->
                            val jobs = t.optInt("completedJobsYtd", 0)
                            val cb = t.optInt("callbackCount", 0)
                            Triple(t, jobs, cb)
                        }
                        .filter { it.second > 0 }
                        .sortedWith(compareBy<Triple<JSONObject, Int, Int>> { it.third.toDouble() / it.second }.thenByDescending { it.second })
                        .map { (t, jobs, cb) ->
                            val rate = cb.toDouble() / jobs * 100
                            Entry(
                                t.optString("name"), t.optString("avatarUrl"),
                                String.format(java.util.Locale.US, "%.1f%%", rate),
                                "$cb ${if (cb == 1) "callback" else "callbacks"}"
                            )
                        }
                }
            }
            if (!isAdded) return@launch
            renderBoard("cb", result, "No completed jobs yet this year.")
        }
    }

    private fun loadStrikes() {
        setBoardState("inv", loading = true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                getJson("/api/dashboards/inventory").mapCatching { json ->
                    val arr = json.optJSONArray("topStrikeTechs") ?: JSONArray()
                    (0 until arr.length()).map { arr.getJSONObject(it) }.map { t ->
                        val n = t.optInt("strikeCount", 0)
                        Entry(t.optString("name"), t.optString("imageUrl"), "$n", if (n == 1) "strike" else "strikes")
                    }
                }
            }
            if (!isAdded) return@launch
            renderBoard("inv", result, "No inventory strike data recorded.")
        }
    }

    private fun idOf(prefix: String, suffix: String): Int =
        resources.getIdentifier(prefix + suffix, "id", requireContext().packageName)

    private fun setBoardState(prefix: String, loading: Boolean, message: String? = null) {
        val v = view ?: return
        v.findViewById<View>(idOf(prefix, "Loader")).visibility = if (loading) View.VISIBLE else View.GONE
        v.findViewById<View>(idOf(prefix, "Message")).visibility = if (message != null) View.VISIBLE else View.GONE
        if (message != null) v.findViewById<TextView>(idOf(prefix, "MessageText")).text = message
        if (loading || message != null) {
            v.findViewById<View>(idOf(prefix, "Podium")).visibility = View.GONE
        }
    }

    private fun renderBoard(prefix: String, result: Result<List<Entry>>, emptyText: String) {
        val v = view ?: return
        result.onFailure { setBoardState(prefix, false, it.message ?: "Couldn’t load this leaderboard."); return }
        val entries = result.getOrThrow()
        if (entries.isEmpty()) { setBoardState(prefix, false, emptyText); return }
        setBoardState(prefix, false)

        val podium = v.findViewById<LinearLayout>(idOf(prefix, "Podium"))
        podium.removeAllViews()
        podium.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(requireContext())
        val dp = resources.displayMetrics.density
        // Visual order: 2nd, 1st, 3rd.
        for (rank in listOf(2, 1, 3)) {
            val e = entries.getOrNull(rank - 1)
            val step = inflater.inflate(R.layout.item_podium_step, podium, false)
            if (e == null) { step.visibility = View.INVISIBLE; podium.addView(step); continue }
            val color = Color.parseColor(when (rank) { 1 -> "#F59E0B"; 2 -> "#94A3B8"; else -> "#B45309" })
            val size = ((if (rank == 1) 96 else 76) * dp).toInt()
            step.findViewById<View>(R.id.podiumAvatarBox).layoutParams = LinearLayout.LayoutParams(size, size)
            bindAvatar(step, e)
            step.findViewById<TextView>(R.id.podiumName).text = e.name.split(" ").first()
            step.findViewById<TextView>(R.id.podiumStat).text = "${e.main} · ${e.sub}"
            step.findViewById<TextView>(R.id.podiumStep).apply {
                text = when (rank) { 1 -> "1st"; 2 -> "2nd"; else -> "3rd" }
                setTextColor(color)
                setBackgroundColor(Color.argb(46, Color.red(color), Color.green(color), Color.blue(color)))
                layoutParams = layoutParams.apply { height = ((when (rank) { 1 -> 92; 2 -> 66; else -> 46 }) * dp).toInt() }
            }
            podium.addView(step)
        }

        animatePodium(podium)
    }

    // ── Motion ────────────────────────────────────────────────────────────────

    // Bobbing animators per podium, so starting one podium's motion never cancels the other's.
    private val floaters = mutableMapOf<LinearLayout, MutableList<android.animation.Animator>>()

    private fun motionOn(): Boolean =
        android.provider.Settings.Global.getFloat(requireContext().contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

    /** Steps rise one after another (3rd, 2nd, 1st), then names and photos pop in; 1st place floats gently. */
    private fun animatePodium(podium: LinearLayout) {
        floaters.remove(podium)?.forEach { it.cancel() }
        if (!motionOn()) return
        val dp = resources.displayMetrics.density

        // Steps are laid out in visual order 2nd, 1st, 3rd; animate in rank order 3, 2, 1.
        val rankDelay = mapOf(2 to 0L, 0 to 220L, 1 to 440L)
        for (idx in listOf(2, 0, 1)) {
            val step = podium.getChildAt(idx) ?: continue
            if (step.visibility != View.VISIBLE) continue
            val block = step.findViewById<TextView>(R.id.podiumStep)
            val avatarBox = step.findViewById<View>(R.id.podiumAvatarBox)
            val name = step.findViewById<View>(R.id.podiumName)
            val stat = step.findViewById<View>(R.id.podiumStat)
            val delay = rankDelay.getValue(idx)
            val target = block.layoutParams.height

            block.layoutParams = block.layoutParams.apply { height = 0 }
            block.alpha = 0f
            listOf(avatarBox, name, stat).forEach { it.alpha = 0f; it.translationY = 28 * dp }

            android.animation.ValueAnimator.ofInt(0, target).apply {
                duration = 650
                startDelay = delay
                interpolator = android.view.animation.OvershootInterpolator(1.1f)
                addUpdateListener {
                    block.layoutParams = block.layoutParams.apply { height = it.animatedValue as Int }
                    block.alpha = (it.animatedFraction * 3f).coerceAtMost(1f)
                }
                start()
            }
            var t = delay + 380
            listOf(avatarBox, name, stat).forEach { v2 ->
                v2.animate().alpha(1f).translationY(0f).setStartDelay(t).setDuration(420)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.4f)).start()
                t += 70
            }

            // Every photo bobs, each a little out of step with the others so they don't move in lockstep.
            val bob = android.animation.ObjectAnimator.ofFloat(avatarBox, View.TRANSLATION_Y, 0f, -5 * dp, 0f).apply {
                duration = 2400L + idx * 250L
                startDelay = delay + 1300 + idx * 180L
                repeatCount = android.animation.ValueAnimator.INFINITE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                start()
            }
            floaters.getOrPut(podium) { mutableListOf() }.add(bob)
        }
    }

    override fun onDestroyView() {
        floaters.values.flatten().forEach { it.cancel() }
        floaters.clear()
        super.onDestroyView()
    }

    private fun bindAvatar(step: View, e: Entry) {
        val initials = e.name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
        step.findViewById<TextView>(R.id.podiumInitials).text = initials
        val iv = step.findViewById<ShapeableImageView>(R.id.podiumAvatar)
        val resId = resources.getIdentifier(e.name.lowercase().trim().replace(" ", "_"), "drawable", requireContext().packageName)
        when {
            resId != 0 -> { iv.setImageResource(resId); iv.visibility = View.VISIBLE }
            e.imageUrl.startsWith("http") -> { Glide.with(this).load(e.imageUrl).into(iv); iv.visibility = View.VISIBLE }
            else -> iv.visibility = View.GONE
        }
    }

    private fun load() {
        loader.visibility = View.VISIBLE
        errorBox.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url("https://integrity-backend-cr02.onrender.com/team_roster")
                        .build()
                    ApiClient.client.newCall(request).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        when {
                            resp.code == 404 -> Result.failure(Exception("The team list isn’t available yet. Try again after the next update."))
                            !resp.isSuccessful -> Result.failure(Exception("Server error (${resp.code})."))
                            else -> Result.success(parse(body))
                        }
                    }
                } catch (e: Exception) {
                    Result.failure(Exception("No connection. Check your internet and try again."))
                }
            }
            if (!isAdded) return@launch
            loader.visibility = View.GONE
            result.onSuccess { people ->
                if (people.isEmpty()) {
                    errorText.text = "No team members found."
                    errorBox.visibility = View.VISIBLE
                } else {
                    adapter.submit(group(people))
                }
            }.onFailure {
                errorText.text = it.message ?: "Couldn’t load the team."
                errorBox.visibility = View.VISIBLE
            }
        }
    }

    private fun parse(body: String): List<Person> {
        val arr = JSONArray(body)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Person(
                name = o.optString("name"),
                role = o.optString("role"),
                isLead = o.optBoolean("isLead"),
                has5Star = o.optBoolean("has5Star"),
                hasNoRecalls = o.optBoolean("hasNoRecalls"),
                joinYear = o.optInt("joinYear", 0).takeIf { it > 0 },
                tenureTier = o.optInt("tenureTier", 0)
            )
        }
    }

    private fun sectionOf(role: String): String {
        val r = role.lowercase()
        return when {
            r.contains("tech") -> "FIELD TEAM"
            r.contains("warehouse") -> "WAREHOUSE"
            else -> "OFFICE" // admin, owner, managers, office staff and sales
        }
    }

    // Owner/admins first, then managers, then everyone else, each alphabetical.
    private fun officeRank(role: String): Int {
        val r = role.lowercase()
        return when {
            r.contains("admin") || r.contains("owner") -> 0
            r.contains("manager") -> 1
            else -> 2
        }
    }

    private fun group(people: List<Person>): List<Row> {
        val order = listOf("OFFICE", "FIELD TEAM", "WAREHOUSE")
        val rows = mutableListOf<Row>()
        val bySection = people.groupBy { sectionOf(it.role) }
        for (title in order) {
            val members = bySection[title] ?: continue
            rows.add(Row.Section(title))
            val sorted = if (title == "OFFICE") members.sortedWith(compareBy<Person> { officeRank(it.role) }.thenBy { it.name.lowercase() })
            else members.sortedWith(compareByDescending<Person> { it.isLead }.thenBy { it.name.lowercase() })
            sorted.forEach { rows.add(Row.Member(it)) }
        }
        return rows
    }

    private fun prettyRole(role: String): String =
        role.trim().lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    private inner class TeamAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var rows = listOf<Row>()

        private var animated = mutableSetOf<Int>()

        fun submit(list: List<Row>) {
            rows = list
            animated = mutableSetOf()
            notifyDataSetChanged()
        }

        override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
            val pos = holder.adapterPosition
            if (pos < 0 || pos > 14 || !animated.add(pos) || !motionOn()) return
            val v = holder.itemView
            v.alpha = 0f
            v.translationY = 36 * resources.displayMetrics.density
            v.animate().alpha(1f).translationY(0f).setStartDelay(pos * 55L).setDuration(380)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f)).start()
        }

        override fun getItemViewType(position: Int) = if (rows[position] is Row.Section) 0 else 1
        override fun getItemCount() = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 0) {
                object : RecyclerView.ViewHolder(inflater.inflate(R.layout.item_team_section, parent, false)) {}
            } else {
                object : RecyclerView.ViewHolder(inflater.inflate(R.layout.item_team_member, parent, false)) {}
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val v = holder.itemView
            v.animate().cancel()
            v.alpha = 1f
            v.translationY = 0f
            when (val row = rows[position]) {
                is Row.Section -> (v as TextView).text = row.title
                is Row.Member -> {
                    val p = row.person
                    v.findViewById<TextView>(R.id.techNameText).text = p.name
                    val since = p.joinYear?.let { " · Since $it" } ?: ""
                    v.findViewById<TextView>(R.id.techRoleText).text = prettyRole(p.role) + since

                    val initials = p.name.split(" ").filter { it.isNotBlank() }.take(2)
                        .joinToString("") { it.first().uppercase() }
                    val tvInitials = v.findViewById<TextView>(R.id.tvInitials)
                    val iv = v.findViewById<ShapeableImageView>(R.id.ivAvatar)
                    val resId = v.resources.getIdentifier(
                        p.name.lowercase().trim().replace(" ", "_"), "drawable", v.context.packageName
                    )
                    tvInitials.text = initials
                    if (resId != 0) {
                        iv.setImageResource(resId)
                        iv.visibility = View.VISIBLE
                    } else {
                        iv.visibility = View.GONE
                    }

                    // Techs always show every achievement (faded until earned); others only what they have.
                    val isTech = p.role.lowercase().contains("tech")
                    val anyEarned = p.isLead || p.has5Star || p.hasNoRecalls || p.tenureTier > 0
                    v.findViewById<View>(R.id.badgeRow).visibility = if (isTech || anyEarned) View.VISIBLE else View.GONE
                    fun show(id: Int, earned: Boolean) {
                        v.findViewById<View>(id).apply {
                            visibility = if (isTech || earned) View.VISIBLE else View.GONE
                            alpha = if (earned) 1f else 0.22f
                        }
                    }
                    show(R.id.badge5Star, p.has5Star)
                    show(R.id.badgeNoRecalls, p.hasNoRecalls)
                    show(R.id.badgeLead, p.isLead)
                    val tenureIcon = when (p.tenureTier.coerceIn(0, 5)) {
                        0, 1 -> R.drawable.ic_badge_1
                        2 -> R.drawable.ic_badge_2
                        3 -> R.drawable.ic_badge_3
                        4 -> R.drawable.ic_badge_4
                        else -> R.drawable.ic_badge_5
                    }
                    v.findViewById<ImageView>(R.id.badgeTenure).setImageResource(tenureIcon)
                    show(R.id.badgeTenure, p.tenureTier > 0)
                }
            }
        }
    }
}
