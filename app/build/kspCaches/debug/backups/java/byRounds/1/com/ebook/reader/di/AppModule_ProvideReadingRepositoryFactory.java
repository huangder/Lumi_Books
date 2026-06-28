package com.ebook.reader.di;

import com.ebook.reader.data.local.dao.BookmarkDao;
import com.ebook.reader.data.local.dao.NoteDao;
import com.ebook.reader.data.local.dao.ReadingRecordDao;
import com.ebook.reader.domain.repository.ReadingRepository;
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
public final class AppModule_ProvideReadingRepositoryFactory implements Factory<ReadingRepository> {
  private final Provider<ReadingRecordDao> readingRecordDaoProvider;

  private final Provider<BookmarkDao> bookmarkDaoProvider;

  private final Provider<NoteDao> noteDaoProvider;

  public AppModule_ProvideReadingRepositoryFactory(
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
