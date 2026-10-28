package com.ternagames.terna.feature.home.profile

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ternagames.terna.core.designsystem.component.PlayerAvatar
import com.ternagames.terna.core.designsystem.component.TernaAvatars
import com.ternagames.terna.core.designsystem.component.TernaPanel
import com.ternagames.terna.core.designsystem.component.TernaPrimaryButton
import com.ternagames.terna.core.designsystem.theme.TernaColors
import com.ternagames.terna.core.designsystem.theme.TitleFont
import com.ternagames.terna.core.model.Avatars

private const val COLUMNS = 5

/** Modal "Elige tu avatar", con el mismo marco que "Sala Privada": panel, ✕, ícono y título amarillos. */
@Composable
internal fun AvatarPickerDialog(
    current: String,
    photoUrl: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    // La foto de Google solo se ofrece a quien la tiene.
    val options = buildList {
        if (photoUrl != null) add(Avatars.PHOTO to "Mi foto")
        TernaAvatars.all.forEach { add(it.id to it.name) }
    }
    var selected by rememberSaveable { mutableStateOf(current) }
    val selectedName = options.firstOrNull { it.first == selected }?.second.orEmpty()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        TernaPanel(Modifier.widthIn(max = 520.dp).padding(16.dp), contentPadding = 16.dp) {
            Box(Modifier.fillMaxWidth()) {
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = TernaColors.TextMuted)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                ) {
                    Icon(Icons.Rounded.Pets, contentDescription = null, tint = TernaColors.Yellow, modifier = Modifier.size(24.dp))
                    Text(
                        "Elige tu avatar",
                        style = MaterialTheme.typography.headlineLarge.copy(fontFamily = TitleFont, fontWeight = FontWeight.Bold, fontSize = 28.sp),
                        color = TernaColors.White,
                    )
                    options.chunked(COLUMNS).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { (id, name) ->
                                AvatarChoice(id, name, photoUrl, selected = id == selected, onClick = { selected = id })
                            }
                        }
                    }
                    Text(selectedName, style = MaterialTheme.typography.labelLarge, color = TernaColors.Yellow)
                    TernaPrimaryButton(
                        text = "GUARDAR",
                        onClick = { onConfirm(selected) },
                        modifier = Modifier.width(260.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarChoice(id: String, name: String, photoUrl: String?, selected: Boolean, onClick: () -> Unit) {
    val ring = if (selected) TernaColors.Yellow else TernaColors.PanelVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .border(3.dp, ring, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = name
                this.selected = selected
            },
    ) {
        PlayerAvatar(avatarId = id, size = 54.dp, photoUrl = photoUrl, borderWidth = 0.dp)
    }
}
