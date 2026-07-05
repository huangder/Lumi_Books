package com.huangder.lumibooks.ui.bookshelf;

import com.huangder.lumibooks.data.local.DataStoreManager;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class BookNotesActivity_MembersInjector implements MembersInjector<BookNotesActivity> {
  private final Provider<DataStoreManager> dataStoreManagerProvider;

  public BookNotesActivity_MembersInjector(Provider<DataStoreManager> dataStoreManagerProvider) {
    this.dataStoreManagerProvider = dataStoreManagerProvider;
  }

  public static MembersInjector<BookNotesActivity> create(
      Provider<DataStoreManager> dataStoreManagerProvider) {
    return new BookNotesActivity_MembersInjector(dataStoreManagerProvider);
  }

  @Override
  public void injectMembers(BookNotesActivity instance) {
    injectDataStoreManager(instance, dataStoreManagerProvider.get());
  }

  @InjectedFieldSignature("com.huangder.lumibooks.ui.bookshelf.BookNotesActivity.dataStoreManager")
  public static void injectDataStoreManager(BookNotesActivity instance,
      DataStoreManager dataStoreManager) {
    instance.dataStoreManager = dataStoreManager;
  }
}
