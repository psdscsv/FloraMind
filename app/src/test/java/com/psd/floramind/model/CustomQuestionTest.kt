package com.psd.floramind.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.psd.floramind.database.Converters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 题库包自定义题型（question_types.json + 多小问题目）的解析回归测试。
 * 题库是外部导入的 JSON，字段写错也不能让 App 崩。
 */
class CustomQuestionTest {

    @Test
    fun parseTypeDefinitions_array() {
        val json = """
            [
              {"id":"multi_part","name":"多小问","layout":"parts","grading":"self"},
              {"id":"case","name":"案例","layout":"single","grading":"ai"}
            ]
        """.trimIndent()
        val type = object : TypeToken<List<CustomQuestionType>>() {}.type
        val types: List<CustomQuestionType> = Gson().fromJson(json, type)

        assertEquals(2, types.size)
        assertEquals("多小问", types[0].name)
        assertTrue(types[0].isPartsLayout)
        assertFalse(types[0].useAiGrading)
        assertTrue(types[1].isSingleLayout)
        assertTrue(types[1].useAiGrading)
    }

    @Test
    fun parseTypeDefinition_singleObject() {
        val json = """{"id":"multi_part","name":"多小问"}"""
        val type = Gson().fromJson(json, CustomQuestionType::class.java)
        assertEquals("multi_part", type.id)
        assertTrue("缺省 layout 应按 parts 处理", type.isPartsLayout)
        assertFalse("缺省 grading 应按 self 处理", type.useAiGrading)
    }

    @Test
    fun parseCustomQuestion_withParts() {
        val json = """
            [{
              "id": 1,
              "type": "CUSTOM",
              "customType": "multi_part",
              "stem": "总题干",
              "parts": [
                {"stem": "（1）第一问", "answer": "答案一", "keywords": ["k1"]},
                {"stem": "（2）第二问", "answer": "答案二"}
              ]
            }]
        """.trimIndent()
        val type = object : TypeToken<List<Question>>() {}.type
        val question: Question = Gson().fromJson<List<Question>>(json, type).first()

        assertEquals(QuestionType.CUSTOM, question.type)
        assertEquals("multi_part", question.customType)
        assertEquals(2, question.parts?.size)
        assertEquals("答案一", question.parts?.get(0)?.answer)
        assertEquals(listOf("k1"), question.parts?.get(0)?.keywords)
        assertEquals(2, question.resolvedParts().size)
    }

    @Test
    fun resolvedParts_fallsBackToWholeQuestion() {
        val question = Question(
            id = 2,
            type = QuestionType.CUSTOM,
            stem = "整题题干",
            correctAnswer = "整题答案",
            customType = "multi_part"
        )
        val parts = question.resolvedParts()
        assertEquals(1, parts.size)
        assertEquals("整题题干", parts[0].stem)
        assertEquals("整题答案", parts[0].answer)
    }

    @Test
    fun resolvedParts_skipsEmptyParts() {
        val question = Question(
            id = 3,
            type = QuestionType.CUSTOM,
            stem = "总题干",
            parts = listOf(
                QuestionPart(stem = "", answer = null),
                QuestionPart(stem = "（1）有内容", answer = "答案")
            )
        )
        val parts = question.resolvedParts()
        assertEquals(1, parts.size)
        assertEquals("（1）有内容", parts[0].stem)
    }

    @Test
    fun composePartsAnswer_numbersOnlyFilledAnswers() {
        val question = Question(
            id = 5,
            type = QuestionType.CUSTOM,
            stem = "总题干",
            parts = listOf(
                QuestionPart("（1）"),
                QuestionPart("（2）"),
                QuestionPart("（3）")
            )
        )
        assertEquals(
            "（1）甲\n（3）丙",
            question.composePartsAnswer(listOf("甲", "   ", "丙"))
        )
        assertEquals("", question.composePartsAnswer(emptyList()))
    }

    @Test
    fun converters_roundTripParts() {
        val converters = Converters()
        val parts = listOf(
            QuestionPart("（1）", "答案", listOf("要点")),
            QuestionPart("（2）", "答案二")
        )
        val text = converters.fromQuestionPartList(parts)
        assertTrue(text != null && text.contains("（1）"))
        assertEquals(parts, converters.toQuestionPartList(text))
        assertNull(converters.fromQuestionPartList(null))
    }

    @Test
    fun sanitize_dropsTypeWithoutId() {
        assertNull(CustomQuestionType(id = "  ").sanitizedOrNull())
    }

    @Test
    fun sanitize_fillsDefaults() {
        val cleaned = CustomQuestionType(id = " multi_part ", name = "", layout = "", grading = "")
            .sanitizedOrNull()
        assertEquals("multi_part", cleaned?.id)
        assertEquals("multi_part", cleaned?.name)
        assertEquals("parts", cleaned?.layout)
        assertEquals("self", cleaned?.grading)
    }

    @Test
    fun safeName_neverBlank() {
        assertEquals("多小问", CustomQuestionType(id = "a", name = "多小问").safeName())
        assertEquals("a", CustomQuestionType(id = "a").safeName())
        assertEquals("自定义题型", CustomQuestionType().safeName())
    }

    @Test
    fun parseMultiChoiceQuestion() {
        val json = """
            [{
              "id": 5, "type": "CUSTOM", "customType": "multi_choice",
              "stem": "下列属于昆虫纲特征的有（ ）",
              "options": ["头胸腹三部分", "三对足", "两对翅", "用鳃呼吸"],
              "correctOptions": ["头胸腹三部分", "三对足"]
            }]
        """.trimIndent()
        val type = object : TypeToken<List<Question>>() {}.type
        val question: Question = Gson().fromJson<List<Question>>(json, type).first()

        assertEquals(listOf("头胸腹三部分", "三对足"), question.correctOptions)
        assertTrue("顺序无关，选全即对", question.isMultiChoiceCorrect(listOf("三对足", "头胸腹三部分")))
        assertFalse("少选算错", question.isMultiChoiceCorrect(listOf("头胸腹三部分")))
        assertFalse(
            "多选算错",
            question.isMultiChoiceCorrect(listOf("头胸腹三部分", "三对足", "两对翅"))
        )
        assertEquals(listOf("A", "B"), question.correctOptionLetters())
        assertEquals("A" to "头胸腹三部分", question.correctOptionEntries().first())
    }

    @Test
    fun multiChoice_withoutCorrectOptions_neverCorrect() {
        val question = Question(
            id = 6,
            type = QuestionType.CUSTOM,
            stem = "题干",
            options = listOf("甲", "乙")
        )
        assertFalse(question.isMultiChoiceCorrect(listOf("甲")))
        assertTrue(question.correctOptionLetters().isEmpty())
    }

    @Test
    fun customLayout_parsing() {
        assertEquals(CustomLayout.MULTI_CHOICE, CustomLayout.from("multi_choice"))
        assertEquals(CustomLayout.IMAGE_TEXT, CustomLayout.from("IMAGE_TEXT"))
        assertEquals(CustomLayout.SINGLE, CustomLayout.from(" single "))
        assertEquals("未知 layout 按 parts 兜底", CustomLayout.PARTS, CustomLayout.from("unknown"))
        assertEquals(CustomLayout.PARTS, CustomLayout.from(null))

        val multi = CustomQuestionType(id = "x", layout = "multi_choice")
        assertTrue(multi.isMultiChoiceLayout)
        assertFalse(multi.isPartsLayout)

        val image = CustomQuestionType(id = "y", layout = "image_text")
        assertTrue(image.isImageTextLayout)
        assertFalse(image.isSingleLayout)
    }

    @Test
    fun parseImageTextQuestion() {
        val json = """
            [{
              "id": 7, "type": "CUSTOM", "customType": "image_text",
              "stem": "观察图片作答",
              "imageName": "images/paramecium.png",
              "correctAnswer": "参考答案",
              "keywords": ["纤毛"]
            }]
        """.trimIndent()
        val type = object : TypeToken<List<Question>>() {}.type
        val question: Question = Gson().fromJson<List<Question>>(json, type).first()
        assertEquals("images/paramecium.png", question.imageName)
        assertEquals("参考答案", question.correctAnswer)
        assertEquals(listOf("纤毛"), question.keywords)
    }
}
