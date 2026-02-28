package com.rrimal.notetaker.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.rrimal.notetaker.data.api.GitHubApi
import com.rrimal.notetaker.data.api.GitHubInstallationApi
import com.rrimal.notetaker.data.local.AppDatabase
import com.rrimal.notetaker.data.local.PendingNoteDao
import com.rrimal.notetaker.data.local.SubmissionDao
import com.rrimal.notetaker.data.local.SyncQueueDao
import com.rrimal.notetaker.data.storage.StorageConfigManager
import com.rrimal.notetaker.data.storage.LocalFileManager
import com.rrimal.notetaker.data.storage.LocalOrgStorageBackend
import com.rrimal.notetaker.data.storage.GitHubStorageBackend
import com.rrimal.notetaker.data.sync.GitHubSyncManager
import com.rrimal.notetaker.data.orgmode.OrgParser
import com.rrimal.notetaker.data.orgmode.OrgWriter
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.rrimal.notetaker.BuildConfig
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideEncryptedSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            "auth_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG)
                        HttpLoggingInterceptor.Level.BODY
                    else
                        HttpLoggingInterceptor.Level.NONE
                }
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideGitHubApi(retrofit: Retrofit): GitHubApi {
        return retrofit.create(GitHubApi::class.java)
    }

    @Provides
    @Singleton
    fun provideGitHubInstallationApi(retrofit: Retrofit): GitHubInstallationApi {
        return retrofit.create(GitHubInstallationApi::class.java)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "notetaker.db"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
    }

    @Provides
    fun provideSubmissionDao(db: AppDatabase): SubmissionDao {
        return db.submissionDao()
    }

    @Provides
    fun providePendingNoteDao(db: AppDatabase): PendingNoteDao {
        return db.pendingNoteDao()
    }

    @Provides
    fun provideSyncQueueDao(db: AppDatabase): SyncQueueDao {
        return db.syncQueueDao()
    }

    @Provides
    @Singleton
    fun provideStorageConfigManager(@ApplicationContext context: Context): StorageConfigManager {
        return StorageConfigManager(context)
    }

    @Provides
    @Singleton
    fun provideOrgParser(): OrgParser {
        return OrgParser()
    }

    @Provides
    @Singleton
    fun provideOrgWriter(): OrgWriter {
        return OrgWriter()
    }

    @Provides
    @Singleton
    fun provideLocalFileManager(
        @ApplicationContext context: Context,
        storageConfigManager: StorageConfigManager
    ): LocalFileManager {
        return LocalFileManager(context, storageConfigManager)
    }

    @Provides
    @Singleton
    fun provideGitHubStorageBackend(
        api: GitHubApi,
        authManager: com.rrimal.notetaker.data.auth.AuthManager
    ): GitHubStorageBackend {
        return GitHubStorageBackend(api, authManager)
    }

    @Provides
    @Singleton
    fun provideGitHubSyncManager(
        syncQueueDao: SyncQueueDao,
        workManager: WorkManager
    ): GitHubSyncManager {
        return GitHubSyncManager(syncQueueDao, workManager)
    }

    @Provides
    @Singleton
    fun provideLocalOrgStorageBackend(
        fileManager: LocalFileManager,
        orgParser: OrgParser,
        orgWriter: OrgWriter,
        storageConfigManager: StorageConfigManager
    ): LocalOrgStorageBackend {
        return LocalOrgStorageBackend(fileManager, orgParser, orgWriter, storageConfigManager)
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }
}
