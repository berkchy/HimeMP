package hime.mp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hime.mp.ui.theme.LocalHimeMotion

data class BottomNavItem(val icon: ImageVector, val label: String)

@Composable
fun ModernBottomBar(
    items: List<BottomNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalHimeMotion.current
    val motionSpec: AnimationSpec<Float> = if (motion.standard == 0) {
        tween(0)
    } else {
        spring(dampingRatio = 0.78f, stiffness = 480f)
    }
    val colorSpec = tween<Color>(motion.standard)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 12.dp,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
        ) {
            val itemWidth = maxWidth / items.size.coerceAtLeast(1)
            val indicatorWidth = (itemWidth - 24.dp).coerceIn(44.dp, 88.dp)
            val indicatorX by animateDpAsState(
                targetValue = itemWidth * selectedIndex + (itemWidth - indicatorWidth) / 2,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
                label = "bottomNavIndicator",
            )

            Box(
                Modifier
                    .offset(x = indicatorX, y = 11.dp)
                    .size(indicatorWidth, 50.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )

            Row(Modifier.fillMaxWidth()) {
                items.forEachIndexed { index, item ->
                    val isSelected = index == selectedIndex
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.08f else 1f,
                        animationSpec = motionSpec,
                        label = "bottomNavIconScale",
                    )
                    val iconAlpha by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0.72f,
                        animationSpec = motionSpec,
                        label = "bottomNavIconAlpha",
                    )
                    val iconColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = colorSpec,
                        label = "bottomNavIconColor",
                    )
                    val labelColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = colorSpec,
                        label = "bottomNavLabelColor",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp)
                            .clickable(role = Role.Tab) { onSelect(index) }
                            .semantics { selected = isSelected },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            tint = iconColor,
                            modifier = Modifier
                                .size(24.dp)
                                .alpha(iconAlpha)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                },
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = labelColor,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
