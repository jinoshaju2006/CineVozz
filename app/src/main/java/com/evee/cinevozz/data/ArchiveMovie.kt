package com.evee.cinevozz.data

data class ArchiveMovie(
    val identifier: String,
    val title: String,
    val year: String,
    val description: String,
    val licenseUrl: String,
    val rights: String
) {
    val isDownloadAllowed: Boolean
        get() = isApprovedLicense(licenseUrl, rights)

    companion object {
        fun isApprovedLicense(url: String, rights: String): Boolean {
            val u = url.lowercase()
            val r = rights.lowercase()
            return u.contains("creativecommons.org/licenses/by/") ||
                u.contains("creativecommons.org/licenses/by-sa/") ||
                u.contains("creativecommons.org/publicdomain/zero/") ||
                u.contains("creativecommons.org/publicdomain/mark/") ||
                r.contains("public domain")
        }
    }
}
