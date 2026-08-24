package com.example.sillybilibili.ui.pages.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sillybilibili.domain.model.Category
import com.example.sillybilibili.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val message: String? = null
)

/** Returns a user-facing validation error, or null when [rawName] can be saved. */
internal fun categoryNameError(
    rawName: String,
    categories: List<Category>,
    editingCategoryId: Long? = null
): String? {
    val name = rawName.trim()
    if (name.isBlank()) return "请输入分类名称"
    if (name.length > CATEGORY_NAME_MAX_LENGTH) return "分类名称最多 $CATEGORY_NAME_MAX_LENGTH 个字符"
    val duplicated = categories.any { it.id != editingCategoryId && it.name.trim().equals(name, ignoreCase = true) }
    return if (duplicated) "已存在同名分类" else null
}

internal const val CATEGORY_NAME_MAX_LENGTH = 24

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            categoryRepository.getAllCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories, isLoading = false) }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun validateCategoryName(name: String, editingCategoryId: Long? = null): String? =
        categoryNameError(name, _uiState.value.categories, editingCategoryId)

    /** Saves a new category after synchronous validation. Returns false when no write was scheduled. */
    fun addCategory(name: String, color: Long): Boolean {
        val cleanName = name.trim()
        categoryNameError(cleanName, _uiState.value.categories)?.let { error ->
            postMessage(error)
            return false
        }
        viewModelScope.launch {
            categoryRepository.insertCategory(
                Category(
                    name = cleanName,
                    color = color
                )
            )
            postMessage("已创建分类“$cleanName”")
        }
        return true
    }

    /** Updates name and color without allowing accidental duplicate names. */
    fun updateCategory(id: Long, name: String, color: Long): Boolean {
        val cleanName = name.trim()
        categoryNameError(cleanName, _uiState.value.categories, id)?.let { error ->
            postMessage(error)
            return false
        }
        viewModelScope.launch {
            val existingCategory = categoryRepository.getCategoryById(id)
            existingCategory?.let {
                categoryRepository.updateCategory(
                    it.copy(name = cleanName, color = color)
                )
                postMessage("已保存分类“$cleanName”")
            }
        }
        return true
    }

    /** The Room foreign key safely clears categoryId on affected videos; media files stay untouched. */
    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            categoryRepository.deleteCategory(category.id)
            postMessage(
                if (category.videoCount > 0) "已删除“${category.name}”，显示中的 ${category.videoCount} 个视频已移至未分类"
                else "已删除分类“${category.name}”"
            )
        }
    }

    fun clearMessage() { _uiState.update { it.copy(message = null) } }

    private fun postMessage(message: String) { _uiState.update { it.copy(message = message) } }
}
