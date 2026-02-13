package com.jakubjirak.debtaccountant

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.jakubjirak.debtaccountant.store.DebtInventoryStore

class DebtAccountantStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val store = DebtInventoryStore.getInstance(project)
        store.load()
        LOG.info("Technical Debt Accountant: loaded ${store.getItemCount()} items for ${project.name}")
    }

    companion object {
        private val LOG = Logger.getInstance(DebtAccountantStartupActivity::class.java)
    }
}
