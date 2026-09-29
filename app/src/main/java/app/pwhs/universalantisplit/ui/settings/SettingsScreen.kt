package app.pwhs.universalantisplit.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import coil.compose.AsyncImage
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pwhs.universalantisplit.R
import app.pwhs.universalantisplit.domain.AppThemePreset
import app.pwhs.universalantisplit.domain.ThemeMode
import app.pwhs.universalantisplit.theme.Spacing
import app.pwhs.universalantisplit.ui.components.SettingsSection
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: (() -> Unit)? = null,
    onHistoryClick: () -> Unit = {},
    onLanguageClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsScreenViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.settings_nav_back)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.L)
        ) {
            item { Spacer(Modifier.height(Spacing.XS)) }

            // 1. Giao diện & Chủ đề
            item {
                app.pwhs.universalantisplit.ui.settings.components.AppearanceSettingsSection(
                    themeMode = state.themeMode,
                    dynamicColor = state.dynamicColor,
                    amoledMode = state.amoledMode,
                    themePreset = state.themePreset,
                    onThemeModeChange = { viewModel.setThemeMode(it) },
                    onAmoledModeChange = { viewModel.setAmoledMode(it) },
                    onDynamicColorChange = { viewModel.setDynamicColor(it) },
                    onThemePresetChange = { viewModel.setThemePreset(it) },
                )
            }

            // 2. Ngôn ngữ
            item {
                SettingsSection(
                    title = stringResource(R.string.settings_section_language),
                    icon = Icons.Rounded.Language,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onLanguageClick)
                            .padding(Spacing.L),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_language_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(state.appLanguage.labelRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. Cấu hình Tối ưu hóa Anti-Split
            item {
                SettingsSection(
                    title = stringResource(R.string.settings_section_engine),
                    icon = Icons.Rounded.Memory,
                    collapsible = true,
                    defaultExpanded = true,
                ) {
                    Column(modifier = Modifier.padding(Spacing.L)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_align16kb_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.settings_align16kb_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.align16Kb,
                                onCheckedChange = { viewModel.setAlign16Kb(it) }
                            )
                        }

                        Spacer(Modifier.height(Spacing.L))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_clean_cache_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.settings_clean_cache_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.cleanCache,
                                onCheckedChange = { viewModel.setCleanCache(it) }
                            )
                        }

                        Spacer(Modifier.height(Spacing.L))

                        CacheManagementSection(
                            cacheSize = state.cacheSize,
                            isClearingCache = state.isClearingCache,
                            onClearTemp = { viewModel.clearCache(includeLocalMerged = false) },
                            onClearAll = { viewModel.clearCache(includeLocalMerged = true) },
                            onHistoryClick = onHistoryClick
                        )
                    }
                }
            }

            // 3. Ký số APK
            item {
                SettingsSection(
                    title = stringResource(R.string.settings_section_signature),
                    icon = Icons.Rounded.Key,
                    collapsible = true,
                    defaultExpanded = true,
                ) {
                    Column(modifier = Modifier.padding(Spacing.L)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.setting_auto_sign_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.settings_auto_sign_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.autoSign,
                                onCheckedChange = { viewModel.setAutoSign(it) }
                            )
                        }

                        Spacer(Modifier.height(Spacing.L))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_bypass_signature_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.settings_bypass_signature_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.bypassSignature,
                                onCheckedChange = { viewModel.setBypassSignature(it) }
                            )
                        }

                        Spacer(Modifier.height(Spacing.M))

                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(Spacing.M)) {
                                Text(
                                    text = stringResource(R.string.settings_default_keystore_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.settings_default_keystore_value),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 4. Giới thiệu & Thông tin
            item {
                SettingsSection(
                    title = stringResource(R.string.settings_section_about),
                    icon = Icons.Rounded.Info,
                    collapsible = true,
                    defaultExpanded = true,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.L),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AsyncImage(
                            model = R.drawable.ic_app_logo,
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(18.dp))
                        )

                        Spacer(Modifier.height(Spacing.M))

                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = stringResource(R.string.app_version_info),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(Spacing.S))

                        Text(
                            text = stringResource(R.string.app_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.M)
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }
}
