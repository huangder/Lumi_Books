package com.huangder.lumibooks.ui.home;

import com.huangder.lumibooks.data.local.DataStoreManager;
import com.huangder.lumibooks.domain.repository.BookRepository;
import com.huangder.lumibooks.domain.repository.ReadingRepository;
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
public final class HomeViewModel_Factory implements Factory<HomeViewModel> {
  private final Provider<BookRepository> bookRepositoryProvider;

  private final Provider<ReadingRepository> readingRepositoryProvider;

  private final Provider<DataStoreManager> dataStoreManagerProvider;

  public HomeViewModel_Factory(Provider<BookRepository> bookRepositoryProvider,
      Provider<ReadingRepository> readingRepositoryProvider,
      Provider<DataStoreManager> dataStoreManagerProvider) {
    this.bookRepositoryProvider = bookRepositoryProvider;
    this.readingRepositoryProvider = readingRepositoryProvider;
    this.dataStoreManagerProvider = dataStoreManagerProvider;
  }

  @Override
  public HomeViewModel get() {
    return newInstance(bookRepositoryProvider.get(), readingRepositoryProvider.get(), dataStoreManagerProvider.get());
  }

  public static HomeViewModel_Factory create(Provider<BookRepository> bookRepositoryProvider,
      Provider<ReadingRepository> readingRepositoryProvider,
      Provider<DataStoreManager> dataStoreManagerProvider) {
    return new HomeViewModel_Factory(bookRepositoryProvider, readingRepositoryProvider, dataStoreManagerProvider);
  }

  public static HomeViewModel newInstance(BookRepository bookRepository,
      ReadingRepository readingRepository, DataStoreManager dataStoreManager) {
    return new HomeViewModel(bookRepository, readingRepository, dataStoreManager);
  }
}
