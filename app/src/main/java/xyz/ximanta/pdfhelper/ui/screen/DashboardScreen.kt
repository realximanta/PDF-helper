package xyz.ximanta.pdfhelper.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.ximanta.pdfhelper.R
import xyz.ximanta.pdfhelper.ui.catalog.ToolCatalog
import xyz.ximanta.pdfhelper.ui.model.Tool
import xyz.ximanta.pdfhelper.ui.model.Tool.Section
import xyz.ximanta.pdfhelper.ui.theme.PdfHelperTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onToolClick: (String) -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                actions = {
                    IconButton(onClick = onAboutClick) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.nav_about),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(
                        text = stringResource(R.string.dashboard_greeting),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(8.dp))
                    PrivacyBadge()
                    Spacer(Modifier.height(16.dp))
                }
            }

            val orderedSections = listOf(
                Section.ORGANIZE,
                Section.CONVERT,
                Section.EDIT,
                Section.SECURE,
                Section.COMING_SOON,
            )

            orderedSections.forEach { section ->
                val tools = ToolCatalog.bySection(section)
                if (tools.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(stringResource(section.labelRes))
                    }
                    items(items = tools, key = { it.id }) { tool ->
                        ToolCard(
                            tool = tool,
                            onClick = { if (tool.enabled) onToolClick(tool.id) },
                        )
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PrivacyBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.dashboard_privacy_badge),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier.padding(top = 12.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun ToolCard(
    tool: Tool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (tool.enabled) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentAlpha = if (tool.enabled) 1f else 0.55f

    Card(
        onClick = onClick,
        enabled = tool.enabled,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.05f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = containerColor,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f * contentAlpha),
                        shape = RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = stringResource(tool.titleRes),
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                )
            }

            Column {
                Text(
                    text = stringResource(tool.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                    maxLines = 2,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (tool.enabled) {
                        stringResource(tool.descRes)
                    } else {
                        stringResource(R.string.coming_soon)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    maxLines = 2,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardScreenPreview() {
    PdfHelperTheme {
        DashboardScreen(onToolClick = {}, onAboutClick = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF020617)
@Composable
private fun DashboardScreenDarkPreview() {
    PdfHelperTheme(darkTheme = true) {
        DashboardScreen(onToolClick = {}, onAboutClick = {})
    }
}

@Suppress("unused")
private val previewTint = Color.Unspecified
