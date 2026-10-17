package com.behlul.liftlog.di

import com.behlul.liftlog.data.remote.AuthInterceptor
import com.behlul.liftlog.data.repository.AuthRepository
import com.behlul.liftlog.data.repository.ExerciseRepository
import com.behlul.liftlog.data.repository.RemoteAuthRepository
import com.behlul.liftlog.data.repository.RemoteExerciseRepository
import com.behlul.liftlog.data.repository.RemoteStatsRepository
import com.behlul.liftlog.data.repository.RemoteWorkoutRepository
import com.behlul.liftlog.data.repository.StatsRepository
import com.behlul.liftlog.data.repository.WorkoutRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import okhttp3.Interceptor

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindWorkoutRepository(impl: RemoteWorkoutRepository): WorkoutRepository

    @Binds
    abstract fun bindExerciseRepository(impl: RemoteExerciseRepository): ExerciseRepository

    @Binds
    abstract fun bindAuthRepository(impl: RemoteAuthRepository): AuthRepository

    @Binds
    abstract fun bindStatsRepository(impl: RemoteStatsRepository): StatsRepository

    @Binds
    @IntoSet
    abstract fun bindAuthInterceptor(impl: AuthInterceptor): Interceptor

    /** Declares the (possibly empty) set of OkHttp interceptors other modules can contribute to. */
    @Multibinds
    abstract fun interceptors(): Set<Interceptor>
}
