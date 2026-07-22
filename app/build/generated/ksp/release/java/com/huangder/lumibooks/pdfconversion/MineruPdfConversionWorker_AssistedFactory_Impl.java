package com.huangder.lumibooks.pdfconversion;

import android.content.Context;
import androidx.work.WorkerParameters;
import dagger.internal.DaggerGenerated;
import dagger.internal.InstanceFactory;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class MineruPdfConversionWorker_AssistedFactory_Impl implements MineruPdfConversionWorker_AssistedFactory {
  private final MineruPdfConversionWorker_Factory delegateFactory;

  MineruPdfConversionWorker_AssistedFactory_Impl(
      MineruPdfConversionWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public MineruPdfConversionWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<MineruPdfConversionWorker_AssistedFactory> create(
      MineruPdfConversionWorker_Factory delegateFactory) {
    return InstanceFactory.create(new MineruPdfConversionWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<MineruPdfConversionWorker_AssistedFactory> createFactoryProvider(
      MineruPdfConversionWorker_Factory delegateFactory) {
    return InstanceFactory.create(new MineruPdfConversionWorker_AssistedFactory_Impl(delegateFactory));
  }
}
