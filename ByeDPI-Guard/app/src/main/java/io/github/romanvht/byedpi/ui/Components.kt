package io.github.romanvht.byedpi.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import io.github.romanvht.byedpi.utility.getPreferences
import androidx.compose.ui.res.stringResource
import io.github.romanvht.byedpi.R

// ---------------------------------------------------------------------------------------------
// Containers
// ---------------------------------------------------------------------------------------------

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Palette.Border,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    // The default border is the glass edge; any other color highlights the card.
    val highlighted = borderColor != Palette.Border
    val border by animateColorAsState(if (highlighted) borderColor else Color.Transparent, tween(Motion.SLOW), label = "cardBorder")
    Column(
        modifier
            .fillMaxWidth()
            .glass(borderColor = if (highlighted) border else null)
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (subtitle != null) {
            Text(
                subtitle,
                color = Palette.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun HelperText(text: String, modifier: Modifier = Modifier, color: Color = Palette.TextSecondary) {
    Text(text, color = color, style = MaterialTheme.typography.bodySmall, modifier = modifier)
}

/** Tinted notice used for "reconnect to apply", "disabled because…" and similar hints. */
@Composable
fun InfoBanner(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = Palette.Glow,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(Glass.ControlShape)
            .background(Palette.Surface.copy(alpha = 0.55f))
            .background(tint.copy(alpha = 0.14f))
            .border(1.dp, tint.copy(alpha = 0.40f), Glass.ControlShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            action()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    container: Color = Palette.AccentBlue,
    height: Int = 52
) {
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(container, tween(Motion.MEDIUM), label = "primaryBg")
    // The default accent is drawn as the glass accent gradient; other colors stay solid.
    val accent = container == Palette.AccentBlue
    val shape = RoundedCornerShape(18.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .pressScale(interaction)
            .height(height.dp)
            .clip(shape)
            .then(
                when {
                    !enabled -> Modifier.glassInset(shape)
                    accent -> Modifier.background(Glass.Accent).border(1.dp, Color.White.copy(alpha = 0.25f), shape)
                    else -> Modifier.background(bg).border(1.dp, Glass.InsetBorder, shape)
                }
            ),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = Palette.TextSecondary
        ),
        interactionSource = interaction
    ) {
        AnimatedContent(
            targetState = text to icon,
            transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.FAST)) },
            label = "primaryContent"
        ) { (label, ic) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ic != null) {
                    Icon(ic, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(label, fontWeight = FontWeight.SemiBold, fontSize = if (height >= 56) 18.sp else 15.sp)
            }
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val interaction = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.pressScale(interaction),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Glass.InsetBorder),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Glass.Inset, contentColor = Color.White),
        interactionSource = interaction
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text)
    }
}

// ---------------------------------------------------------------------------------------------
// Selection controls
// ---------------------------------------------------------------------------------------------

/** Horizontally scrolling pills with an animated selected state. */
@Composable
fun <T> PillTabs(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector?)? = null,
    enabled: Boolean = true
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            Pill(
                text = label(item),
                icon = icon?.invoke(item),
                selected = item == selected,
                enabled = enabled,
                onClick = { onSelect(item) }
            )
        }
    }
}

@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val accent by animateFloatAsState(if (selected) 1f else 0f, tween(Motion.MEDIUM), label = "pillBg")
    val fg by animateColorAsState(
        when {
            !enabled -> Palette.Gray
            selected -> Color.White
            else -> Palette.TextSecondary
        },
        tween(Motion.MEDIUM), label = "pillFg"
    )
    val border by animateColorAsState(
        if (selected) Color.White.copy(alpha = 0.30f) else Glass.InsetBorder, tween(Motion.MEDIUM), label = "pillBorder"
    )
    Row(
        Modifier
            .pressScale(interaction)
            .clip(RoundedCornerShape(50))
            .background(Glass.Inset)
            .background(Glass.Accent, alpha = accent)
            .border(1.dp, border, RoundedCornerShape(50))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .heightIn(min = 48.dp) // Fitts: a full thumb-sized target
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Equal-width segmented choice, for 2–3 short options. */
@Composable
fun <T> Segmented(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier
            .fillMaxWidth()
            .glassInset()
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { item ->
            val isSelected = item == selected
            val accent by animateFloatAsState(if (isSelected) 1f else 0f, tween(Motion.MEDIUM), label = "segBg")
            val fg by animateColorAsState(
                when {
                    !enabled -> Palette.Gray
                    isSelected -> Color.White
                    else -> Palette.TextSecondary
                },
                tween(Motion.MEDIUM), label = "segFg"
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Glass.Accent, alpha = accent)
                    .clickable(enabled = enabled) { onSelect(item) }
                    .heightIn(min = 48.dp)
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label(item), color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    val titleColor by animateColorAsState(if (enabled) Color.White else Palette.Gray, label = "switchTitle")
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = titleColor, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) HelperText(subtitle, Modifier.padding(top = 2.dp))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Palette.AccentBlue,
                checkedBorderColor = Palette.Glow,
                uncheckedThumbColor = Palette.TextSecondary,
                uncheckedTrackColor = Palette.SurfaceHigh,
                uncheckedBorderColor = Palette.Border
            )
        )
    }
}

/** Tappable settings row: icon, title, current value, optional trailing content. */
@Composable
fun NavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Sky.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = Palette.Sky, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) Color.White else Palette.Gray, style = MaterialTheme.typography.bodyLarge)
            if (value != null) HelperText(value, Modifier.padding(top = 2.dp))
        }
        trailing?.invoke()
    }
}

// ---------------------------------------------------------------------------------------------
// Text input
// ---------------------------------------------------------------------------------------------

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.Glow,
    unfocusedBorderColor = Glass.InsetBorder,
    disabledBorderColor = Glass.InsetBorder,
    focusedContainerColor = Color.White.copy(alpha = 0.08f),
    unfocusedContainerColor = Glass.Inset,
    disabledContainerColor = Color.White.copy(alpha = 0.03f),
    cursorColor = Palette.Glow,
    focusedLabelColor = Palette.Sky
)

/**
 * Text field bound to a SharedPreferences string. The value is saved only while it passes
 * [validate]; otherwise the field shows [error] and the last valid value stays in effect.
 */
@Composable
fun PrefTextField(
    label: String,
    key: String,
    default: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    signed: Boolean = false,
    singleLine: Boolean = true,
    monospace: Boolean = false,
    enabled: Boolean = true,
    maxLength: Int = Int.MAX_VALUE,
    error: String? = null,
    hint: String? = null,
    validate: (String) -> Boolean = { true }
) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getPreferences() }
    var value by remember(key) { mutableStateOf(prefs.getString(key, default) ?: default) }
    var invalid by remember(key) { mutableStateOf(false) }
    val support: (@Composable () -> Unit)? = when {
        invalid -> { { Text(error ?: stringResource(R.string.pg_err_invalid)) } }
        hint != null -> { { Text(hint) } }
        else -> null
    }
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val cleaned = when {
                numeric && signed -> raw.filterIndexed { i, c -> c.isDigit() || (i == 0 && c == '-') }
                numeric -> raw.filter(Char::isDigit)
                else -> raw
            }.take(maxLength)
            value = cleaned
            invalid = !validate(cleaned)
            if (!invalid) prefs.edit { putString(key, cleaned) }
        },
        label = { Text(label) },
        singleLine = singleLine,
        enabled = enabled,
        isError = invalid,
        supportingText = support,
        textStyle = if (monospace) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        else MaterialTheme.typography.bodyLarge,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = fieldColors()
    )
}

// ---------------------------------------------------------------------------------------------
// Preference-backed state
// ---------------------------------------------------------------------------------------------

private class PrefBackedState<T>(
    private val inner: MutableState<T>,
    private val save: (T) -> Unit
) : MutableState<T> {
    override var value: T
        get() = inner.value
        set(v) {
            inner.value = v
            save(v)
        }

    override operator fun component1(): T = value
    override operator fun component2(): (T) -> Unit = { value = it }
}

@Composable
fun rememberBoolPref(key: String, default: Boolean): MutableState<Boolean> {
    val prefs = LocalContext.current.getPreferences()
    return remember(key) {
        PrefBackedState(mutableStateOf(prefs.getBoolean(key, default))) { prefs.edit { putBoolean(key, it) } }
    }
}

@Composable
fun rememberStringPref(key: String, default: String): MutableState<String> {
    val prefs = LocalContext.current.getPreferences()
    return remember(key) {
        PrefBackedState(mutableStateOf(prefs.getString(key, default) ?: default)) { prefs.edit { putString(key, it) } }
    }
}

/** "Between [min] and [max]", the standard range error for numeric fields. */
@Composable
fun between(min: Int, max: Int): String = stringResource(R.string.pg_err_between, min, max)

@Composable
fun Gap(height: Int = 8) = Spacer(Modifier.height(height.dp))
