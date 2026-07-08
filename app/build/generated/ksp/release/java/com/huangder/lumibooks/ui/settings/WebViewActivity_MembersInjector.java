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
public final class WebViewActivity_MembersInjector implements MembersInjector<WebViewActivity> {
  private final Provider<DataStoreManager> dataStoreManagerProvider;

  public WebViewActivity_MembersInjector(Provider<DataStoreManager> dataStoreManagerProvider) {
    this.dataStoreManagerProvider = dataStoreManagerProvider;
  }

  public static MembersInjector<WebViewActivity> create(
      Provider<DataStoreManager> dataStoreManagerProvider) {
    return new WebViewActivity_MembersInjector(dataStoreManagerProvider);
  }

  @Override
  public void injectMembers(WebViewActivity instance) {
    injectDataStoreManager(instance, dataStoreManagerProvider.get());
  }

  @InjectedFieldSignature("com.huangder.lumibooks.ui.settings.WebViewActivity.dataStoreManager")
  public static void injectDataStoreManager(WebViewActivity instance,
      DataStoreManager dataStoreManager) {
    instance.dataStoreManager = dataStoreManager;
  }
}
