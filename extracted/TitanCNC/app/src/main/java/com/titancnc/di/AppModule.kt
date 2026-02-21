package com.titancnc.di

import android.content.Context
import com.titancnc.data.database.AppDatabase
import com.titancnc.data.database.ToolDao
import com.titancnc.service.ConnectionManager
import com.titancnc.service.GCodeSender
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideConnectionManager(
        @ApplicationContext context: Context
    ): ConnectionManager {
        return ConnectionManager(context)
    }
    
    @Provides
    @Singleton
    fun provideGCodeSender(
        connectionManager: ConnectionManager
    ): GCodeSender {
        return GCodeSender(connectionManager)
    }
    
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return AppDatabase.getDatabase(context)
    }
    
    @Provides
    @Singleton
    fun provideToolDao(
        database: AppDatabase
    ): ToolDao {
        return database.toolDao()
    }
}
