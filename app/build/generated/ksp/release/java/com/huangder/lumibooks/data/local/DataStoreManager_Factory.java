package com.huangder.lumibooks.data.local;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class DataStoreManager_Factory implements Factory<DataStoreManager> {
  private final Provider<Context> contextProvider;

  private DataStoreManager_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public DataStoreManager get() {
    return newInstance(contextProvider.get());
  }

  public static DataStoreManager_Factory create(Provider<Context> contextProvider) {
    return new DataStoreManager_Factory(contextProvider);
  }

  public static DataStoreManager newInstance(Context context) {
    return new DataStoreManager(context);
  }
}
