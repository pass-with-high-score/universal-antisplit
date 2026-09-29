package app.pwhs.universalantisplit.ui.settings.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pwhs.universalantisplit.R
import app.pwhs.universalantisplit.domain.AppThemePreset
import app.pwhs.universalantisplit.domain.ThemeMode
import app.pwhs.universalantisplit.theme.Spacing
import app.pwhs.universalantisplit.ui.components.SettingsSection

@Composable
fun AppearanceSettingsSection(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    amoledMode: Boolean,
    themePreset: AppThemePreset,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAmoledModeChange: (Boolean) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onThemePresetChange: (AppThemePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(R.string.settings_section_appearance),
        icon = Icons.Rounded.Palette,
        collapsible = true,
        defaultExpanded = true,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(Spacing.L)) {
            Text(
                text = stringResource(R.string.settings_theme_mode_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(Spacing.S))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S)
            ) {
                ThemeMode.entries.forEach { mode ->
                    val selected = themeMode == mode
                    val labelRes = when (mode) {
                        ThemeMode.System -> R.string.settings_theme_system
                        ThemeMode.Light -> R.string.settings_theme_light
                        ThemeMode.Dark -> R.string.settings_theme_dark
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { onThemeModeChange(mode) },
                        label = { Text(stringResource(labelRes)) },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(Modifier.height(Spacing.L))

            // AMOLED Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_amoled_title),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.settings_amoled_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = amoledMode,
                    onCheckedChange = onAmoledModeChange
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(Modifier.height(Spacing.L))

                // Dynamic Color Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_dynamic_color_title),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.settings_dynamic_color_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = onDynamicColorChange
                    )
                }
            }

            Spacer(Modifier.height(Spacing.L))

            // Color Presets
            Text(
                text = stringResource(R.string.settings_preset_color_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(Spacing.S))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppThemePreset.entries.forEach { preset ->
                    val color = when (preset) {
                        AppThemePreset.Orange -> Color(0xFFEA580C)
                        AppThemePreset.Blue -> Color(0xFF0284C7)
                        AppThemePreset.Green -> Color(0xFF16A34A)
                        AppThemePreset.Red -> Color(0xFFDC2626)
                        AppThemePreset.Purple -> Color(0xFF9333EA)
                    }
                    val isSelected = themePreset == preset && !dynamicColor

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onThemePresetChange(preset) }
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
