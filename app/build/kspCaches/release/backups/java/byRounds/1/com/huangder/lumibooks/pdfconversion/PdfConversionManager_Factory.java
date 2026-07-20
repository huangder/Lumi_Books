package com.huangder.lumibooks.pdfconversion;

import android.content.Context;
import com.huangder.lumibooks.domain.repository.BookRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class PdfConversionManager_Factory implements Factory<PdfConversionManager> {
  private final Provider<Context> contextProvider;

  private final Provider<BookRepository> bookRepositoryProvider;

  public PdfConversionManager_Factory(Provider<Context> contextProvider,
      Provider<BookRepository> bookRepositoryProvider) {
    this.contextProvider = contextProvider;
    this.bookRepositoryProvider = bookRepositoryProvider;
  }

  @Override
  public PdfConversionManager get() {
    return newInstance(contextProvider.get(), bookRepositoryProvider.get());
  }

  public static PdfConversionManager_Factory create(Provider<Context> contextProvider,
      Provider<BookRepository> bookRepositoryProvider) {
    return new PdfConversionManager_Factory(contextProvider, bookRepositoryProvider);
  }

  public static PdfConversionManager newInstance(Context context, BookRepository bookRepository) {
    return new PdfConversionManager(context, bookRepository);
  }
}
