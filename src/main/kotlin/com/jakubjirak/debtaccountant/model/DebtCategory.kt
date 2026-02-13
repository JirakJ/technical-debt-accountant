package com.jakubjirak.debtaccountant.model

enum class DebtCategory(val displayName: String, val description: String) {
    CODE_SMELL("Code Smell", "Poor code quality requiring cleanup"),
    MISSING_TESTS("Missing Tests", "Insufficient test coverage"),
    OUTDATED_DEPENDENCY("Outdated Dependency", "Dependency needing upgrade"),
    SECURITY_VULNERABILITY("Security Vulnerability", "Known security issue"),
    PERFORMANCE_ISSUE("Performance Issue", "Code causing performance degradation"),
    DOCUMENTATION_GAP("Documentation Gap", "Missing or outdated documentation"),
    ARCHITECTURE_VIOLATION("Architecture Violation", "Code violating architectural constraints"),
    HARDCODED_VALUE("Hardcoded Value", "Magic numbers or hardcoded configuration"),
    COPY_PASTE_CODE("Copy-Paste Code", "Duplicated code requiring extraction")
}
