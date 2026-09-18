package com.tools.flowervault.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "master_passwords")
data class MasterPassword(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val pwdEnc: String,
    val lastUsedAt: Long = 0
)

@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = MasterPassword::class,
            parentColumns = ["id"],
            childColumns = ["masterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("masterId")]
)
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val masterId: Long,
    val textEnc: String,
    val copyCount: Int = 0,
    val updatedAt: Long = 0
)

@Dao
interface VaultDao {

    @Query("SELECT * FROM master_passwords ORDER BY lastUsedAt DESC, id ASC")
    fun masters(): Flow<List<MasterPassword>>

    @Query("SELECT * FROM entries WHERE masterId = :masterId")
    fun entries(masterId: Long): Flow<List<Entry>>

    @Insert
    suspend fun insertMaster(master: MasterPassword): Long

    @Insert
    suspend fun insertEntry(entry: Entry): Long

    @Query("UPDATE master_passwords SET lastUsedAt = :time WHERE id = :id")
    suspend fun touchMaster(id: Long, time: Long)

    @Query("UPDATE entries SET copyCount = copyCount + 1, updatedAt = :time WHERE id = :id")
    suspend fun bumpEntry(id: Long, time: Long)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    // 外键 CASCADE 级联删除该主密码下所有历史条目
    @Query("DELETE FROM master_passwords WHERE id = :id")
    suspend fun deleteMaster(id: Long)
}

@Database(entities = [MasterPassword::class, Entry::class], version = 1, exportSchema = false)
abstract class VaultDb : RoomDatabase() {

    abstract fun dao(): VaultDao

    companion object {
        @Volatile
        private var instance: VaultDb? = null

        fun get(context: Context): VaultDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    VaultDb::class.java,
                    "flowervault.db"
                ).build().also { instance = it }
            }
    }
}
