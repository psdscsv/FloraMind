package com.psd.floramind.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.psd.floramind.model.StudyRecord

@Dao
interface StudyRecordDao {
    @Insert
    suspend fun insert(record: StudyRecord)

    @Query("SELECT * FROM study_records WHERE questionId = :questionId ORDER BY timestamp DESC")
    suspend fun getRecordsForQuestion(questionId: Int): List<StudyRecord>

    @Query("SELECT * FROM study_records WHERE isCorrect = 0 ORDER BY timestamp DESC")
    suspend fun getMistakes(): List<StudyRecord>
}