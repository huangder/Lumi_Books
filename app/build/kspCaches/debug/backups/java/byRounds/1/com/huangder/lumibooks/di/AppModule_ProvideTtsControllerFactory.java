package com.huangder.lumibooks.di;

import com.huangder.lumibooks.data.local.DataStoreManager;
import com.huangder.lumibooks.tts.TtsController;
import com.huangder.lumibooks.tts.TtsEngine;
import com.huangder.lumibooks.tts.TtsTextExtractor;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
    "deprecation"
})
public final class AppModule_ProvideTtsControllerFactory implements Factory<TtsController> {
  private final Provider<TtsEngine> ttsEngineProvider;

  private final Provider<TtsTextExtractor> textExtractorProvider;

  private final Provider<DataStoreManager> dataStoreManagerProvider;

  public AppModule_ProvideTtsControllerFactory(Provider<TtsEngine> ttsEngineProvider,
      Provider<TtsTextExtractor> textExtractorProvider,
      Provider<DataStoreManager> dataStoreManagerProvider) {
    this.ttsEngineProvider = ttsEngineProvider;
    this.textExtractorProvider = textExtractorProvider;
    this.dataStoreManagerProvider = dataStoreManagerProvider;
  }

  @Override
  public TtsController get() {
    return provideTtsController(ttsEngineProvider.get(), textExtractorProvider.get(), dataStoreManagerProvider.get());
  }

  public static AppModule_ProvideTtsControllerFactory create(Provider<TtsEngine> ttsEngineProvider,
      Provider<TtsTextExtractor> textExtractorProvider,
      Provider<DataStoreManager> dataStoreManagerProvider) {
    return new AppModule_ProvideTtsControllerFactory(ttsEngineProvider, textExtractorProvider, dataStoreManagerProvider);
  }

  public static TtsController provideTtsController(TtsEngine ttsEngine,
      TtsTextExtractor textExtractor, DataStoreManager dataStoreManager) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideTtsController(ttsEngine, textExtractor, dataStoreManager));
  }
}
