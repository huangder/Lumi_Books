package com.huangder.lumibooks.service;

import com.huangder.lumibooks.tts.TtsController;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class TtsForegroundService_MembersInjector implements MembersInjector<TtsForegroundService> {
  private final Provider<TtsController> ttsControllerProvider;

  private final Provider<TtsNotificationManager> notificationManagerProvider;

  private TtsForegroundService_MembersInjector(Provider<TtsController> ttsControllerProvider,
      Provider<TtsNotificationManager> notificationManagerProvider) {
    this.ttsControllerProvider = ttsControllerProvider;
    this.notificationManagerProvider = notificationManagerProvider;
  }

  @Override
  public void injectMembers(TtsForegroundService instance) {
    injectTtsController(instance, ttsControllerProvider.get());
    injectNotificationManager(instance, notificationManagerProvider.get());
  }

  public static MembersInjector<TtsForegroundService> create(
      Provider<TtsController> ttsControllerProvider,
      Provider<TtsNotificationManager> notificationManagerProvider) {
    return new TtsForegroundService_MembersInjector(ttsControllerProvider, notificationManagerProvider);
  }

  @InjectedFieldSignature("com.huangder.lumibooks.service.TtsForegroundService.ttsController")
  public static void injectTtsController(TtsForegroundService instance,
      TtsController ttsController) {
    instance.ttsController = ttsController;
  }

  @InjectedFieldSignature("com.huangder.lumibooks.service.TtsForegroundService.notificationManager")
  public static void injectNotificationManager(TtsForegroundService instance,
      TtsNotificationManager notificationManager) {
    instance.notificationManager = notificationManager;
  }
}
