package app.pwhs.universalantisplit.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pwhs.universalantisplit.R
import app.pwhs.universalantisplit.theme.Spacing
import app.pwhs.universalantisplit.ui.onboarding.components.LiquidPagerIndicator
import app.pwhs.universalantisplit.ui.onboarding.pages.OnboardingFeaturesPage
import app.pwhs.universalantisplit.ui.onboarding.pages.OnboardingPermissionsPage
import app.pwhs.universalantisplit.ui.onboarding.pages.OnboardingSetupPage
import app.pwhs.universalantisplit.ui.onboarding.pages.OnboardingWelcomePage
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.math.absoluteValue

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()

    // Ambient glow colors shifting across pages
    val topGlowColor by animateColorAsState(
        targetValue = when (pagerState.currentPage) {
            0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            1 -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
            2 -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        },
        animationSpec = tween(600),
        label = "top_glow"
    )

    val bottomGlowColor by animateColorAsState(
        targetValue = when (pagerState.currentPage) {
            0 -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
            1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
            2 -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
            else -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f)
        },
        animationSpec = tween(600),
        label = "bottom_glow"
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Ambient Orbs Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(topGlowColor, Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.12f),
                        radius = size.width * 0.75f
                    ),
                    center = Offset(size.width * 0.85f, size.height * 0.12f),
                    radius = size.width * 0.75f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(bottomGlowColor, Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.88f),
                        radius = size.width * 0.75f
                    ),
                    center = Offset(size.width * 0.15f, size.height * 0.88f),
                    radius = size.width * 0.75f
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = Spacing.L),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pagerState.currentPage < 3) {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(3)
                                }
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_skip),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Horizontal Pager with 3D Parallax & Depth Transform
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page ->
                    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                    val scale = 1f - (pageOffset * 0.12f).coerceIn(0f, 0.25f)
                    val alpha = 1f - (pageOffset * 0.45f).coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                            }
                    ) {
                        when (page) {
                            0 -> OnboardingWelcomePage()
                            1 -> OnboardingFeaturesPage()
                            2 -> OnboardingPermissionsPage(
                                uiState = uiState,
                                onPermissionChanged = { viewModel.refreshPermissionStates() }
                            )
                            3 -> OnboardingSetupPage(
                                uiState = uiState,
                                onLanguageSelected = { viewModel.setLanguage(it) },
                                onThemeModeSelected = { viewModel.setThemeMode(it) }
                            )
                        }
                    }
                }

                // Bottom Navigation Controller
                if (pagerState.currentPage < 3) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.L, vertical = Spacing.L),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LiquidPagerIndicator(
                            pageCount = 4,
                            currentPage = pagerState.currentPage,
                            currentPageOffsetFraction = pagerState.currentPageOffsetFraction
                        )

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_next),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(Spacing.S))
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.L, vertical = Spacing.L),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.M)
                    ) {
                        LiquidPagerIndicator(
                            pageCount = 4,
                            currentPage = pagerState.currentPage,
                            currentPageOffsetFraction = pagerState.currentPageOffsetFraction
                        )

                        Button(
                            onClick = {
                                viewModel.completeOnboarding(onSuccess = onComplete)
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RocketLaunch,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(Spacing.S))
                            Text(
                                text = stringResource(R.string.onboarding_get_started),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
