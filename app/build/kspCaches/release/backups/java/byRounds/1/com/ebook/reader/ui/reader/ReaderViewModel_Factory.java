package com.ebook.reader.ui.reader;

import android.content.Context;
import androidx.lifecycle.SavedStateHandle;
import com.ebook.reader.data.local.DataStoreManager;
import com.ebook.reader.domain.repository.BookRepository;
import com.ebook.reader.domain.repository.ReadingRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
    "deprecation"
})
public final class ReaderViewModel_Factory implements Factory<ReaderViewModel> {
  private final Provider<SavedStateHandle> savedStateHandleProvider;

  private final Provider<Context> contextProvider;

  private final Provider<BookRepository> bookRepositoryProvider;

  private final Provider<ReadingRepository> readingRepositoryProvider;

  private final Provider<DataStoreManager> dataStoreManagerProvider;

  public ReaderViewModel_Factory(Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<Context> contextProvider, Provider<BookRepository> bookRepositoryProvider,
      Provider<ReadingRepository> readingRepositoryProvider,
      Provider<DataStoreManager> dataStoreManagerProvider) {
    this.savedStateHandleProvider = savedStateHandleProvider;
    this.contextProvider = contextProvider;
    this.bookRepositoryProvider = bookRepositoryProvider;
    this.readingRepositoryProvider = readingRepositoryProvider;
    this.dataStoreManagerProvider = dataStoreManagerProvider;
  }

  @Override
  public ReaderViewModel get() {
    return newInstance(savedStateHandleProvider.get(), contextProvider.get(), bookRepositoryProvider.get(), readingRepositoryProvider.get(), dataStoreManagerProvider.get());
  }

  public static ReaderViewModel_Factory create(Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<Context> contextProvider, Provider<BookRepository> bookRepositoryProvider,
      Provider<ReadingRepository> readingRepositoryProvider,
      Provider<DataStoreManager> dataStoreManagerProvider) {
    return new ReaderViewModel_Factory(savedStateHandleProvider, contextProvider, bookRepositoryProvider, readingRepositoryProvider, dataStoreManagerProvider);
  }

  public static ReaderViewModel newInstance(SavedStateHandle savedStateHandle, Context context,
      BookRepository bookRepository, ReadingRepository readingRepository,
      DataStoreManager dataStoreManager) {
    return new ReaderViewModel(savedStateHandle, context, bookRepository, readingRepository, dataStoreManager);
  }
}
