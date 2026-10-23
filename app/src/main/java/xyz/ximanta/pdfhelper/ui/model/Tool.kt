package xyz.ximanta.pdfhelper.ui.model

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import xyz.ximanta.pdfhelper.R

/**
 * Everything the Dashboard needs to render a tool card.
 *
 * @param id        stable route key (matches Destinations constants)
 * @param titleRes  string resource for the card title
 * @param descRes   string resource for the card description
 * @param icon      material icon for the card
 * @param section   dashboard group this tool belongs to
 * @param enabled   false → shown with "Coming soon" badge, not clickable
 */
data class Tool(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val icon: ImageVector,
    val section: Section,
    val enabled: Boolean = true,
) {
    enum class Section(@StringRes val labelRes: Int) {
        ORGANIZE(R.string.dashboard_section_organize),
        CONVERT(R.string.dashboard_section_convert),
        EDIT(R.string.dashboard_section_edit),
        SECURE(R.string.dashboard_section_secure),
        COMING_SOON(R.string.dashboard_section_coming_soon),
    }
}
