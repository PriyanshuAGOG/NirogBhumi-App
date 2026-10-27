package com.nirogbhumi.app.health.domain

import java.util.Locale

/**
 * The file type sent with an upload. Storage rules accept only photos and PDFs for a member's own files, and they judge the
 * type the app *declares*. A file the app generates itself (the Health File PDF) or one picked from some apps arrives with no
 * type, which used to be sent as "application/octet-stream" and refused with "User does not have permission to access this object".
 * So the type is always decided here: what Android reports, else the file name, else a sensible default for the folder.
 */
object UploadTypes {
    private val allowed = Regex("(image/(png|jpe?g|webp|heic|heif|gif)|application/pdf)")
    private val byExtension = mapOf(
        "pdf" to "application/pdf", "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
        "webp" to "image/webp", "heic" to "image/heic", "heif" to "image/heif", "gif" to "image/gif",
    )
    private val pdfFolders = setOf("health-file", "reports")

    /** Whether the Storage rules would accept this type for a member's own file. */
    fun isAllowed(type: String): Boolean = allowed.matches(type.lowercase(Locale.ROOT))

    fun resolve(reportedType: String?, fileName: String?, folder: String): String {
        val reported = reportedType?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (reported.isNotEmpty() && reported != "application/octet-stream" && reported != "*/*") return reported
        val ext = fileName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
        byExtension[ext]?.let { return it }
        return if (folder in pdfFolders) "application/pdf" else "image/jpeg"
    }

    /** What to tell the member when the file they picked is neither a photo nor a PDF. */
    const val NOT_SUPPORTED_MESSAGE = "That file type isn't supported. Please choose a photo (JPG, PNG, WebP or HEIC) or a PDF."
}
