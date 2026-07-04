package com.huangder.lumibooks;

import com.huangder.lumibooks.data.local.DataStoreManager;
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
public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<DataStoreManager> dataStoreManagerProvider;

  public MainActivity_MembersInjector(Provider<DataStoreManager> dataStoreManagerProvider) {
    this.dataStoreManagerProvider = dataStoreManagerProvider;
  }

  public static MembersInjector<MainActivity> create(
      Provider<DataStoreManager> dataStoreManagerProvider) {
    return new MainActivity_MembersInjector(dataStoreManagerProvider);
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectDataStoreManager(instance, dataStoreManagerProvider.get());
  }

  @InjectedFieldSignature("com.huangder.lumibooks.MainActivity.dataStoreManager")
  public static void injectDataStoreManager(MainActivity instance,
      DataStoreManager dataStoreManager) {
    instance.dataStoreManager = dataStoreManager;
  }
}
