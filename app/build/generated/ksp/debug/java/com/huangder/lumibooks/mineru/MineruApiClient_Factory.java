package com.huangder.lumibooks.mineru;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
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
public final class MineruApiClient_Factory implements Factory<MineruApiClient> {
  @Override
  public MineruApiClient get() {
    return newInstance();
  }

  public static MineruApiClient_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static MineruApiClient newInstance() {
    return new MineruApiClient();
  }

  private static final class InstanceHolder {
    private static final MineruApiClient_Factory INSTANCE = new MineruApiClient_Factory();
  }
}
