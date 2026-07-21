package com.huangder.lumibooks.ui.settings;

import android.content.Context;
import com.huangder.lumibooks.data.local.DataStoreManager;
import com.huangder.lumibooks.domain.repository.BookRepository;
import com.huangder.lumibooks.mineru.MineruTokenStore;
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
public final class SettingsViewModel_Factory implements Factory<SettingsViewModel> {
  private final Provider<DataStoreManager> dataStoreManagerProvider;

  private final Provider<BookRepository> bookRepositoryProvider;

  private final Provider<MineruTokenStore> mineruTokenStoreProvider;

  private final Provider<Context> contextProvider;

  public SettingsViewModel_Factory(Provider<DataStoreManager> dataStoreManagerProvider,
      Provider<BookRepository> bookRepositoryProvider,
      Provider<MineruTokenStore> mineruTokenStoreProvider, Provider<Context> contextProvider) {
    this.dataStoreManagerProvider = dataStoreManagerProvider;
    this.bookRepositoryProvider = bookRepositoryProvider;
    this.mineruTokenStoreProvider = mineruTokenStoreProvider;
    this.contextProvider = contextProvider;
  }

  @Override
  public SettingsViewModel get() {
    return newInstance(dataStoreManagerProvider.get(), bookRepositoryProvider.get(), mineruTokenStoreProvider.get(), contextProvider.get());
  }

  public static SettingsViewModel_Factory create(
      Provider<DataStoreManager> dataStoreManagerProvider,
      Provider<BookRepository> bookRepositoryProvider,
      Provider<MineruTokenStore> mineruTokenStoreProvider, Provider<Context> contextProvider) {
    return new SettingsViewModel_Factory(dataStoreManagerProvider, bookRepositoryProvider, mineruTokenStoreProvider, contextProvider);
  }

  public static SettingsViewModel newInstance(DataStoreManager dataStoreManager,
      BookRepository bookRepository, MineruTokenStore mineruTokenStore, Context context) {
    return new SettingsViewModel(dataStoreManager, bookRepository, mineruTokenStore, context);
  }
}
