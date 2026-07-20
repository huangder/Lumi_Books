package com.huangder.lumibooks.data.repository;

import com.huangder.lumibooks.data.local.dao.BookDao;
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
public final class BookRepositoryImpl_Factory implements Factory<BookRepositoryImpl> {
  private final Provider<BookDao> bookDaoProvider;

  public BookRepositoryImpl_Factory(Provider<BookDao> bookDaoProvider) {
    this.bookDaoProvider = bookDaoProvider;
  }

  @Override
  public BookRepositoryImpl get() {
    return newInstance(bookDaoProvider.get());
  }

  public static BookRepositoryImpl_Factory create(Provider<BookDao> bookDaoProvider) {
    return new BookRepositoryImpl_Factory(bookDaoProvider);
  }

  public static BookRepositoryImpl newInstance(BookDao bookDao) {
    return new BookRepositoryImpl(bookDao);
  }
}
