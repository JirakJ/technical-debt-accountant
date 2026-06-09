package com.jakubjirak.debtaccountant.scanner

/**
 * A debt-marker comment found in source code by [DebtCommentScanner].
 *
 * @property filePath path of the file the comment was found in (relative to the scan root when scanning a tree)
 * @property line 1-based line number of the marker
 * @property marker the marker keyword that matched (e.g. `FIXME`)
 * @property text the comment text following the marker, trimmed; may be empty
 */
data class ScannedComment(
    val filePath: String,
    val line: Int,
    val marker: DebtMarker,
    val text: String
)
