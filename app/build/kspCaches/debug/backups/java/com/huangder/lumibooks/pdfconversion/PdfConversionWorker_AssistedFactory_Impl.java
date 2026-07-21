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
public final class PdfConversionWorker_AssistedFactory_Impl implements PdfConversionWorker_AssistedFactory {
  private final PdfConversionWorker_Factory delegateFactory;

  PdfConversionWorker_AssistedFactory_Impl(PdfConversionWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public PdfConversionWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<PdfConversionWorker_AssistedFactory> create(
      PdfConversionWorker_Factory delegateFactory) {
    return InstanceFactory.create(new PdfConversionWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<PdfConversionWorker_AssistedFactory> createFactoryProvider(
      PdfConversionWorker_Factory delegateFactory) {
    return InstanceFactory.create(new PdfConversionWorker_AssistedFactory_Impl(delegateFactory));
  }
}
