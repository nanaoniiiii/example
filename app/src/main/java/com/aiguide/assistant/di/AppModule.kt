package com.aiguide.assistant.di

import android.content.Context
import com.aiguide.assistant.engine.AssistModeManager
import com.aiguide.assistant.engine.DeviceProfile
import com.aiguide.assistant.engine.FlashlightManager
import com.aiguide.assistant.engine.HazardDetector
import com.aiguide.assistant.engine.NavSafetyEngine
import com.aiguide.assistant.engine.NavigationListener
import com.aiguide.assistant.engine.PerformanceOptimizer
import com.aiguide.assistant.engine.TtsManager
import com.aiguide.assistant.engine.VisionEngine
import com.aiguide.assistant.engine.VoiceEngine
import com.aiguide.assistant.overlay.PrivacyOverlayManager
import com.aiguide.assistant.service.NotificationHelper
import com.aiguide.assistant.service.ServiceBus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI 模块：提供所有核心单例依赖。
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideServiceBus(): ServiceBus = ServiceBus()

    @Provides
    @Singleton
    fun provideNotificationHelper(
        @ApplicationContext context: Context
    ): NotificationHelper = NotificationHelper(context)

    @Provides
    @Singleton
    fun providePrivacyOverlayManager(
        @ApplicationContext context: Context,
        serviceBus: ServiceBus
    ): PrivacyOverlayManager = PrivacyOverlayManager(context, serviceBus)

    @Provides
    @Singleton
    fun provideAssistModeManager(
        serviceBus: ServiceBus
    ): AssistModeManager = AssistModeManager(serviceBus)

    @Provides
    @Singleton
    fun provideVoiceEngine(
        serviceBus: ServiceBus
    ): VoiceEngine = VoiceEngine(serviceBus)

    @Provides
    @Singleton
    fun provideTtsManager(
        @ApplicationContext context: Context,
        serviceBus: ServiceBus
    ): TtsManager = TtsManager(context, serviceBus)

    @Provides
    @Singleton
    fun provideVisionEngine(
        @ApplicationContext context: Context,
        serviceBus: ServiceBus
    ): VisionEngine = VisionEngine(context, serviceBus)

    @Provides
    @Singleton
    fun provideNavigationListener(
        serviceBus: ServiceBus
    ): NavigationListener = NavigationListener(serviceBus)

    @Provides
    @Singleton
    fun provideNavSafetyEngine(
        serviceBus: ServiceBus
    ): NavSafetyEngine = NavSafetyEngine(serviceBus)

    @Provides
    @Singleton
    fun provideHazardDetector(
        @ApplicationContext context: Context,
        serviceBus: ServiceBus
    ): HazardDetector = HazardDetector(context, serviceBus)

    @Provides
    @Singleton
    fun provideFlashlightManager(
        @ApplicationContext context: Context,
        serviceBus: ServiceBus
    ): FlashlightManager = FlashlightManager(context, serviceBus)

    @Provides
    @Singleton
    fun provideAutoEngine(
        serviceBus: ServiceBus,
        ttsManager: TtsManager,
        visionEngine: VisionEngine
    ): AutoEngine = AutoEngine(serviceBus, ttsManager, visionEngine)

    @Provides
    @Singleton
    fun provideSafetyGuard(
        serviceBus: ServiceBus
    ): SafetyGuard = SafetyGuard(serviceBus)

    // ========================
    // Phase 4: 性能适配
    // ========================

    @Provides
    @Singleton
    fun provideDeviceProfile(
        @ApplicationContext context: Context
    ): DeviceProfile = DeviceProfile(context)

    @Provides
    @Singleton
    fun providePerformanceOptimizer(
        @ApplicationContext context: Context,
        deviceProfile: DeviceProfile,
        serviceBus: ServiceBus
    ): PerformanceOptimizer = PerformanceOptimizer(context, deviceProfile, serviceBus)
}
