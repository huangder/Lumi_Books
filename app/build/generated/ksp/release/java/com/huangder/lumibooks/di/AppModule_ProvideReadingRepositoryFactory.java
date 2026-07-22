package com.huangder.lumibooks.di;

import com.huangder.lumibooks.data.local.dao.BookmarkDao;
import com.huangder.lumibooks.data.local.dao.NoteDao;
import com.huangder.lumibooks.data.local.dao.ReadingRecordDao;
import com.huangder.lumibooks.domain.repository.ReadingRepository;
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
public final class AppModule_ProvideReadingRepositoryFactory implements Factory<ReadingRepository> {
  private final Provider<ReadingRecordDao> readingRecordDaoProvider;

  private final Provider<BookmarkDao> bookmarkDaoProvider;

  private final Provider<NoteDao> noteDaoProvider;

  private AppModule_ProvideReadingRepositoryFactory(
      Provider<ReadingRecordDao> readingRecordDaoProvider,
      Provider<BookmarkDao> bookmarkDaoProvider, Provider<NoteDao> noteDaoProvider) {
    this.readingRecordDaoProvider = readingRecordDaoProvider;
    this.bookmarkDaoProvider = bookmarkDaoProvider;
    this.noteDaoProvider = noteDaoProvider;
  }

  @Override
  public ReadingRepository get() {
    return provideReadingRepository(readingRecordDaoProvider.get(), bookmarkDaoProvider.get(), noteDaoProvider.get());
  }

  public static AppModule_ProvideReadingRepositoryFactory create(
      Provider<ReadingRecordDao> readingRecordDaoProvider,
      Provider<BookmarkDao> bookmarkDaoProvider, Provider<NoteDao> noteDaoProvider) {
    return new AppModule_ProvideReadingRepositoryFactory(readingRecordDaoProvider, bookmarkDaoProvider, noteDaoProvider);
  }

  public static ReadingRepository provideReadingRepository(ReadingRecordDao readingRecordDao,
      BookmarkDao bookmarkDao, NoteDao noteDao) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideReadingRepository(readingRecordDao, bookmarkDao, noteDao));
  }
}
