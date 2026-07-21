package com.huangder.lumibooks.pdfconversion;

import androidx.hilt.work.WorkerAssistedFactory;
import androidx.work.ListenableWorker;
import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.codegen.OriginatingElement;
import dagger.hilt.components.SingletonComponent;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;
import javax.annotation.processing.Generated;

@Generated("androidx.hilt.AndroidXHiltProcessor")
@Module
@InstallIn(SingletonComponent.class)
@OriginatingElement(
    topLevelClass = PdfConversionWorker.class
)
public interface PdfConversionWorker_HiltModule {
  @Binds
  @IntoMap
  @StringKey("com.huangder.lumibooks.pdfconversion.PdfConversionWorker")
  WorkerAssistedFactory<? extends ListenableWorker> bind(
      PdfConversionWorker_AssistedFactory factory);
}
