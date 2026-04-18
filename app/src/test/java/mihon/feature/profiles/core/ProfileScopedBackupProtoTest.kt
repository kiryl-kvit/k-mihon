package mihon.feature.profiles.core

import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Test
import tachiyomi.domain.profile.model.ProfileType

class ProfileScopedBackupProtoTest {

    @Test
    fun `legacy profile backup bytes decode with manga default type`() {
        val bytes = ProtoBuf.encodeToByteArray(
            serializer = LegacyProfileBackup.serializer(),
            LegacyProfileBackup(
                uuid = "legacy-uuid",
                name = "Legacy",
                colorSeed = 123L,
                position = 4L,
                requiresAuth = true,
                isArchived = false,
            ),
        )

        ProtoBuf.decodeFromByteArray(ProfileBackup.serializer(), bytes) shouldBe ProfileBackup(
            uuid = "legacy-uuid",
            name = "Legacy",
            colorSeed = 123L,
            position = 4L,
            requiresAuth = true,
            isArchived = false,
            type = ProfileType.MANGA,
        )
    }

    @Test
    fun `legacy scoped backup bytes decode with manga default profile type`() {
        val bytes = ProtoBuf.encodeToByteArray(
            serializer = LegacyProfileScopedBackup.serializer(),
            LegacyProfileScopedBackup(
                profile = LegacyProfileBackup(
                    uuid = "legacy-uuid",
                    name = "Legacy",
                    colorSeed = 123L,
                    position = 4L,
                    requiresAuth = true,
                    isArchived = false,
                ),
            ),
        )

        ProtoBuf.decodeFromByteArray(ProfileScopedBackup.serializer(), bytes).profile.type shouldBe ProfileType.MANGA
    }

    @Serializable
    private data class LegacyProfileScopedBackup(
        @ProtoNumber(1) val profile: LegacyProfileBackup,
    )

    @Serializable
    private data class LegacyProfileBackup(
        @ProtoNumber(1) val uuid: String,
        @ProtoNumber(2) val name: String,
        @ProtoNumber(3) val colorSeed: Long,
        @ProtoNumber(4) val position: Long,
        @ProtoNumber(5) val requiresAuth: Boolean,
        @ProtoNumber(6) val isArchived: Boolean,
    )
}
