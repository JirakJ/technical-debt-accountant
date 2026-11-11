package com.jakubjirak.debtaccountant.tracker

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.jakubjirak.debtaccountant.model.WorkaroundLog
import com.jakubjirak.debtaccountant.store.DebtInventoryStore

/** Records workaround time against debt items in the project inventory. */
@Service(Service.Level.PROJECT)
class WorkaroundTracker(private val project: Project) {

    /** Appends a [minutes]-long workaround log with an optional [note] to the item [itemId]. */
    fun logManual(itemId: String, minutes: Int, note: String = "") {
        require(minutes > 0) { "minutes must be > 0" }
        val log = WorkaroundLog(
            durationMinutes = minutes,
            note = note
        )
        DebtInventoryStore.getInstance(project).addWorkaroundLog(itemId, log)
    }

    companion object {
        fun getInstance(project: Project): WorkaroundTracker =
            project.getService(WorkaroundTracker::class.java)
    }
}
