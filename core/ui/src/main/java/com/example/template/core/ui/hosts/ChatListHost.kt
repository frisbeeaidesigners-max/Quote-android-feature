package com.example.template.core.ui.hosts

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.components.avatar.AvatarColorScheme
import com.example.components.avatar.AvatarView
import com.example.components.chatlist.ChatListItem
import com.example.components.chatlist.ChatListItemView
import com.example.template.core.model.AvatarType
import com.example.template.core.model.Chat
import com.example.template.core.model.ChatType as DomainChatType
import com.example.template.core.model.MessageStatus as DomainMessageStatus
import com.example.template.core.model.Persona
import com.example.template.core.model.PreviewKind
import com.example.template.core.model.User
import com.example.template.core.model.UserStatus
import com.example.template.core.ui.BitmapCache
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalBitmapCache
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.format.TimeFormatter

@Composable
fun ChatListHost(
    chats: List<Chat>,
    onChatClick: (String) -> Unit,
    userLookup: (String) -> User?,
    personaLookup: (String) -> Persona?,
    personaById: (String) -> Persona?,
    modifier: Modifier = Modifier,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val bitmapCache = LocalBitmapCache.current
    // Eager-read для подписки на фоновый прогрев аватарок: иначе при асинхронной загрузке
    // персональных PNG список так и остался бы на initials до случайной рекомпозиции.
    val cacheVersion = bitmapCache.version.value
    val chatListColors = remember(brand, isDark) { brand.chatListColorScheme(isDark) }
    val badgeColors = remember(brand, isDark) { brand.badgeColorScheme(isDark) }

    val avatarSchemesByIndex: Map<Int, AvatarColorScheme> = remember(brand, isDark) {
        val pairs = brand.avatarGradientPairs(isDark)
        val base = brand.avatarColorScheme(isDark)
        (pairs.indices).associateWith { i ->
            base.copy(
                initialsGradientTop = pairs[i].top,
                initialsGradientBottom = pairs[i].bottom,
            )
        }
    }

    // Bottom Tabs overlay: 56dp pill + 24dp от нижнего края + 12dp воздуха над панелью.
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 92.dp),
    ) {
        itemsIndexed(chats, key = { _, chat -> chat.id }) { index, chat ->
            val isLast = index == chats.lastIndex
            val time = remember(chat.id, chat.lastMessage.timestamp) {
                TimeFormatter.formatChatListTime(chat.lastMessage.timestamp)
            }
            // Item-scope read version: lazy items пропускают рекомпозицию когда inputs стабильны,
            // подписки на уровне ChatListHost мало — нужно затронуть и каждый item, чтобы он
            // перечитал bitmapCache.get(...) когда фоновый прогрев докинул аватарку.
            @Suppress("UNUSED_VARIABLE")
            val itemCacheVersion = cacheVersion

            val persona: Persona? = if (chat.type == DomainChatType.P2P) {
                chat.participantIds.firstOrNull()?.let { personaLookup(it) }
            } else null

            val resolution = resolveAvatar(chat, persona, bitmapCache)

            val schemeIndex = resolution.gradientIndex
                .coerceAtLeast(0)
                .let { it % avatarSchemesByIndex.size.coerceAtLeast(1) }
            val avatarScheme = avatarSchemesByIndex[schemeIndex]
                ?: avatarSchemesByIndex.values.first()

            val avatarBadge = when {
                chat.type == DomainChatType.Channel -> ChatListItemView.AvatarBadgeType.CHANNEL
                chat.type == DomainChatType.P2P && isParticipantOnline(chat, userLookup) ->
                    ChatListItemView.AvatarBadgeType.ONLINE
                else -> ChatListItemView.AvatarBadgeType.NONE
            }

            val messageViewType = when (chat.lastMessage.kind) {
                PreviewKind.Text -> ChatListItemView.MessageViewType.TEXT
                PreviewKind.Group -> ChatListItemView.MessageViewType.GROUP
                PreviewKind.Draft -> ChatListItemView.MessageViewType.DRAFT
                PreviewKind.Typing -> ChatListItemView.MessageViewType.TYPING
            }

            val messageStatus = when (chat.lastMessage.ownStatus) {
                DomainMessageStatus.NONE -> ChatListItemView.MessageStatus.NONE
                DomainMessageStatus.SENDING -> ChatListItemView.MessageStatus.SENDING
                DomainMessageStatus.DELIVERED -> ChatListItemView.MessageStatus.DELIVERED
                DomainMessageStatus.READ -> ChatListItemView.MessageStatus.READ
                DomainMessageStatus.ERROR -> ChatListItemView.MessageStatus.ERROR
            }

            val senderPersona = chat.lastMessage.senderPersonaId?.let(personaById)
            val senderAvatarBitmap: Bitmap? = bitmapCache.get(senderPersona?.avatarAsset)

            ChatListItem(
                avatarMode = AvatarView.AvatarMode.USER_GROUP,
                avatarType = resolution.type,
                avatarImage = resolution.image,
                avatarText = resolution.text,
                avatarIconName = resolution.iconName,
                avatarColorScheme = avatarScheme,
                avatarBadge = avatarBadge,
                avatarBadgeColorScheme = badgeColors,
                title = chat.title,
                chatType = ChatListItemView.ChatType.DEFAULT,
                isMuted = chat.muted,
                showVerification = false,
                time = time,
                messageStatus = messageStatus,
                messageViewType = messageViewType,
                messageText = chat.lastMessage.text,
                senderName = chat.lastMessage.senderName.orEmpty(),
                senderAvatar = senderAvatarBitmap,
                showReactionBadge = chat.hasReaction,
                showMentionBadge = chat.mention,
                showPinBadge = chat.pinned,
                unreadCount = chat.unreadCount,
                colorScheme = chatListColors,
                showDivider = !isLast,
                onClick = { onChatClick(chat.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private data class AvatarResolution(
    val type: AvatarView.AvatarViewType,
    val text: String,
    val iconName: String,
    val image: Bitmap?,
    val gradientIndex: Int,
)

private fun resolveAvatar(
    chat: Chat,
    persona: Persona?,
    bitmapCache: BitmapCache,
): AvatarResolution {
    if (chat.avatar.type == AvatarType.Self) {
        return AvatarResolution(
            type = AvatarView.AvatarViewType.SAVED,
            text = "",
            iconName = "bookmark",
            image = null,
            gradientIndex = chat.avatar.gradientIndex,
        )
    }

    if (persona != null) {
        val image = bitmapCache.get(persona.avatarAsset)
        return if (image != null) {
            AvatarResolution(
                type = AvatarView.AvatarViewType.IMAGE,
                text = persona.initials,
                iconName = "bookmark",
                image = image,
                gradientIndex = persona.gradientIndex,
            )
        } else {
            AvatarResolution(
                type = AvatarView.AvatarViewType.INITIALS,
                text = persona.initials,
                iconName = "bookmark",
                image = null,
                gradientIndex = persona.gradientIndex,
            )
        }
    }

    val explicitImage = bitmapCache.get(chat.avatar.imageAsset)
    return if (explicitImage != null) {
        AvatarResolution(
            type = AvatarView.AvatarViewType.IMAGE,
            text = chat.avatar.initials.orEmpty(),
            iconName = "bookmark",
            image = explicitImage,
            gradientIndex = chat.avatar.gradientIndex,
        )
    } else {
        AvatarResolution(
            type = AvatarView.AvatarViewType.INITIALS,
            text = chat.avatar.initials.orEmpty(),
            iconName = "bookmark",
            image = null,
            gradientIndex = chat.avatar.gradientIndex,
        )
    }
}

private fun isParticipantOnline(chat: Chat, userLookup: (String) -> User?): Boolean {
    val participantId = chat.participantIds.firstOrNull() ?: return false
    val user = userLookup(participantId) ?: return false
    return user.status is UserStatus.Online
}
