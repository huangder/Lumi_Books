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
public final class FeedbackActivity_MembersInjector implements MembersInjector<FeedbackActivity> {
  private final Provider<DataStoreManager> dataStoreManagerProvider;

  private FeedbackActivity_MembersInjector(Provider<DataStoreManager> dataStoreManagerProvider) {
    this.dataStoreManagerProvider = dataStoreManagerProvider;
  }

  @Override
  public void injectMembers(FeedbackActivity instance) {
    injectDataStoreManager(instance, dataStoreManagerProvider.get());
  }

  public static MembersInjector<FeedbackActivity> create(
      Provider<DataStoreManager> dataStoreManagerProvider) {
    return new FeedbackActivity_MembersInjector(dataStoreManagerProvider);
  }

  @InjectedFieldSignature("com.huangder.lumibooks.ui.settings.FeedbackActivity.dataStoreManager")
  public static void injectDataStoreManager(FeedbackActivity instance,
      DataStoreManager dataStoreManager) {
    instance.dataStoreManager = dataStoreManager;
  }
}
