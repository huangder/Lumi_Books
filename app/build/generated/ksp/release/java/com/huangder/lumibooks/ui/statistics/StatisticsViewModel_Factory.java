package com.huangder.lumibooks.ui.statistics;

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
public final class StatisticsViewModel_Factory implements Factory<StatisticsViewModel> {
  private final Provider<ReadingRepository> readingRepositoryProvider;

  private final Provider<BookRepository> bookRepositoryProvider;

  private final Provider<DataStoreManager> dataStoreManagerProvider;

  public StatisticsViewModel_Factory(Provider<ReadingRepository> readingRepositoryProvider,
      Provider<BookRepository> bookRepositoryProvider,
      Provider<DataStoreManager> dataStoreManagerProvider) {
    this.readingRepositoryProvider = readingRepositoryProvider;
    this.bookRepositoryProvider = bookRepositoryProvider;
    this.dataStoreManagerProvider = dataStoreManagerProvider;
  }

  @Override
  public StatisticsViewModel get() {
    return newInstance(readingRepositoryProvider.get(), bookRepositoryProvider.get(), dataStoreManagerProvider.get());
  }

  public static StatisticsViewModel_Factory create(
      Provider<ReadingRepository> readingRepositoryProvider,
      Provider<BookRepository> bookRepositoryProvider,
      Provider<DataStoreManager> dataStoreManagerProvider) {
    return new StatisticsViewModel_Factory(readingRepositoryProvider, bookRepositoryProvider, dataStoreManagerProvider);
  }

  public static StatisticsViewModel newInstance(ReadingRepository readingRepository,
      BookRepository bookRepository, DataStoreManager dataStoreManager) {
    return new StatisticsViewModel(readingRepository, bookRepository, dataStoreManager);
  }
}
