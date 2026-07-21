package com.huangder.lumibooks.di;

import com.huangder.lumibooks.data.local.dao.ReadingRecordDao;
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
public final class AppModule_ProvideReadingRecordDaoFactory implements Factory<ReadingRecordDao> {
  private final Provider<AppDatabase> databaseProvider;

  private AppModule_ProvideReadingRecordDaoFactory(Provider<AppDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public ReadingRecordDao get() {
    return provideReadingRecordDao(databaseProvider.get());
  }

  public static AppModule_ProvideReadingRecordDaoFactory create(
      Provider<AppDatabase> databaseProvider) {
    return new AppModule_ProvideReadingRecordDaoFactory(databaseProvider);
  }

  public static ReadingRecordDao provideReadingRecordDao(AppDatabase database) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideReadingRecordDao(database));
  }
}
