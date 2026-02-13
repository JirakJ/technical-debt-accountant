package com.jakubjirak.debtaccountant.git

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler

@Service(Service.Level.PROJECT)
class GitDebtLinker(private val project: Project) {

    fun getCurrentCommitHash(): String? {
        return try {
            val roots = GitUtil.getRepositoryManager(project).repositories
            val repo = roots.firstOrNull() ?: return null
            val handler = GitLineHandler(project, repo.root, GitCommand.LOG)
            handler.addParameters("--format=%H", "-1")
            val result = Git.getInstance().runCommand(handler)
            if (result.success()) result.output.firstOrNull()?.trim() else null
        } catch (e: Exception) {
            LOG.debug("Failed to get current commit hash: ${e.message}")
            null
        }
    }

    fun getFileLastAuthor(filePath: String): String? {
        return try {
            val roots = GitUtil.getRepositoryManager(project).repositories
            val repo = roots.firstOrNull() ?: return null
            val handler = GitLineHandler(project, repo.root, GitCommand.LOG)
            handler.addParameters("--format=%an", "-1", "--", filePath)
            val result = Git.getInstance().runCommand(handler)
            if (result.success()) result.output.firstOrNull()?.trim() else null
        } catch (e: Exception) {
            LOG.debug("Failed to get file author: ${e.message}")
            null
        }
    }

    fun getItemAge(commitHash: String): Long? {
        return try {
            val roots = GitUtil.getRepositoryManager(project).repositories
            val repo = roots.firstOrNull() ?: return null
            val handler = GitLineHandler(project, repo.root, GitCommand.LOG)
            handler.addParameters("--format=%ct", "-1", commitHash)
            val result = Git.getInstance().runCommand(handler)
            if (result.success()) {
                result.output.firstOrNull()?.trim()?.toLongOrNull()?.let {
                    System.currentTimeMillis() / 1000 - it
                }
            } else {
                null
            }
        } catch (e: Exception) {
            LOG.debug("Failed to get item age: ${e.message}")
            null
        }
    }

    companion object {
        private val LOG = Logger.getInstance(GitDebtLinker::class.java)

        fun getInstance(project: Project): GitDebtLinker =
            project.getService(GitDebtLinker::class.java)
    }
}
