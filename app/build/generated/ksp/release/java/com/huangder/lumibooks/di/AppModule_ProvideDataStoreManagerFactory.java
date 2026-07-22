package com.huangder.lumibooks.di;

import android.content.Context;
import com.huangder.lumibooks.data.local.DataStoreManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class AppModule_ProvideDataStoreManagerFactory implements Factory<DataStoreManager> {
  private final Provider<Context> contextProvider;

  private AppModule_ProvideDataStoreManagerFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public DataStoreManager get() {
    return provideDataStoreManager(contextProvider.get());
  }

  public static AppModule_ProvideDataStoreManagerFactory create(Provider<Context> contextProvider) {
    return new AppModule_ProvideDataStoreManagerFactory(contextProvider);
  }

  public static DataStoreManager provideDataStoreManager(Context context) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideDataStoreManager(context));
  }
}
