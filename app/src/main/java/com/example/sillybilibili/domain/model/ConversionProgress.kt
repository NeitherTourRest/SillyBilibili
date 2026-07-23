// ============================================================
// ConversionProgress.kt — 视频转换进度的数据模型
// ============================================================
// 当用户把 .m4s 转为 .mp4 时，转换过程会实时发送进度信息。
// ConversionProgress 就是这条"进度消息"的数据结构。
// 被 VideoDetailPage 用于显示进度条 / 完成 / 失败状态。
// ============================================================

package com.example.sillybilibili.domain.model

// progress = 0.0 ~ 1.0（0% ~ 100%）
// status = 当前状态（排队中 / 转换中 / 完成 / 失败）
// outputPath = 完成时输出的 .mp4 文件路径
// errorMessage = 失败时的错误描述
data class ConversionProgress(
    val videoId: Long,
    val videoName: String,
    val progress: Float,
    val status: ConversionStatus,
    val outputPath: String? = null,
    val errorMessage: String? = null
)

// 转换状态枚举 — 只有这 4 种可能
enum class ConversionStatus {
    PENDING,     // 排队等待
    CONVERTING,  // 正在转换
    COMPLETED,   // 转换完成
    FAILED       // 转换失败
}
