package com.huangder.lumibooks.ui.welcome;

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
public final class WelcomeActivity_MembersInjector implements MembersInjector<WelcomeActivity> {
  private final Provider<DataStoreManager> dataStoreManagerProvider;

  public WelcomeActivity_MembersInjector(Provider<DataStoreManager> dataStoreManagerProvider) {
    this.dataStoreManagerProvider = dataStoreManagerProvider;
  }

  public static MembersInjector<WelcomeActivity> create(
      Provider<DataStoreManager> dataStoreManagerProvider) {
    return new WelcomeActivity_MembersInjector(dataStoreManagerProvider);
  }

  @Override
  public void injectMembers(WelcomeActivity instance) {
    injectDataStoreManager(instance, dataStoreManagerProvider.get());
  }

  @InjectedFieldSignature("com.huangder.lumibooks.ui.welcome.WelcomeActivity.dataStoreManager")
  public static void injectDataStoreManager(WelcomeActivity instance,
      DataStoreManager dataStoreManager) {
    instance.dataStoreManager = dataStoreManager;
  }
}
