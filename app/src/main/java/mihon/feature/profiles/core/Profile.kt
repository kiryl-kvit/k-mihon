package mihon.feature.profiles.core

import kotlinx.serialization.Serializable
import tachiyomi.domain.profile.model.ProfileType

@Serializable
data class Profile(
    val id: Long,
    val uuid: String,
    val name: String,
    val type: ProfileType,
    val colorSeed: Long,
    val position: Long,
    val requiresAuth: Boolean,
    val isArchived: Boolean,
)
