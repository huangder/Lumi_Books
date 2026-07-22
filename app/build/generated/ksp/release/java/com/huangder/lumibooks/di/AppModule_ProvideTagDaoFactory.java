package com.huangder.lumibooks.di;

import com.huangder.lumibooks.data.local.dao.TagDao;
import com.huangder.lumibooks.data.local.database.AppDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
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
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class AppModule_ProvideTagDaoFactory implements Factory<TagDao> {
  private final Provider<AppDatabase> databaseProvider;

  private AppModule_ProvideTagDaoFactory(Provider<AppDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public TagDao get() {
    return provideTagDao(databaseProvider.get());
  }

  public static AppModule_ProvideTagDaoFactory create(Provider<AppDatabase> databaseProvider) {
    return new AppModule_ProvideTagDaoFactory(databaseProvider);
  }

  public static TagDao provideTagDao(AppDatabase database) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideTagDao(database));
  }
}
