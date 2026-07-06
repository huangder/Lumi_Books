package com.huangder.lumibooks.ui.settings;

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
public final class DetailActivity_MembersInjector implements MembersInjector<DetailActivity> {
  private final Provider<DataStoreManager> dataStoreManagerProvider;

  public DetailActivity_MembersInjector(Provider<DataStoreManager> dataStoreManagerProvider) {
    this.dataStoreManagerProvider = dataStoreManagerProvider;
  }

  public static MembersInjector<DetailActivity> create(
      Provider<DataStoreManager> dataStoreManagerProvider) {
    return new DetailActivity_MembersInjector(dataStoreManagerProvider);
  }

  @Override
  public void injectMembers(DetailActivity instance) {
    injectDataStoreManager(instance, dataStoreManagerProvider.get());
  }

  @InjectedFieldSignature("com.huangder.lumibooks.ui.settings.DetailActivity.dataStoreManager")
  public static void injectDataStoreManager(DetailActivity instance,
      DataStoreManager dataStoreManager) {
    instance.dataStoreManager = dataStoreManager;
  }
}
