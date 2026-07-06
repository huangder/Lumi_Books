package com.huangder.lumibooks.di;

import com.huangder.lumibooks.data.local.dao.BookDao;
import com.huangder.lumibooks.domain.repository.BookRepository;
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
public final class AppModule_ProvideBookRepositoryFactory implements Factory<BookRepository> {
  private final Provider<BookDao> bookDaoProvider;

  public AppModule_ProvideBookRepositoryFactory(Provider<BookDao> bookDaoProvider) {
    this.bookDaoProvider = bookDaoProvider;
  }

  @Override
  public BookRepository get() {
    return provideBookRepository(bookDaoProvider.get());
  }

  public static AppModule_ProvideBookRepositoryFactory create(Provider<BookDao> bookDaoProvider) {
    return new AppModule_ProvideBookRepositoryFactory(bookDaoProvider);
  }

  public static BookRepository provideBookRepository(BookDao bookDao) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideBookRepository(bookDao));
  }
}
