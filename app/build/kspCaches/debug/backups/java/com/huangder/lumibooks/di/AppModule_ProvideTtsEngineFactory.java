package com.huangder.lumibooks.di;

import android.content.Context;
import com.huangder.lumibooks.tts.TtsEngine;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class AppModule_ProvideTtsEngineFactory implements Factory<TtsEngine> {
  private final Provider<Context> contextProvider;

  private AppModule_ProvideTtsEngineFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public TtsEngine get() {
    return provideTtsEngine(contextProvider.get());
  }

  public static AppModule_ProvideTtsEngineFactory create(Provider<Context> contextProvider) {
    return new AppModule_ProvideTtsEngineFactory(contextProvider);
  }

  public static TtsEngine provideTtsEngine(Context context) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideTtsEngine(context));
  }
}
