package com.huangder.lumibooks.ui.settings;

import com.huangder.lumibooks.data.local.DataStoreManager;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

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
public final class SponsorActivity_MembersInjector implements MembersInjector<SponsorActivity> {
  private final Provider<DataStoreManager> dataStoreManagerProvider;

  private SponsorActivity_MembersInjector(Provider<DataStoreManager> dataStoreManagerProvider) {
    this.dataStoreManagerProvider = dataStoreManagerProvider;
  }

  @Override
  public void injectMembers(SponsorActivity instance) {
    injectDataStoreManager(instance, dataStoreManagerProvider.get());
  }

  public static MembersInjector<SponsorActivity> create(
      Provider<DataStoreManager> dataStoreManagerProvider) {
    return new SponsorActivity_MembersInjector(dataStoreManagerProvider);
  }

  @InjectedFieldSignature("com.huangder.lumibooks.ui.settings.SponsorActivity.dataStoreManager")
  public static void injectDataStoreManager(SponsorActivity instance,
      DataStoreManager dataStoreManager) {
    instance.dataStoreManager = dataStoreManager;
  }
}
