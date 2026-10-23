package xyz.ximanta.pdfhelper.ui.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.BrandingWatermark
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tune
import xyz.ximanta.pdfhelper.R
import xyz.ximanta.pdfhelper.ui.model.Tool
import xyz.ximanta.pdfhelper.ui.model.Tool.Section
import xyz.ximanta.pdfhelper.ui.nav.Destinations

/**
 * The single source of truth for every tool shown on the Dashboard.
 * To add a tool: append here + add a route in Destinations + add the
 * screen in AppNavHost. Nothing else needs to change.
 */
object ToolCatalog {

    val all: List<Tool> = listOf(

        // ---------------- Organize ----------------
        Tool(
            id = Destinations.MERGE,
            titleRes = R.string.tool_merge,
            descRes = R.string.tool_merge_desc,
            icon = Icons.Outlined.Merge,
            section = Section.ORGANIZE,
        ),
        Tool(
            id = Destinations.SPLIT,
            titleRes = R.string.tool_split,
            descRes = R.string.tool_split_desc,
            icon = Icons.Outlined.CallSplit,
            section = Section.ORGANIZE,
        ),
        Tool(
            id = Destinations.ROTATE,
            titleRes = R.string.tool_rotate,
            descRes = R.string.tool_rotate_desc,
            icon = Icons.Outlined.Rotate90DegreesCw,
            section = Section.ORGANIZE,
        ),
        Tool(
            id = Destinations.REORDER,
            titleRes = R.string.tool_reorder,
            descRes = R.string.tool_reorder_desc,
            icon = Icons.Outlined.SwapVert,
            section = Section.ORGANIZE,
        ),
        Tool(
            id = Destinations.REMOVE_PAGES,
            titleRes = R.string.tool_remove_pages,
            descRes = R.string.tool_remove_pages_desc,
            icon = Icons.Outlined.Delete,
            section = Section.ORGANIZE,
        ),

        // ---------------- Convert ----------------
        Tool(
            id = Destinations.IMAGES_TO_PDF,
            titleRes = R.string.tool_images_to_pdf,
            descRes = R.string.tool_images_to_pdf_desc,
            icon = Icons.Outlined.Image,
            section = Section.CONVERT,
        ),
        Tool(
            id = Destinations.TEXT_TO_PDF,
            titleRes = R.string.tool_text_to_pdf,
            descRes = R.string.tool_text_to_pdf_desc,
            icon = Icons.Outlined.Article,
            section = Section.CONVERT,
        ),
        Tool(
            id = Destinations.PDF_TO_IMAGES,
            titleRes = R.string.tool_pdf_to_images,
            descRes = R.string.tool_pdf_to_images_desc,
            icon = Icons.Outlined.PhotoLibrary,
            section = Section.CONVERT,
        ),

        // ---------------- Edit ----------------
        Tool(
            id = Destinations.ADD_CONTENT,
            titleRes = R.string.tool_add_content,
            descRes = R.string.tool_add_content_desc,
            icon = Icons.Outlined.Edit,
            section = Section.EDIT,
        ),
        Tool(
            id = Destinations.WATERMARK,
            titleRes = R.string.tool_watermark,
            descRes = R.string.tool_watermark_desc,
            icon = Icons.Outlined.BrandingWatermark,
            section = Section.EDIT,
        ),
        Tool(
            id = Destinations.PAGE_NUMBERS,
            titleRes = R.string.tool_page_numbers,
            descRes = R.string.tool_page_numbers_desc,
            icon = Icons.Outlined.FormatListNumbered,
            section = Section.EDIT,
        ),

        // ---------------- Secure ----------------
        Tool(
            id = Destinations.PASSWORD,
            titleRes = R.string.tool_password,
            descRes = R.string.tool_password_desc,
            icon = Icons.Outlined.Lock,
            section = Section.SECURE,
        ),

        // ---------------- Coming soon (Phase 2) ----------------
        Tool(
            id = Destinations.EDIT_PDF,
            titleRes = R.string.tool_edit_pdf,
            descRes = R.string.tool_edit_pdf_desc,
            icon = Icons.Outlined.Tune,
            section = Section.COMING_SOON,
            enabled = false,
        ),
    )

    fun byId(id: String): Tool? = all.firstOrNull { it.id == id }

    fun bySection(section: Section): List<Tool> = all.filter { it.section == section }
}
