package com.huangder.lumibooks.ui.settings;

import android.content.Context;
import com.huangder.lumibooks.data.local.DataStoreManager;
import com.huangder.lumibooks.domain.repository.BookRepository;
import com.huangder.lumibooks.mineru.MineruManualImportManager;
import com.huangder.lumibooks.mineru.MineruTokenStore;
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
public final class SettingsViewModel_Factory implements Factory<SettingsViewModel> {
  private final Provider<DataStoreManager> dataStoreManagerProvider;

  private final Provider<BookRepository> bookRepositoryProvider;

  private final Provider<MineruTokenStore> mineruTokenStoreProvider;

  private final Provider<MineruManualImportManager> mineruManualImportManagerProvider;

  private final Provider<Context> contextProvider;

  private SettingsViewModel_Factory(Provider<DataStoreManager> dataStoreManagerProvider,
      Provider<BookRepository> bookRepositoryProvider,
      Provider<MineruTokenStore> mineruTokenStoreProvider,
      Provider<MineruManualImportManager> mineruManualImportManagerProvider,
      Provider<Context> contextProvider) {
    this.dataStoreManagerProvider = dataStoreManagerProvider;
    this.bookRepositoryProvider = bookRepositoryProvider;
    this.mineruTokenStoreProvider = mineruTokenStoreProvider;
    this.mineruManualImportManagerProvider = mineruManualImportManagerProvider;
    this.contextProvider = contextProvider;
  }

  @Override
  public SettingsViewModel get() {
    return newInstance(dataStoreManagerProvider.get(), bookRepositoryProvider.get(), mineruTokenStoreProvider.get(), mineruManualImportManagerProvider.get(), contextProvider.get());
  }

  public static SettingsViewModel_Factory create(
      Provider<DataStoreManager> dataStoreManagerProvider,
      Provider<BookRepository> bookRepositoryProvider,
      Provider<MineruTokenStore> mineruTokenStoreProvider,
      Provider<MineruManualImportManager> mineruManualImportManagerProvider,
      Provider<Context> contextProvider) {
    return new SettingsViewModel_Factory(dataStoreManagerProvider, bookRepositoryProvider, mineruTokenStoreProvider, mineruManualImportManagerProvider, contextProvider);
  }

  public static SettingsViewModel newInstance(DataStoreManager dataStoreManager,
      BookRepository bookRepository, MineruTokenStore mineruTokenStore,
      MineruManualImportManager mineruManualImportManager, Context context) {
    return new SettingsViewModel(dataStoreManager, bookRepository, mineruTokenStore, mineruManualImportManager, context);
  }
}
