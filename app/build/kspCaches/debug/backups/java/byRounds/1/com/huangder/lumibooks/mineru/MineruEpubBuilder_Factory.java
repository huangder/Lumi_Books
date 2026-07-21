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
public final class MineruEpubBuilder_Factory implements Factory<MineruEpubBuilder> {
  @Override
  public MineruEpubBuilder get() {
    return newInstance();
  }

  public static MineruEpubBuilder_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static MineruEpubBuilder newInstance() {
    return new MineruEpubBuilder();
  }

  private static final class InstanceHolder {
    private static final MineruEpubBuilder_Factory INSTANCE = new MineruEpubBuilder_Factory();
  }
}
