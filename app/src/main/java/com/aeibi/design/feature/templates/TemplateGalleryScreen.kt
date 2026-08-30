package com.aeibi.design.feature.templates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.aeibi.design.R
import com.aeibi.design.data.templates.Template
import com.aeibi.design.theme.spacing

@Composable
fun TemplateGalleryScreen(
    projectId: String,
    onBackClick: () -> Unit,
    onProjectInitialized: (projectId: String) -> Unit,
    viewModel: TemplateGalleryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TemplateGalleryContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onRetry = viewModel::retry,
        onTemplateClick = viewModel::openTemplate,
        onCloseTemplate = viewModel::closeTemplate,
        onUseTemplateClick = {
            viewModel.initializeProject(projectId) {
                onProjectInitialized(projectId)
            }
        }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun TemplateGalleryContent(
    uiState: TemplateGalleryUiState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onTemplateClick: (Template) -> Unit,
    onCloseTemplate: () -> Unit,
    onUseTemplateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedTemplate = uiState.selectedTemplate
    BackHandler(enabled = selectedTemplate != null) {
        if (!uiState.isApplyingTemplate) onCloseTemplate()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selectedTemplate?.name ?: stringResource(R.string.template_gallery_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = if (selectedTemplate == null) onBackClick else onCloseTemplate,
                        enabled = !uiState.isApplyingTemplate
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            selectedTemplate?.let {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .navigationBarsPadding()
                        .padding(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)
                ) {
                    if (uiState.applyFailed) {
                        Text(
                            text = stringResource(R.string.template_gallery_apply_failed),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("template_apply_failed")
                        )
                    }
                    Button(
                        onClick = onUseTemplateClick,
                        enabled = !uiState.isApplyingTemplate,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("use_template_button")
                    ) {
                        if (uiState.isApplyingTemplate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp).testTag("template_apply_loading"),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.template_gallery_use_template))
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        val contentModifier = Modifier.fillMaxSize().padding(innerPadding)
        when {
            selectedTemplate != null -> TemplateDetail(
                template = selectedTemplate,
                readme = uiState.readme,
                isReadmeLoading = uiState.isReadmeLoading,
                modifier = contentModifier
            )

            uiState.isLoading -> Box(contentModifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.testTag("template_gallery_loading"))
            }

            uiState.loadFailed -> Column(
                modifier = contentModifier.padding(MaterialTheme.spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md, Alignment.CenterVertically)
            ) {
                Text(
                    text = stringResource(R.string.template_gallery_load_failed),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
            }

            uiState.templates.isEmpty() -> Box(contentModifier, contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.template_gallery_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> TemplateGrid(
                templates = uiState.templates,
                onTemplateClick = onTemplateClick,
                modifier = contentModifier
            )
        }
    }
}

@Composable
private fun TemplateGrid(
    templates: List<Template>,
    onTemplateClick: (Template) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = modifier.testTag("template_grid"),
        contentPadding = PaddingValues(
            start = spacing.sm,
            top = spacing.md,
            end = spacing.sm,
            bottom = spacing.xl
        ),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalItemSpacing = spacing.sm
    ) {
        items(templates, key = Template::id) { template ->
            ElevatedCard(
                onClick = { onTemplateClick(template) },
                modifier = Modifier.fillMaxWidth().testTag("template_card_${template.id}"),
                shape = MaterialTheme.shapes.medium
            ) {
                TemplateAssetImage(
                    assetPath = template.coverAssetPath,
                    contentDescription = stringResource(R.string.template_gallery_cover_cd, template.name),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.xs, start = spacing.xs, end = spacing.xs)
                        .clip(RoundedCornerShape(spacing.xs))
                )
                Column(
                    modifier = Modifier.padding(start = spacing.sm, top = 10.dp, end = spacing.sm, bottom = 14.dp)
                ) {
                    Text(
                        text = template.category,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = template.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = template.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateDetail(
    template: Template,
    readme: String?,
    isReadmeLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing
    val imagePaths = remember(template) { listOf(template.coverAssetPath) + template.previewAssetPaths }
    val pagerState = rememberPagerState(pageCount = imagePaths::size)

    LazyColumn(
        modifier = modifier.testTag("template_detail_${template.id}"),
        contentPadding = PaddingValues(bottom = spacing.lg)
    ) {
        item {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .testTag("template_preview_pager")
            ) { page ->
                TemplateAssetImage(
                    assetPath = imagePaths[page],
                    contentDescription = stringResource(
                        R.string.template_gallery_preview_cd,
                        template.name,
                        page + 1
                    ),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
        item {
            val indicatorDescription = stringResource(
                R.string.template_gallery_page_indicator,
                pagerState.currentPage + 1,
                imagePaths.size
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.sm)
                    .semantics { contentDescription = indicatorDescription }
                    .testTag("template_pager_page_${pagerState.currentPage + 1}"),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(imagePaths.size) { page ->
                    Box(
                        modifier = Modifier
                            .size(if (page == pagerState.currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (page == pagerState.currentPage) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                    )
                }
            }
        }
        item {
            Column(
                modifier = Modifier.padding(horizontal = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                Text(
                    text = template.category,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(text = template.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = template.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = spacing.md))
                Text(
                    text = stringResource(R.string.template_gallery_readme_title),
                    style = MaterialTheme.typography.titleMedium
                )
                when {
                    isReadmeLoading -> CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).testTag("template_readme_loading"),
                        strokeWidth = 2.dp
                    )

                    readme != null -> Text(text = readme, style = MaterialTheme.typography.bodyMedium)
                    else -> Text(
                        text = stringResource(R.string.template_gallery_readme_unavailable),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateAssetImage(
    assetPath: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.FillWidth
) {
    SubcomposeAsyncImage(
        model = "file:///android_asset/$assetPath",
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
            .defaultMinSize(minHeight = 140.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        alignment = Alignment.Center,
        loading = {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        },
        error = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)
            ) {
                Icon(
                    imageVector = Icons.Filled.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.template_gallery_image_failed),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}
