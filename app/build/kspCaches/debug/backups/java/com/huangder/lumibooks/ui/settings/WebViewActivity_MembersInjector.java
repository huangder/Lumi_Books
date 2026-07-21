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
public final class WebViewActivity_MembersInjector implements MembersInjector<WebViewActivity> {
  private final Provider<DataStoreManager> dataStoreManagerProvider;

  private WebViewActivity_MembersInjector(Provider<DataStoreManager> dataStoreManagerProvider) {
    this.dataStoreManagerProvider = dataStoreManagerProvider;
  }

  @Override
  public void injectMembers(WebViewActivity instance) {
    injectDataStoreManager(instance, dataStoreManagerProvider.get());
  }

  public static MembersInjector<WebViewActivity> create(
      Provider<DataStoreManager> dataStoreManagerProvider) {
    return new WebViewActivity_MembersInjector(dataStoreManagerProvider);
  }

  @InjectedFieldSignature("com.huangder.lumibooks.ui.settings.WebViewActivity.dataStoreManager")
  public static void injectDataStoreManager(WebViewActivity instance,
      DataStoreManager dataStoreManager) {
    instance.dataStoreManager = dataStoreManager;
  }
}
