package com.autodroid.di

import android.content.Context
import com.autodroid.adapter.*
import com.autodroid.server.AdapterContainer
import com.autodroid.service.AccessibilityServiceProvider
import com.autodroid.service.DefaultAccessibilityServiceProvider
import com.autodroid.service.ScreenCaptureManager
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
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideAccessibilityServiceProvider(): AccessibilityServiceProvider =
        DefaultAccessibilityServiceProvider()

    @Provides
    @Singleton
    fun provideScreenCaptureManager(provider: AccessibilityServiceProvider): ScreenCaptureManager =
        ScreenCaptureManager(provider)

    @Provides
    @Singleton
    fun provideAppAdapter(
        @ApplicationContext context: Context,
        provider: AccessibilityServiceProvider,
    ): AppAdapter = AppAdapter(context, provider)

    @Provides
    @Singleton
    fun provideDeviceAdapter(
        @ApplicationContext context: Context,
        screenCapture: ScreenCaptureManager,
    ): DeviceAdapter = DeviceAdapter(context, screenCapture)

    @Provides
    @Singleton
    fun provideShellAdapter(): ShellAdapter = ShellAdapter()

    @Provides
    @Singleton
    fun provideAutomatorAdapter(provider: AccessibilityServiceProvider): AutomatorAdapter =
        AutomatorAdapter(provider)

    @Provides
    @Singleton
    fun provideEventAdapter(): EventAdapter = EventAdapter()

    @Provides
    @Singleton
    fun provideFileAdapter(): FileAdapter = FileAdapter()

    @Provides
    @Singleton
    fun provideAdapterContainer(
        app: AppAdapter,
        automator: AutomatorAdapter,
        device: DeviceAdapter,
        shell: ShellAdapter,
        events: EventAdapter,
        file: FileAdapter,
    ): AdapterContainer = AdapterContainer(app, automator, device, shell, events, file)
}
