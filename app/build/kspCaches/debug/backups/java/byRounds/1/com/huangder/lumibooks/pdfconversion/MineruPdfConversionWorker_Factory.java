package com.huangder.lumibooks.pdfconversion;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.huangder.lumibooks.data.local.DataStoreManager;
import com.huangder.lumibooks.data.local.database.AppDatabase;
import com.huangder.lumibooks.domain.repository.BookRepository;
import com.huangder.lumibooks.mineru.MineruApiClient;
import com.huangder.lumibooks.mineru.MineruEpubBuilder;
import com.huangder.lumibooks.mineru.MineruTokenStore;
import dagger.internal.DaggerGenerated;
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
public final class MineruPdfConversionWorker_Factory {
  private final Provider<BookRepository> bookRepositoryProvider;

  private final Provider<AppDatabase> databaseProvider;

  private final Provider<DataStoreManager> dataStoreManagerProvider;

  private final Provider<MineruApiClient> apiClientProvider;

  private final Provider<MineruEpubBuilder> epubBuilderProvider;

  private final Provider<MineruTokenStore> tokenStoreProvider;

  public MineruPdfConversionWorker_Factory(Provider<BookRepository> bookRepositoryProvider,
      Provider<AppDatabase> databaseProvider, Provider<DataStoreManager> dataStoreManagerProvider,
      Provider<MineruApiClient> apiClientProvider, Provider<MineruEpubBuilder> epubBuilderProvider,
      Provider<MineruTokenStore> tokenStoreProvider) {
    this.bookRepositoryProvider = bookRepositoryProvider;
    this.databaseProvider = databaseProvider;
    this.dataStoreManagerProvider = dataStoreManagerProvider;
    this.apiClientProvider = apiClientProvider;
    this.epubBuilderProvider = epubBuilderProvider;
    this.tokenStoreProvider = tokenStoreProvider;
  }

  public MineruPdfConversionWorker get(Context appContext, WorkerParameters workerParameters) {
    return newInstance(appContext, workerParameters, bookRepositoryProvider.get(), databaseProvider.get(), dataStoreManagerProvider.get(), apiClientProvider.get(), epubBuilderProvider.get(), tokenStoreProvider.get());
  }

  public static MineruPdfConversionWorker_Factory create(
      Provider<BookRepository> bookRepositoryProvider, Provider<AppDatabase> databaseProvider,
      Provider<DataStoreManager> dataStoreManagerProvider,
      Provider<MineruApiClient> apiClientProvider, Provider<MineruEpubBuilder> epubBuilderProvider,
      Provider<MineruTokenStore> tokenStoreProvider) {
    return new MineruPdfConversionWorker_Factory(bookRepositoryProvider, databaseProvider, dataStoreManagerProvider, apiClientProvider, epubBuilderProvider, tokenStoreProvider);
  }

  public static MineruPdfConversionWorker newInstance(Context appContext,
      WorkerParameters workerParameters, BookRepository bookRepository, AppDatabase database,
      DataStoreManager dataStoreManager, MineruApiClient apiClient, MineruEpubBuilder epubBuilder,
      MineruTokenStore tokenStore) {
    return new MineruPdfConversionWorker(appContext, workerParameters, bookRepository, database, dataStoreManager, apiClient, epubBuilder, tokenStore);
  }
}
