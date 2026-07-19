package com.huangder.lumibooks.ui.bookshelf;

import androidx.lifecycle.SavedStateHandle;
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
public final class BookNotesViewModel_Factory implements Factory<BookNotesViewModel> {
  private final Provider<SavedStateHandle> savedStateHandleProvider;

  private final Provider<BookRepository> bookRepositoryProvider;

  private final Provider<ReadingRepository> readingRepositoryProvider;

  public BookNotesViewModel_Factory(Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<BookRepository> bookRepositoryProvider,
      Provider<ReadingRepository> readingRepositoryProvider) {
    this.savedStateHandleProvider = savedStateHandleProvider;
    this.bookRepositoryProvider = bookRepositoryProvider;
    this.readingRepositoryProvider = readingRepositoryProvider;
  }

  @Override
  public BookNotesViewModel get() {
    return newInstance(savedStateHandleProvider.get(), bookRepositoryProvider.get(), readingRepositoryProvider.get());
  }

  public static BookNotesViewModel_Factory create(
      Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<BookRepository> bookRepositoryProvider,
      Provider<ReadingRepository> readingRepositoryProvider) {
    return new BookNotesViewModel_Factory(savedStateHandleProvider, bookRepositoryProvider, readingRepositoryProvider);
  }

  public static BookNotesViewModel newInstance(SavedStateHandle savedStateHandle,
      BookRepository bookRepository, ReadingRepository readingRepository) {
    return new BookNotesViewModel(savedStateHandle, bookRepository, readingRepository);
  }
}
