package com.huangder.lumibooks.data.repository;

import com.huangder.lumibooks.data.local.dao.BookmarkDao;
import com.huangder.lumibooks.data.local.dao.NoteDao;
import com.huangder.lumibooks.data.local.dao.ReadingRecordDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class ReadingRepositoryImpl_Factory implements Factory<ReadingRepositoryImpl> {
  private final Provider<ReadingRecordDao> readingRecordDaoProvider;

  private final Provider<BookmarkDao> bookmarkDaoProvider;

  private final Provider<NoteDao> noteDaoProvider;

  public ReadingRepositoryImpl_Factory(Provider<ReadingRecordDao> readingRecordDaoProvider,
      Provider<BookmarkDao> bookmarkDaoProvider, Provider<NoteDao> noteDaoProvider) {
    this.readingRecordDaoProvider = readingRecordDaoProvider;
    this.bookmarkDaoProvider = bookmarkDaoProvider;
    this.noteDaoProvider = noteDaoProvider;
  }

  @Override
  public ReadingRepositoryImpl get() {
    return newInstance(readingRecordDaoProvider.get(), bookmarkDaoProvider.get(), noteDaoProvider.get());
  }

  public static ReadingRepositoryImpl_Factory create(
      Provider<ReadingRecordDao> readingRecordDaoProvider,
      Provider<BookmarkDao> bookmarkDaoProvider, Provider<NoteDao> noteDaoProvider) {
    return new ReadingRepositoryImpl_Factory(readingRecordDaoProvider, bookmarkDaoProvider, noteDaoProvider);
  }

  public static ReadingRepositoryImpl newInstance(ReadingRecordDao readingRecordDao,
      BookmarkDao bookmarkDao, NoteDao noteDao) {
    return new ReadingRepositoryImpl(readingRecordDao, bookmarkDao, noteDao);
  }
}
