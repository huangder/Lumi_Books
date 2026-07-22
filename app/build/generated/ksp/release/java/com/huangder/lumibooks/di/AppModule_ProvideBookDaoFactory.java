package com.huangder.lumibooks.di;

import com.huangder.lumibooks.data.local.dao.BookDao;
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
public final class AppModule_ProvideBookDaoFactory implements Factory<BookDao> {
  private final Provider<AppDatabase> databaseProvider;

  private AppModule_ProvideBookDaoFactory(Provider<AppDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public BookDao get() {
    return provideBookDao(databaseProvider.get());
  }

  public static AppModule_ProvideBookDaoFactory create(Provider<AppDatabase> databaseProvider) {
    return new AppModule_ProvideBookDaoFactory(databaseProvider);
  }

  public static BookDao provideBookDao(AppDatabase database) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideBookDao(database));
  }
}
