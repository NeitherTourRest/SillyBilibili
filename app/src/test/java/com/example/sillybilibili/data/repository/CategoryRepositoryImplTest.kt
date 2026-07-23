package com.example.sillybilibili.data.repository

import com.example.sillybilibili.data.local.dao.CategoryDao
import com.example.sillybilibili.data.local.dao.VideoDao
import com.example.sillybilibili.data.local.entity.CategoryEntity
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CategoryRepositoryImplTest {

    private val categoryDao = mockk<CategoryDao>()
    private val videoDao = mockk<VideoDao>()
    private val repo = CategoryRepositoryImpl(categoryDao, videoDao)

    // ── getAllCategories ───────────────────────────────────────

    @Test
    fun `getAllCategories maps entities with video counts`() = runTest {
        val entity = CategoryEntity(id = 1, name = "Music", color = 0xFFFF0000, createdAt = 1000)
        every { categoryDao.getAllCategories() } returns flowOf(listOf(entity))
        coEvery { videoDao.getVideoCountByCategory(1) } returns 5

        val result = repo.getAllCategories().toList()
        val categories = result.first()

        assertEquals(1, categories.size)
        assertEquals("Music", categories[0].name)
        assertEquals(5, categories[0].videoCount)
        assertEquals(0xFFFF0000, categories[0].color)
    }

    @Test
    fun `getAllCategories returns empty when no categories`() = runTest {
        every { categoryDao.getAllCategories() } returns flowOf(emptyList())
        val result = repo.getAllCategories().toList()
        assertTrue(result.first().isEmpty())
    }

    // ── getCategoryById ────────────────────────────────────────

    @Test
    fun `getCategoryById returns category with video count`() = runTest {
        val entity = CategoryEntity(id = 2, name = "Gaming", color = 0xFF00FF00)
        coEvery { categoryDao.getCategoryById(2) } returns entity
        coEvery { videoDao.getVideoCountByCategory(2) } returns 3

        val cat = repo.getCategoryById(2)

        assertNotNull(cat)
        assertEquals("Gaming", cat!!.name)
        assertEquals(3, cat.videoCount)
    }

    @Test
    fun `getCategoryById returns null for missing category`() = runTest {
        coEvery { categoryDao.getCategoryById(999) } returns null
        assertNull(repo.getCategoryById(999))
    }

    // ── insertCategory ─────────────────────────────────────────

    @Test
    fun `insertCategory delegates to DAO and returns id`() = runTest {
        val cat = com.example.sillybilibili.domain.model.Category(name = "New", color = 0xFF0000FF)
        coEvery { categoryDao.insertCategory(any()) } returns 7L

        val id = repo.insertCategory(cat)
        assertEquals(7L, id)

        coVerify { categoryDao.insertCategory(match { it.name == "New" }) }
    }

    // ── updateCategory ─────────────────────────────────────────

    @Test
    fun `updateCategory delegates to DAO`() = runTest {
        val cat = com.example.sillybilibili.domain.model.Category(id = 5, name = "Updated", color = 0xFFFF0000)
        coEvery { categoryDao.updateCategory(any()) } just Runs

        repo.updateCategory(cat)
        coVerify { categoryDao.updateCategory(match { it.id == 5L && it.name == "Updated" }) }
    }

    // ── deleteCategory ─────────────────────────────────────────

    @Test
    fun `deleteCategory delegates deleteById to DAO`() = runTest {
        coEvery { categoryDao.deleteCategoryById(3) } just Runs

        repo.deleteCategory(3)
        coVerify { categoryDao.deleteCategoryById(3) }
    }
}
