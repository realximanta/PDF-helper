package xyz.ximanta.pdfhelper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import xyz.ximanta.pdfhelper.R
import xyz.ximanta.pdfhelper.ui.catalog.ToolCatalog
import xyz.ximanta.pdfhelper.ui.nav.Destinations
import xyz.ximanta.pdfhelper.ui.screen.AboutScreen
import xyz.ximanta.pdfhelper.ui.screen.DashboardScreen
import xyz.ximanta.pdfhelper.ui.screen.LoadingScreen
import xyz.ximanta.pdfhelper.ui.screen.tools.AddContentScreen
import xyz.ximanta.pdfhelper.ui.screen.tools.ImagesToPdfScreen
import xyz.ximanta.pdfhelper.ui.screen.tools.MergeScreen
import xyz.ximanta.pdfhelper.ui.screen.tools.PageNumbersScreen
import xyz.ximanta.pdfhelper.ui.screen.tools.PasswordScreen
import xyz.ximanta.pdfhelper.ui.screen.tools.PdfToImagesScreen
import xyz.ximanta.pdfhelper.ui.screen.tools.RemovePagesScreen
import xyz.ximanta.pdfhelper.ui.screen.tools.ReorderScreen
import xyz.ximanta.pdfhelper.ui.screen.tools.RotateScreen
import xyz.ximanta.pdfhelper.ui.screen.tools.SplitScreen
import xyz.ximanta.pdfhelper.ui.screen.tools.TextToPdfScreen
import xyz.ximanta.pdfhelper.ui.screen.tools.WatermarkScreen

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.LOADING,
        modifier = modifier,
    ) {
        // ---- Splash / Loading ----
        composable(Destinations.LOADING) {
            LoadingScreen(
                onFinished = {
                    navController.navigate(Destinations.DASHBOARD) {
                        popUpTo(Destinations.LOADING) { inclusive = true }
                    }
                },
            )
        }

        // ---- Dashboard ----
        composable(Destinations.DASHBOARD) {
            DashboardScreen(
                onToolClick = { route -> navController.navigate(route) },
                onAboutClick = { navController.navigate(Destinations.ABOUT) },
            )
        }

        // ---- About ----
        composable(Destinations.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        // ---- Tools (all real, wired) ----
        toolRoute(navController, Destinations.MERGE)        { back -> MergeScreen(onBack = back) }
        toolRoute(navController, Destinations.SPLIT)        { back -> SplitScreen(onBack = back) }
        toolRoute(navController, Destinations.ROTATE)       { back -> RotateScreen(onBack = back) }
        toolRoute(navController, Destinations.REORDER)      { back -> ReorderScreen(onBack = back) }
        toolRoute(navController, Destinations.REMOVE_PAGES) { back -> RemovePagesScreen(onBack = back) }
        toolRoute(navController, Destinations.IMAGES_TO_PDF){ back -> ImagesToPdfScreen(onBack = back) }
        toolRoute(navController, Destinations.TEXT_TO_PDF)  { back -> TextToPdfScreen(onBack = back) }
        toolRoute(navController, Destinations.PDF_TO_IMAGES){ back -> PdfToImagesScreen(onBack = back) }
        toolRoute(navController, Destinations.ADD_CONTENT)  { back -> AddContentScreen(onBack = back) }
        toolRoute(navController, Destinations.WATERMARK)    { back -> WatermarkScreen(onBack = back) }
        toolRoute(navController, Destinations.PAGE_NUMBERS) { back -> PageNumbersScreen(onBack = back) }
        toolRoute(navController, Destinations.PASSWORD)     { back -> PasswordScreen(onBack = back) }

        // ---- Phase 2 placeholder ----
        toolStub(navController, Destinations.EDIT_PDF)
    }
}

private fun NavGraphBuilder.toolRoute(
    navController: NavHostController,
    route: String,
    content: @Composable (onBack: () -> Unit) -> Unit,
) {
    composable(route) {
        content { navController.popBackStack() }
    }
}

/** Kept for Phase 2 tools that are not yet implemented. */
private fun NavGraphBuilder.toolStub(
    navController: NavHostController,
    route: String,
) {
    composable(route) {
        ToolStubScreen(route = route, onBack = { navController.popBackStack() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolStubScreen(route: String, onBack: () -> Unit) {
    val tool = ToolCatalog.byId(route)
    val title = tool?.let { stringResource(it.titleRes) } ?: route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back_button),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.coming_soon),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
