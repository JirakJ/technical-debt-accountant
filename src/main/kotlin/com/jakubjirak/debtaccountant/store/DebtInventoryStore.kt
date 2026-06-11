package com.jakubjirak.debtaccountant.store

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.jakubjirak.debtaccountant.model.DebtCategory
import com.jakubjirak.debtaccountant.model.DebtPriority
import com.jakubjirak.debtaccountant.model.TechDebtItem
import com.jakubjirak.debtaccountant.model.WorkaroundLog
import java.io.File

@Service(Service.Level.PROJECT)
class DebtInventoryStore(private val project: Project) {

    // Guards all access to [items]. Mutations are triggered from background tasks
    // (scan import, SLA escalation) while reads happen on the EDT (tool window,
    // status bar widget), so every read and write snapshots under this lock to
    // avoid ConcurrentModificationException and torn reads. Listener callbacks
    // run outside the lock so they can safely call back into the store.
    private val lock = Any()
    private val items = mutableListOf<TechDebtItem>()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it() }
    }

    fun getItems(): List<TechDebtItem> = synchronized(lock) { items.toList() }

    fun getActiveItems(): List<TechDebtItem> = synchronized(lock) { items.filter { !it.isResolved } }

    fun getResolvedItems(): List<TechDebtItem> = synchronized(lock) { items.filter { it.isResolved } }

    fun getItemById(id: String): TechDebtItem? = synchronized(lock) { items.find { it.id == id } }

    fun addItem(item: TechDebtItem): Boolean {
        synchronized(lock) {
            items.add(item)
            persist()
        }
        notifyListeners()
        return true
    }

    fun updateItem(item: TechDebtItem) {
        val changed = synchronized(lock) {
            val index = items.indexOfFirst { it.id == item.id }
            if (index >= 0) {
                items[index] = item
                persist()
                true
            } else {
                false
            }
        }
        if (changed) notifyListeners()
    }

    fun removeItem(id: String) {
        val changed = synchronized(lock) {
            val removed = items.removeIf { it.id == id }
            if (removed) persist()
            removed
        }
        if (changed) notifyListeners()
    }

    fun resolveItem(id: String) {
        val changed = synchronized(lock) {
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) {
                items[index] = items[index].copy(resolved = true, resolvedAt = System.currentTimeMillis())
                persist()
                true
            } else {
                false
            }
        }
        if (changed) notifyListeners()
    }

    fun addWorkaroundLog(itemId: String, log: WorkaroundLog) {
        val changed = synchronized(lock) {
            val index = items.indexOfFirst { it.id == itemId }
            if (index >= 0) {
                val item = items[index]
                item.workaroundLogs.add(log)
                items[index] = item.copy(updatedAt = System.currentTimeMillis())
                persist()
                true
            } else {
                false
            }
        }
        if (changed) notifyListeners()
    }

    fun getItemsByCategory(category: DebtCategory): List<TechDebtItem> =
        synchronized(lock) { items.filter { it.category == category && !it.isResolved } }

    fun getItemsByPriority(priority: DebtPriority): List<TechDebtItem> =
        synchronized(lock) { items.filter { it.priority == priority && !it.isResolved } }

    fun getItemsByFile(filePath: String): List<TechDebtItem> =
        synchronized(lock) { items.filter { it.filePath == filePath && !it.isResolved } }

    fun load() {
        val file = getInventoryFile()
        if (!file.exists()) return

        val loadedOk = synchronized(lock) {
            try {
                val json = file.readText()
                val type = object : TypeToken<List<TechDebtItem>>() {}.type
                val loaded: List<TechDebtItem> = gson.fromJson(json, type) ?: emptyList()
                items.clear()
                items.addAll(loaded)
                true
            } catch (e: Exception) {
                LOG.warn("Failed to load debt inventory: ${e.message}", e)
                false
            }
        }
        if (loadedOk) notifyListeners()
    }

    fun save(): Unit = synchronized(lock) { persist() }

    /** Writes the current inventory to disk. Caller must hold [lock]. */
    private fun persist() {
        try {
            val file = getInventoryFile()
            file.parentFile?.mkdirs()
            file.writeText(gson.toJson(items))
        } catch (e: Exception) {
            LOG.warn("Failed to save debt inventory: ${e.message}", e)
        }
    }

    fun getInventoryFile(): File {
        val basePath = project.basePath ?: return File(".tech-debt/inventory.json")
        return File(basePath, ".tech-debt/inventory.json")
    }

    fun getItemCount(): Int = synchronized(lock) { items.size }

    fun getActiveItemCount(): Int = synchronized(lock) { items.count { !it.isResolved } }

    companion object {
        private val LOG = Logger.getInstance(DebtInventoryStore::class.java)

        fun getInstance(project: Project): DebtInventoryStore =
            project.getService(DebtInventoryStore::class.java)
    }
}
