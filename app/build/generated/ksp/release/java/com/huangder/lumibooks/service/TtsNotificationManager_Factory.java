package com.huangder.lumibooks.service;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
    "deprecation"
})
public final class TtsNotificationManager_Factory implements Factory<TtsNotificationManager> {
  private final Provider<Context> contextProvider;

  public TtsNotificationManager_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public TtsNotificationManager get() {
    return newInstance(contextProvider.get());
  }

  public static TtsNotificationManager_Factory create(Provider<Context> contextProvider) {
    return new TtsNotificationManager_Factory(contextProvider);
  }

  public static TtsNotificationManager newInstance(Context context) {
    return new TtsNotificationManager(context);
  }
}
