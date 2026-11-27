package com.example.execbin.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

@Database(entities = [CachedMessage::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        private const val ENCRYPTED_DB_NAME = "webview_cache_v3.db"

        // Written by older versions when SQLCipher failed — holds decrypted messages in plaintext.
        private const val LEGACY_PLAINTEXT_DB_NAME = "webview_cache_v3_plain.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** True when the cache is memory-only because the encrypted database could not be opened. */
        @Volatile
        var isMemoryOnly = false
            private set

        private fun keyPrefs(context: Context) = EncryptedSharedPreferences.create(
            "db_key_prefs",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        private fun getDatabasePassword(context: Context): ByteArray {
            // Use a securely random key stored in EncryptedSharedPreferences
            val prefs = keyPrefs(context)
            val existing = prefs.getString("db_key_v2", null)
            if (existing != null) {
                return Base64.decode(existing, Base64.NO_WRAP)
            }
            val keyBytes = ByteArray(32)
            SecureRandom().nextBytes(keyBytes)
            prefs.edit().putString("db_key_v2", Base64.encodeToString(keyBytes, Base64.NO_WRAP)).commit()
            return keyBytes
        }

        private fun openEncrypted(context: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(getDatabasePassword(context))
            val db = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, ENCRYPTED_DB_NAME)
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
            // Force the open now so a wrong key / missing native library fails here, not later
            db.openHelper.writableDatabase
            return db
        }

        /**
         * Opens the SQLCipher-encrypted message cache. Never falls back to an unencrypted
         * file: if encryption is unavailable the cache is kept in memory only (messages are
         * re-fetched from Firestore and decrypted on demand).
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: create(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun create(context: Context): AppDatabase {
            val encrypted = try {
                openEncrypted(context)
            } catch (e: Throwable) {
                android.util.Log.e("ExecBin", "Encrypted cache failed to open, resetting it", e)
                // The cache only mirrors Firestore data, so a corrupted cache/key can be rebuilt
                try {
                    context.deleteDatabase(ENCRYPTED_DB_NAME)
                    keyPrefs(context).edit().remove("db_key_v2").commit()
                    openEncrypted(context)
                } catch (e2: Throwable) {
                    android.util.Log.e("ExecBin", "Encrypted cache unavailable, using memory-only cache", e2)
                    null
                }
            }
            val db = encrypted ?: Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
            isMemoryOnly = encrypted == null
            migrateLegacyPlaintextCache(context, db)
            return db
        }

        /** Moves rows from the old plaintext fallback DB into [target], then deletes the plaintext file. */
        private fun migrateLegacyPlaintextCache(context: Context, target: AppDatabase) {
            if (!context.getDatabasePath(LEGACY_PLAINTEXT_DB_NAME).exists()) return
            try {
                val legacy = Room.databaseBuilder(context, AppDatabase::class.java, LEGACY_PLAINTEXT_DB_NAME)
                    .fallbackToDestructiveMigration()
                    .build()
                runBlocking {
                    val rows = legacy.messageDao().getAll()
                    if (rows.isNotEmpty()) target.messageDao().insertAll(rows)
                }
                legacy.close()
            } catch (e: Throwable) {
                android.util.Log.e("ExecBin", "Legacy plaintext cache migration failed — deleting it", e)
            }
            context.deleteDatabase(LEGACY_PLAINTEXT_DB_NAME)
        }
    }
}
