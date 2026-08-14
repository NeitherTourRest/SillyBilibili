package com.example.sillybilibili.di

// Repository 接口的实现
import com.example.sillybilibili.data.repository.CategoryRepositoryImpl
import com.example.sillybilibili.data.repository.VideoRepositoryImpl
import com.example.sillybilibili.service.BilibiliOnlineVideoStatusRemoteDataSource
import com.example.sillybilibili.service.OnlineVideoStatusRemoteDataSource
// Repository 接口定义（抽象层）
import com.example.sillybilibili.domain.repository.CategoryRepository
import com.example.sillybilibili.domain.repository.VideoRepository
// @Binds = Hilt 注解：告诉 Hilt "当别人需要接口时，用这个实现"
import dagger.Binds
// @Module = Hilt 注解：这个类提供依赖
import dagger.Module
// @InstallIn = 安装到哪个组件
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
// @Singleton = 全局单例
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindVideoRepository(impl: VideoRepositoryImpl): VideoRepository

    @Binds
    @Singleton
    abstract fun bindOnlineVideoStatusRemoteDataSource(
        impl: BilibiliOnlineVideoStatusRemoteDataSource
    ): OnlineVideoStatusRemoteDataSource
}
