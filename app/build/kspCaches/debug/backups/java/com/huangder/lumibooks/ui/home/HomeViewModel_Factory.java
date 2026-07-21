package com.huangder.lumibooks.ui.home;

import android.app.Application;
import com.huangder.lumibooks.data.local.DataStoreManager;
import com.huangder.lumibooks.domain.repository.BookRepository;
import com.huangder.lumibooks.domain.repository.ReadingRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class HomeViewModel_Factory implements Factory<HomeViewModel> {
  private final Provider<BookRepository> bookRepositoryProvider;

  private final Provider<ReadingRepository> readingRepositoryProvider;

  private final Provider<DataStoreManager> dataStoreManagerProvider;

  private final Provider<Application> applicationProvider;

  private HomeViewModel_Factory(Provider<BookRepository> bookRepositoryProvider,
      Provider<ReadingRepository> readingRepositoryProvider,
      Provider<DataStoreManager> dataStoreManagerProvider,
      Provider<Application> applicationProvider) {
    this.bookRepositoryProvider = bookRepositoryProvider;
    this.readingRepositoryProvider = readingRepositoryProvider;
    this.dataStoreManagerProvider = dataStoreManagerProvider;
    this.applicationProvider = applicationProvider;
  }

  @Override
  public HomeViewModel get() {
    return newInstance(bookRepositoryProvider.get(), readingRepositoryProvider.get(), dataStoreManagerProvider.get(), applicationProvider.get());
  }

  public static HomeViewModel_Factory create(Provider<BookRepository> bookRepositoryProvider,
      Provider<ReadingRepository> readingRepositoryProvider,
      Provider<DataStoreManager> dataStoreManagerProvider,
      Provider<Application> applicationProvider) {
    return new HomeViewModel_Factory(bookRepositoryProvider, readingRepositoryProvider, dataStoreManagerProvider, applicationProvider);
  }

  public static HomeViewModel newInstance(BookRepository bookRepository,
      ReadingRepository readingRepository, DataStoreManager dataStoreManager,
      Application application) {
    return new HomeViewModel(bookRepository, readingRepository, dataStoreManager, application);
  }
}
