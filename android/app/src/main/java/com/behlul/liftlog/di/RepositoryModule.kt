package com.behlul.liftlog.di

import com.behlul.liftlog.data.repository.ExerciseRepository
import com.behlul.liftlog.data.repository.RemoteExerciseRepository
import com.behlul.liftlog.data.repository.RemoteWorkoutRepository
import com.behlul.liftlog.data.repository.WorkoutRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import okhttp3.Interceptor

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindWorkoutRepository(impl: RemoteWorkoutRepository): WorkoutRepository

    @Binds
    abstract fun bindExerciseRepository(impl: RemoteExerciseRepository): ExerciseRepository

    /** Declares the (possibly empty) set of OkHttp interceptors other modules can contribute to. */
    @Multibinds
    abstract fun interceptors(): Set<Interceptor>
}
