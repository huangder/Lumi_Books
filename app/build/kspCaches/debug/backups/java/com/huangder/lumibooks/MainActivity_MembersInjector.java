package com.huangder.lumibooks;

import com.huangder.lumibooks.data.local.DataStoreManager;
import com.huangder.lumibooks.domain.repository.BookRepository;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

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
public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<DataStoreManager> dataStoreManagerProvider;

  private final Provider<BookRepository> bookRepositoryProvider;

  private MainActivity_MembersInjector(Provider<DataStoreManager> dataStoreManagerProvider,
      Provider<BookRepository> bookRepositoryProvider) {
    this.dataStoreManagerProvider = dataStoreManagerProvider;
    this.bookRepositoryProvider = bookRepositoryProvider;
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectDataStoreManager(instance, dataStoreManagerProvider.get());
    injectBookRepository(instance, bookRepositoryProvider.get());
  }

  public static MembersInjector<MainActivity> create(
      Provider<DataStoreManager> dataStoreManagerProvider,
      Provider<BookRepository> bookRepositoryProvider) {
    return new MainActivity_MembersInjector(dataStoreManagerProvider, bookRepositoryProvider);
  }

  @InjectedFieldSignature("com.huangder.lumibooks.MainActivity.dataStoreManager")
  public static void injectDataStoreManager(MainActivity instance,
      DataStoreManager dataStoreManager) {
    instance.dataStoreManager = dataStoreManager;
  }

  @InjectedFieldSignature("com.huangder.lumibooks.MainActivity.bookRepository")
  public static void injectBookRepository(MainActivity instance, BookRepository bookRepository) {
    instance.bookRepository = bookRepository;
  }
}
