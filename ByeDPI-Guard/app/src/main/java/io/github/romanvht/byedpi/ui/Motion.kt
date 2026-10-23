package io.github.romanvht.byedpi.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Shared motion language: short, eased, and never in the way. */
object Motion {
    const val FAST = 160
    const val MEDIUM = 260
    const val SLOW = 420

    val bouncy = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
    val settle = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
}

/** Shrinks slightly while pressed and springs back, like a physical key. */
fun Modifier.pressScale(interaction: InteractionSource, pressedScale: Float = 0.96f): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = Motion.bouncy,
        label = "pressScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Fades and lifts content in when it first appears. [index] staggers sibling items
 * so a screen assembles top to bottom instead of popping in at once.
 */
fun Modifier.staggeredEnter(index: Int): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index * 45L).coerceAtMost(360L))
        progress.animateTo(1f, tween(Motion.SLOW, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 18.dp.toPx()
    }
}

/** Horizontal slide + fade, direction taken from the tab order. */
fun <T : Enum<T>> AnimatedContentTransitionScope<T>.tabTransition(): ContentTransform {
    val forward = targetState.ordinal > initialState.ordinal
    val sign = if (forward) 1 else -1
    return (slideInHorizontally(tween(Motion.MEDIUM, easing = FastOutSlowInEasing)) { it / 5 * sign } +
        fadeIn(tween(Motion.MEDIUM))) togetherWith
        (slideOutHorizontally(tween(Motion.MEDIUM, easing = FastOutSlowInEasing)) { -it / 5 * sign } +
            fadeOut(tween(Motion.FAST)))
}

/** A value that rolls up or down when it changes (latency, success rate, counters). */
@Composable
fun RollingText(
    text: String,
    fontSize: TextUnit,
    color: Color = Color.White,
    fontWeight: FontWeight = FontWeight.Bold,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(tween(Motion.MEDIUM)) { it / 2 } + fadeIn(tween(Motion.MEDIUM))) togetherWith
                (slideOutVertically(tween(Motion.FAST)) { -it / 2 } + fadeOut(tween(Motion.FAST)))
        },
        label = "rollingText",
        modifier = modifier
    ) { value ->
        Text(value, fontSize = fontSize, color = color, fontWeight = fontWeight)
    }
}
