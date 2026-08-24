package com.example.sillybilibili.ui.pages.categories

import com.example.sillybilibili.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryNameValidationTest {

    private val categories = listOf(Category(id = 1, name = "动画", color = 0L))

    @Test
    fun `category name trims whitespace before duplicate check`() {
        assertEquals("已存在同名分类", categoryNameError("  动画  ", categories))
    }

    @Test
    fun `editing category can keep its own name`() {
        assertNull(categoryNameError("动画", categories, editingCategoryId = 1))
    }

    @Test
    fun `category name rejects blank and overlong values`() {
        assertEquals("请输入分类名称", categoryNameError("  ", categories))
        assertEquals("分类名称最多 24 个字符", categoryNameError("a".repeat(25), categories))
    }
}
