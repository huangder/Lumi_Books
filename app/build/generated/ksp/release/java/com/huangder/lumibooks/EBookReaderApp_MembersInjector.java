package com.huangder.lumibooks;

import androidx.hilt.work.HiltWorkerFactory;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class EBookReaderApp_MembersInjector implements MembersInjector<EBookReaderApp> {
  private final Provider<HiltWorkerFactory> workerFactoryProvider;

  public EBookReaderApp_MembersInjector(Provider<HiltWorkerFactory> workerFactoryProvider) {
    this.workerFactoryProvider = workerFactoryProvider;
  }

  public static MembersInjector<EBookReaderApp> create(
      Provider<HiltWorkerFactory> workerFactoryProvider) {
    return new EBookReaderApp_MembersInjector(workerFactoryProvider);
  }

  @Override
  public void injectMembers(EBookReaderApp instance) {
    injectWorkerFactory(instance, workerFactoryProvider.get());
  }

  @InjectedFieldSignature("com.huangder.lumibooks.EBookReaderApp.workerFactory")
  public static void injectWorkerFactory(EBookReaderApp instance, HiltWorkerFactory workerFactory) {
    instance.workerFactory = workerFactory;
  }
}
