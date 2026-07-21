package com.huangder.lumibooks.mineru;

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
public final class MineruTokenStore_Factory implements Factory<MineruTokenStore> {
  private final Provider<Context> contextProvider;

  private MineruTokenStore_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public MineruTokenStore get() {
    return newInstance(contextProvider.get());
  }

  public static MineruTokenStore_Factory create(Provider<Context> contextProvider) {
    return new MineruTokenStore_Factory(contextProvider);
  }

  public static MineruTokenStore newInstance(Context context) {
    return new MineruTokenStore(context);
  }
}
