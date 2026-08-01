package com.builtdifferent.audio8d.di

import android.content.Context
import androidx.room.Room
import com.builtdifferent.audio8d.data.db.AppDatabase
import com.builtdifferent.audio8d.data.db.ConversionDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            // Do NOT use fallbackToDestructiveMigration here: this app's whole
            // point is a permanent, never-lost converted library, so a schema
            // bump must ship a real androidx.room.migration.Migration (added to
            // this builder via .addMigrations(...)) rather than wiping Room's
            // table and silently deleting every converted song's metadata.
            .build()

    @Provides
    fun provideConversionDao(db: AppDatabase): ConversionDao = db.conversionDao()
}
