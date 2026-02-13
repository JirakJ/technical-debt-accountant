package com.jakubjirak.debtaccountant.tracker

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.jakubjirak.debtaccountant.model.WorkaroundLog
import com.jakubjirak.debtaccountant.store.DebtInventoryStore

@Service(Service.Level.PROJECT)
class WorkaroundTracker(private val project: Project) {

    private var activeItemId: String? = null
    private var startTimeMs: Long = 0

    fun startTimer(itemId: String) {
        activeItemId = itemId
        startTimeMs = System.currentTimeMillis()
        LOG.info("Timer started for debt item: $itemId")
    }

    fun stopTimer(note: String = ""): WorkaroundLog? {
        val itemId = activeItemId ?: return null
        val elapsed = System.currentTimeMillis() - startTimeMs
        val minutes = (elapsed / 60000).toInt().coerceAtLeast(1)

        val log = WorkaroundLog(
            durationMinutes = minutes,
            note = note
        )

        DebtInventoryStore.getInstance(project).addWorkaroundLog(itemId, log)
        activeItemId = null
        startTimeMs = 0
        LOG.info("Timer stopped for $itemId: $minutes minutes")
        return log
    }

    fun logManual(itemId: String, minutes: Int, note: String = "") {
        val log = WorkaroundLog(
            durationMinutes = minutes,
            note = note
        )
        DebtInventoryStore.getInstance(project).addWorkaroundLog(itemId, log)
    }

    fun isTimerRunning(): Boolean = activeItemId != null

    fun getActiveItemId(): String? = activeItemId

    fun getElapsedMinutes(): Int {
        if (!isTimerRunning()) return 0
        return ((System.currentTimeMillis() - startTimeMs) / 60000).toInt()
    }

    companion object {
        private val LOG = Logger.getInstance(WorkaroundTracker::class.java)

        fun getInstance(project: Project): WorkaroundTracker =
            project.getService(WorkaroundTracker::class.java)
    }
}
