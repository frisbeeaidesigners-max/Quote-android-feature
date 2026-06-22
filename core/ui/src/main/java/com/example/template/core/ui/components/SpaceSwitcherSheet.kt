package com.example.template.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.avatar.AvatarView
import com.example.template.core.model.Space
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceSwitcherSheet(
    spaces: List<Space>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val avatarScheme = brand.avatarColorScheme(isDark)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column {
            spaces.forEach { space ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(space.id); onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    AndroidView(
                        modifier = Modifier.size(40.dp),
                        factory = { AvatarView(it) },
                        update = { view ->
                            view.configure(
                                mode = AvatarView.AvatarMode.SPACES,
                                type = AvatarView.AvatarViewType.INITIALS,
                                size = AvatarView.AvatarSize.SIZE_40,
                                text = space.avatar.initials ?: space.name.take(2),
                                colorScheme = avatarScheme,
                            )
                        },
                    )
                    Text(space.name, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}
