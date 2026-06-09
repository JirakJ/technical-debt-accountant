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

    private val items = mutableListOf<TechDebtItem>()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it() }
    }

    fun getItems(): List<TechDebtItem> = items.toList()

    fun getActiveItems(): List<TechDebtItem> = items.filter { !it.isResolved }

    fun getResolvedItems(): List<TechDebtItem> = items.filter { it.isResolved }

    fun getItemById(id: String): TechDebtItem? = items.find { it.id == id }

    fun addItem(item: TechDebtItem): Boolean {
        items.add(item)
        save()
        notifyListeners()
        return true
    }

    fun updateItem(item: TechDebtItem) {
        val index = items.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            items[index] = item
            save()
            notifyListeners()
        }
    }

    fun removeItem(id: String) {
        items.removeIf { it.id == id }
        save()
        notifyListeners()
    }

    fun resolveItem(id: String) {
        val item = items.find { it.id == id } ?: return
        val resolved = item.copy(resolved = true, resolvedAt = System.currentTimeMillis())
        val index = items.indexOf(item)
        if (index >= 0) {
            items[index] = resolved
            save()
            notifyListeners()
        }
    }

    fun addWorkaroundLog(itemId: String, log: WorkaroundLog) {
        val item = items.find { it.id == itemId } ?: return
        item.workaroundLogs.add(log)
        val updated = item.copy(updatedAt = System.currentTimeMillis())
        val index = items.indexOf(item)
        if (index >= 0) {
            items[index] = updated
            save()
            notifyListeners()
        }
    }

    fun getItemsByCategory(category: DebtCategory): List<TechDebtItem> =
        items.filter { it.category == category && !it.isResolved }

    fun getItemsByPriority(priority: DebtPriority): List<TechDebtItem> =
        items.filter { it.priority == priority && !it.isResolved }

    fun getItemsByFile(filePath: String): List<TechDebtItem> =
        items.filter { it.filePath == filePath && !it.isResolved }

    fun load() {
        val file = getInventoryFile()
        if (!file.exists()) return

        try {
            val json = file.readText()
            val type = object : TypeToken<List<TechDebtItem>>() {}.type
            val loaded: List<TechDebtItem> = gson.fromJson(json, type) ?: emptyList()
            items.clear()
            items.addAll(loaded)
            notifyListeners()
        } catch (e: Exception) {
            LOG.warn("Failed to load debt inventory: ${e.message}", e)
        }
    }

    fun save() {
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

    fun getItemCount(): Int = items.size

    fun getActiveItemCount(): Int = items.count { !it.isResolved }

    companion object {
        private val LOG = Logger.getInstance(DebtInventoryStore::class.java)

        fun getInstance(project: Project): DebtInventoryStore =
            project.getService(DebtInventoryStore::class.java)
    }
}
