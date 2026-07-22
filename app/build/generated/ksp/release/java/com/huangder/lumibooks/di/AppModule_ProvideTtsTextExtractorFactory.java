package com.huangder.lumibooks.di;

import com.huangder.lumibooks.tts.TtsTextExtractor;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
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
public final class AppModule_ProvideTtsTextExtractorFactory implements Factory<TtsTextExtractor> {
  @Override
  public TtsTextExtractor get() {
    return provideTtsTextExtractor();
  }

  public static AppModule_ProvideTtsTextExtractorFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static TtsTextExtractor provideTtsTextExtractor() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideTtsTextExtractor());
  }

  private static final class InstanceHolder {
    static final AppModule_ProvideTtsTextExtractorFactory INSTANCE = new AppModule_ProvideTtsTextExtractorFactory();
  }
}
