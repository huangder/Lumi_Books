package com.huangder.lumibooks.ui.welcome;

import com.huangder.lumibooks.data.local.DataStoreManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
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
public final class WelcomeViewModel_Factory implements Factory<WelcomeViewModel> {
  private final Provider<DataStoreManager> dataStoreManagerProvider;

  private WelcomeViewModel_Factory(Provider<DataStoreManager> dataStoreManagerProvider) {
    this.dataStoreManagerProvider = dataStoreManagerProvider;
  }

  @Override
  public WelcomeViewModel get() {
    return newInstance(dataStoreManagerProvider.get());
  }

  public static WelcomeViewModel_Factory create(
      Provider<DataStoreManager> dataStoreManagerProvider) {
    return new WelcomeViewModel_Factory(dataStoreManagerProvider);
  }

  public static WelcomeViewModel newInstance(DataStoreManager dataStoreManager) {
    return new WelcomeViewModel(dataStoreManager);
  }
}
