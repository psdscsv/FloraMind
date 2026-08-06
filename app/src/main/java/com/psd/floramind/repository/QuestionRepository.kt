package com.psd.floramind.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.psd.floramind.database.AppDatabase
import com.psd.floramind.model.Question
import com.psd.floramind.model.StudyRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.InputStreamReader

class QuestionRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val questionDao = db.questionDao()
    private val recordDao = db.studyRecordDao()

    suspend fun loadQuestionsFromAssets(context: Context) {
        if (questionDao.getAll().isNotEmpty()) return
        val inputStream = context.assets.open("question_bank.json")
        val reader = InputStreamReader(inputStream)
        val type = object : TypeToken<List<Question>>() {}.type
        val questions: List<Question> = Gson().fromJson(reader, type)
        questionDao.insertAll(questions)
    }

    suspend fun getAllQuestions(): List<Question> = questionDao.getAll()

    suspend fun getQuestionById(id: Int): Question? = questionDao.getById(id)

    suspend fun saveRecord(record: StudyRecord) {
        recordDao.insert(record)
    }

    suspend fun getMistakes(): List<StudyRecord> = recordDao.getMistakes()

    fun getRecordsForQuestion(questionId: Int): Flow<List<StudyRecord>> = flow {
        emit(recordDao.getRecordsForQuestion(questionId))
    }
}