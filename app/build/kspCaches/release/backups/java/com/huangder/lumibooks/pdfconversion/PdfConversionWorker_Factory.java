package com.huangder.lumibooks.pdfconversion;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.huangder.lumibooks.data.local.database.AppDatabase;
import com.huangder.lumibooks.domain.repository.BookRepository;
import dagger.internal.DaggerGenerated;
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
public final class PdfConversionWorker_Factory {
  private final Provider<BookRepository> bookRepositoryProvider;

  private final Provider<AppDatabase> databaseProvider;

  private final Provider<PdfTextExtractor> textExtractorProvider;

  private PdfConversionWorker_Factory(Provider<BookRepository> bookRepositoryProvider,
      Provider<AppDatabase> databaseProvider, Provider<PdfTextExtractor> textExtractorProvider) {
    this.bookRepositoryProvider = bookRepositoryProvider;
    this.databaseProvider = databaseProvider;
    this.textExtractorProvider = textExtractorProvider;
  }

  public PdfConversionWorker get(Context appContext, WorkerParameters workerParameters) {
    return newInstance(appContext, workerParameters, bookRepositoryProvider.get(), databaseProvider.get(), textExtractorProvider.get());
  }

  public static PdfConversionWorker_Factory create(Provider<BookRepository> bookRepositoryProvider,
      Provider<AppDatabase> databaseProvider, Provider<PdfTextExtractor> textExtractorProvider) {
    return new PdfConversionWorker_Factory(bookRepositoryProvider, databaseProvider, textExtractorProvider);
  }

  public static PdfConversionWorker newInstance(Context appContext,
      WorkerParameters workerParameters, BookRepository bookRepository, AppDatabase database,
      PdfTextExtractor textExtractor) {
    return new PdfConversionWorker(appContext, workerParameters, bookRepository, database, textExtractor);
  }
}
