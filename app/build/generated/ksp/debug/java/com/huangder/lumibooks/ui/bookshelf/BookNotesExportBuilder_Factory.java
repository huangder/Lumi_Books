package com.huangder.lumibooks.ui.bookshelf;

import android.content.Context;
import com.huangder.lumibooks.data.local.DataStoreManager;
import com.huangder.lumibooks.pdfconversion.PdfTextExtractor;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class BookNotesExportBuilder_Factory implements Factory<BookNotesExportBuilder> {
  private final Provider<Context> contextProvider;

  private final Provider<DataStoreManager> dataStoreManagerProvider;

  private final Provider<PdfTextExtractor> pdfTextExtractorProvider;

  private BookNotesExportBuilder_Factory(Provider<Context> contextProvider,
      Provider<DataStoreManager> dataStoreManagerProvider,
      Provider<PdfTextExtractor> pdfTextExtractorProvider) {
    this.contextProvider = contextProvider;
    this.dataStoreManagerProvider = dataStoreManagerProvider;
    this.pdfTextExtractorProvider = pdfTextExtractorProvider;
  }

  @Override
  public BookNotesExportBuilder get() {
    return newInstance(contextProvider.get(), dataStoreManagerProvider.get(), pdfTextExtractorProvider.get());
  }

  public static BookNotesExportBuilder_Factory create(Provider<Context> contextProvider,
      Provider<DataStoreManager> dataStoreManagerProvider,
      Provider<PdfTextExtractor> pdfTextExtractorProvider) {
    return new BookNotesExportBuilder_Factory(contextProvider, dataStoreManagerProvider, pdfTextExtractorProvider);
  }

  public static BookNotesExportBuilder newInstance(Context context,
      DataStoreManager dataStoreManager, PdfTextExtractor pdfTextExtractor) {
    return new BookNotesExportBuilder(context, dataStoreManager, pdfTextExtractor);
  }
}
