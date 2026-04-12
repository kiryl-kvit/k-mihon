package eu.kanade.tachiyomi.data.backup.models

import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.feature.profiles.core.ProfileBackup
import mihon.feature.profiles.core.ProfileScopedBackup
import org.junit.jupiter.api.Test
import tachiyomi.domain.profile.model.ProfileType

class VideoBackupProtoCompatibilityTest {

    @Test
    fun `legacy backup bytes decode with empty video payload`() {
        val bytes = ProtoBuf.encodeToByteArray(
            serializer = LegacyBackup.serializer(),
            LegacyBackup(
                backupManga = emptyList(),
                backupCategories = emptyList(),
                backupSources = emptyList(),
                backupPreferences = emptyList(),
                backupSourcePreferences = emptyList(),
                backupExtensionRepo = emptyList(),
                backupProfiles = emptyList(),
                activeProfileUuid = null,
            ),
        )

        ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes).backupVideo shouldBe emptyList()
    }

    @Test
    fun `legacy profile scoped backup bytes decode with empty video payload`() {
        val bytes = ProtoBuf.encodeToByteArray(
            serializer = LegacyProfileScopedBackup.serializer(),
            LegacyProfileScopedBackup(
                profile = ProfileBackup(
                    uuid = "uuid",
                    name = "Profile",
                    colorSeed = 1L,
                    position = 1L,
                    requiresAuth = false,
                    isArchived = false,
                    type = ProfileType.MANGA,
                ),
                categories = emptyList(),
                manga = emptyList(),
                preferences = emptyList(),
                sourcePreferences = emptyList(),
            ),
        )

        ProtoBuf.decodeFromByteArray(ProfileScopedBackup.serializer(), bytes).video shouldBe emptyList()
    }

    @Serializable
    private data class LegacyBackup(
        @ProtoNumber(1) val backupManga: List<BackupManga> = emptyList(),
        @ProtoNumber(2) val backupCategories: List<BackupCategory> = emptyList(),
        @ProtoNumber(101) val backupSources: List<BackupSource> = emptyList(),
        @ProtoNumber(104) val backupPreferences: List<BackupPreference> = emptyList(),
        @ProtoNumber(105) val backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),
        @ProtoNumber(106) val backupExtensionRepo: List<BackupExtensionRepos> = emptyList(),
        @ProtoNumber(200) val backupProfiles: List<ProfileScopedBackup> = emptyList(),
        @ProtoNumber(201) val activeProfileUuid: String? = null,
    )

    @Serializable
    private data class LegacyProfileScopedBackup(
        @ProtoNumber(1) val profile: ProfileBackup,
        @ProtoNumber(2) val categories: List<BackupCategory> = emptyList(),
        @ProtoNumber(3) val manga: List<BackupManga> = emptyList(),
        @ProtoNumber(4) val preferences: List<BackupPreference> = emptyList(),
        @ProtoNumber(5) val sourcePreferences: List<BackupSourcePreferences> = emptyList(),
    )
}
