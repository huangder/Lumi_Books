package com.huangder.lumibooks.di;

import com.huangder.lumibooks.data.local.dao.NoteDao;
import com.huangder.lumibooks.data.local.database.AppDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class AppModule_ProvideNoteDaoFactory implements Factory<NoteDao> {
  private final Provider<AppDatabase> databaseProvider;

  public AppModule_ProvideNoteDaoFactory(Provider<AppDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public NoteDao get() {
    return provideNoteDao(databaseProvider.get());
  }

  public static AppModule_ProvideNoteDaoFactory create(Provider<AppDatabase> databaseProvider) {
    return new AppModule_ProvideNoteDaoFactory(databaseProvider);
  }

  public static NoteDao provideNoteDao(AppDatabase database) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideNoteDao(database));
  }
}
