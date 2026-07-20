package com.huangder.lumibooks.ui.reader;

import android.content.Context;
import androidx.lifecycle.SavedStateHandle;
import com.huangder.lumibooks.data.local.DataStoreManager;
import com.huangder.lumibooks.domain.repository.BookRepository;
import com.huangder.lumibooks.domain.repository.ReadingRepository;
import com.huangder.lumibooks.pdfconversion.PdfConversionManager;
import com.huangder.lumibooks.tts.TtsController;
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

  private final Provider<TtsController> ttsControllerProvider;

  private final Provider<PdfConversionManager> pdfConversionManagerProvider;

  public ReaderViewModel_Factory(Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<Context> contextProvider, Provider<BookRepository> bookRepositoryProvider,
      Provider<ReadingRepository> readingRepositoryProvider,
      Provider<DataStoreManager> dataStoreManagerProvider,
      Provider<TtsController> ttsControllerProvider,
      Provider<PdfConversionManager> pdfConversionManagerProvider) {
    this.savedStateHandleProvider = savedStateHandleProvider;
    this.contextProvider = contextProvider;
    this.bookRepositoryProvider = bookRepositoryProvider;
    this.readingRepositoryProvider = readingRepositoryProvider;
    this.dataStoreManagerProvider = dataStoreManagerProvider;
    this.ttsControllerProvider = ttsControllerProvider;
    this.pdfConversionManagerProvider = pdfConversionManagerProvider;
  }

  @Override
  public ReaderViewModel get() {
    return newInstance(savedStateHandleProvider.get(), contextProvider.get(), bookRepositoryProvider.get(), readingRepositoryProvider.get(), dataStoreManagerProvider.get(), ttsControllerProvider.get(), pdfConversionManagerProvider.get());
  }

  public static ReaderViewModel_Factory create(Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<Context> contextProvider, Provider<BookRepository> bookRepositoryProvider,
      Provider<ReadingRepository> readingRepositoryProvider,
      Provider<DataStoreManager> dataStoreManagerProvider,
      Provider<TtsController> ttsControllerProvider,
      Provider<PdfConversionManager> pdfConversionManagerProvider) {
    return new ReaderViewModel_Factory(savedStateHandleProvider, contextProvider, bookRepositoryProvider, readingRepositoryProvider, dataStoreManagerProvider, ttsControllerProvider, pdfConversionManagerProvider);
  }

  public static ReaderViewModel newInstance(SavedStateHandle savedStateHandle, Context context,
      BookRepository bookRepository, ReadingRepository readingRepository,
      DataStoreManager dataStoreManager, TtsController ttsController,
      PdfConversionManager pdfConversionManager) {
    return new ReaderViewModel(savedStateHandle, context, bookRepository, readingRepository, dataStoreManager, ttsController, pdfConversionManager);
  }
}
