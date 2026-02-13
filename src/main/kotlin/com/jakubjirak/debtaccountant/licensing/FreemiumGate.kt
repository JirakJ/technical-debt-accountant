package com.jakubjirak.debtaccountant.licensing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service

@Service(Service.Level.APP)
class FreemiumGate {

    enum class LicenseStatus {
        FREE, TRIAL, PRO, ENTERPRISE, EXPIRED
    }

    @Suppress("UNUSED_PARAMETER")
    fun isPaidFeatureAvailable(featureId: String): Boolean {
        val status = getLicenseStatus()
        return status == LicenseStatus.PRO ||
            status == LicenseStatus.ENTERPRISE ||
            status == LicenseStatus.TRIAL
    }

    fun isEnterpriseFeatureAvailable(): Boolean {
        return getLicenseStatus() == LicenseStatus.ENTERPRISE
    }

    fun getLicenseStatus(): LicenseStatus {
        // TODO: Integrate with JetBrains Marketplace Licensing API
        return LicenseStatus.FREE
    }

    fun canAddItem(currentCount: Int): Boolean {
        return when (getLicenseStatus()) {
            LicenseStatus.FREE, LicenseStatus.EXPIRED -> currentCount < MAX_FREE_ITEMS
            LicenseStatus.TRIAL, LicenseStatus.PRO, LicenseStatus.ENTERPRISE -> true
        }
    }

    fun getMaxItems(): Int {
        return when (getLicenseStatus()) {
            LicenseStatus.FREE, LicenseStatus.EXPIRED -> MAX_FREE_ITEMS
            LicenseStatus.TRIAL, LicenseStatus.PRO, LicenseStatus.ENTERPRISE -> Int.MAX_VALUE
        }
    }

    companion object {
        const val MAX_FREE_ITEMS = 50

        const val FEATURE_DOLLAR_VALUATION = "dollar_valuation"
        const val FEATURE_ROI = "roi_calculator"
        const val FEATURE_BURNDOWN = "burndown"
        const val FEATURE_EXPORT = "export"
        const val FEATURE_INTEREST = "interest_rate"
        const val FEATURE_TEAM = "team_aggregation"
        const val FEATURE_JIRA = "jira_integration"
        const val FEATURE_TRENDS = "trend_analysis"

        fun getInstance(): FreemiumGate =
            ApplicationManager.getApplication().getService(FreemiumGate::class.java)
    }
}
