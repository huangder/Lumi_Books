package com.huangder.lumibooks.mineru;

import android.content.Context;
import com.huangder.lumibooks.data.local.database.AppDatabase;
import com.huangder.lumibooks.domain.repository.BookRepository;
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
public final class MineruManualImportManager_Factory implements Factory<MineruManualImportManager> {
  private final Provider<Context> contextProvider;

  private final Provider<BookRepository> bookRepositoryProvider;

  private final Provider<AppDatabase> databaseProvider;

  private final Provider<MineruEpubBuilder> epubBuilderProvider;

  private MineruManualImportManager_Factory(Provider<Context> contextProvider,
      Provider<BookRepository> bookRepositoryProvider, Provider<AppDatabase> databaseProvider,
      Provider<MineruEpubBuilder> epubBuilderProvider) {
    this.contextProvider = contextProvider;
    this.bookRepositoryProvider = bookRepositoryProvider;
    this.databaseProvider = databaseProvider;
    this.epubBuilderProvider = epubBuilderProvider;
  }

  @Override
  public MineruManualImportManager get() {
    return newInstance(contextProvider.get(), bookRepositoryProvider.get(), databaseProvider.get(), epubBuilderProvider.get());
  }

  public static MineruManualImportManager_Factory create(Provider<Context> contextProvider,
      Provider<BookRepository> bookRepositoryProvider, Provider<AppDatabase> databaseProvider,
      Provider<MineruEpubBuilder> epubBuilderProvider) {
    return new MineruManualImportManager_Factory(contextProvider, bookRepositoryProvider, databaseProvider, epubBuilderProvider);
  }

  public static MineruManualImportManager newInstance(Context context,
      BookRepository bookRepository, AppDatabase database, MineruEpubBuilder epubBuilder) {
    return new MineruManualImportManager(context, bookRepository, database, epubBuilder);
  }
}
