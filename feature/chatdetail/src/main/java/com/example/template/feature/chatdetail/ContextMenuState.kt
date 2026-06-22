package com.example.template.feature.chatdetail

import com.example.components.contextmenu.ContextMenuView

data class ContextMenuState(
    val messageId: String,
    val type: ContextMenuView.Type,
    val mode: ContextMenuView.Mode,
    val messageKind: ContextMenuView.MessageKind,
    val isPinned: Boolean,
    val isSending: Boolean,
    val canReport: Boolean,
    val copyText: String,
)
