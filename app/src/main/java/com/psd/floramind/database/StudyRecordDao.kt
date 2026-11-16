package com.psd.floramind.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.psd.floramind.model.StudyRecord

/**
 * 旧版做题记录表。
 * 现在做题记录写在题库包的 practice.json 里，这张表只用于一次性迁移旧数据，
 * 迁移完成后会被清空（见 QuestionRepository.migrateLegacyStudyRecordsIfNeeded）。
 */
@Dao
interface StudyRecordDao {
    @Insert
    suspend fun insert(record: StudyRecord)

    @Query("SELECT * FROM study_records")
    suspend fun getAll(): List<StudyRecord>

    @Query("DELETE FROM study_records")
    suspend fun deleteAll()

    @Query("SELECT * FROM study_records WHERE questionId = :questionId ORDER BY timestamp DESC")
    suspend fun getRecordsForQuestion(questionId: Int): List<StudyRecord>

    @Query("SELECT * FROM study_records WHERE isCorrect = 0 ORDER BY timestamp DESC")
    suspend fun getMistakes(): List<StudyRecord>

    @Query("SELECT * FROM study_records WHERE isCorrect = 0 AND packageName = :packageName ORDER BY timestamp DESC")
    suspend fun getMistakesByPackage(packageName: String): List<StudyRecord>

    @Query("SELECT DISTINCT packageName FROM study_records WHERE isCorrect = 0")
    suspend fun getDistinctMistakePackages(): List<String>

    @Query("SELECT COUNT(*) FROM study_records WHERE isCorrect = 0 AND packageName = :packageName")
    suspend fun getMistakeCountByPackage(packageName: String): Int

    @Query("DELETE FROM study_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM study_records WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)

    @Query("UPDATE study_records SET packageName = :newPath WHERE packageName = :oldPath")
    suspend fun renameExactPackage(oldPath: String, newPath: String)
}