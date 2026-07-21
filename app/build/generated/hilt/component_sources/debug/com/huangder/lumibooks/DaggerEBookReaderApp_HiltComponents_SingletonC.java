package com.huangder.lumibooks;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.view.View;
import androidx.fragment.app.Fragment;
import androidx.hilt.work.HiltWorkerFactory;
import androidx.hilt.work.WorkerAssistedFactory;
import androidx.hilt.work.WorkerFactoryModule_ProvideFactoryFactory;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;
import com.huangder.lumibooks.data.local.DataStoreManager;
import com.huangder.lumibooks.data.local.dao.BookDao;
import com.huangder.lumibooks.data.local.dao.BookmarkDao;
import com.huangder.lumibooks.data.local.dao.NoteDao;
import com.huangder.lumibooks.data.local.dao.ReadingRecordDao;
import com.huangder.lumibooks.data.local.database.AppDatabase;
import com.huangder.lumibooks.di.AppModule_ProvideAppDatabaseFactory;
import com.huangder.lumibooks.di.AppModule_ProvideBookDaoFactory;
import com.huangder.lumibooks.di.AppModule_ProvideBookRepositoryFactory;
import com.huangder.lumibooks.di.AppModule_ProvideBookmarkDaoFactory;
import com.huangder.lumibooks.di.AppModule_ProvideDataStoreManagerFactory;
import com.huangder.lumibooks.di.AppModule_ProvideNoteDaoFactory;
import com.huangder.lumibooks.di.AppModule_ProvideReadingRecordDaoFactory;
import com.huangder.lumibooks.di.AppModule_ProvideReadingRepositoryFactory;
import com.huangder.lumibooks.di.AppModule_ProvideTtsControllerFactory;
import com.huangder.lumibooks.di.AppModule_ProvideTtsEngineFactory;
import com.huangder.lumibooks.di.AppModule_ProvideTtsTextExtractorFactory;
import com.huangder.lumibooks.domain.repository.BookRepository;
import com.huangder.lumibooks.domain.repository.ReadingRepository;
import com.huangder.lumibooks.mineru.MineruApiClient;
import com.huangder.lumibooks.mineru.MineruEpubBuilder;
import com.huangder.lumibooks.mineru.MineruManualImportManager;
import com.huangder.lumibooks.mineru.MineruTokenStore;
import com.huangder.lumibooks.pdfconversion.MineruPdfConversionWorker;
import com.huangder.lumibooks.pdfconversion.MineruPdfConversionWorker_AssistedFactory;
import com.huangder.lumibooks.pdfconversion.PdfConversionManager;
import com.huangder.lumibooks.pdfconversion.PdfConversionWorker;
import com.huangder.lumibooks.pdfconversion.PdfConversionWorker_AssistedFactory;
import com.huangder.lumibooks.pdfconversion.PdfTextExtractor;
import com.huangder.lumibooks.service.TtsForegroundService;
import com.huangder.lumibooks.service.TtsForegroundService_MembersInjector;
import com.huangder.lumibooks.service.TtsNotificationManager;
import com.huangder.lumibooks.tts.TtsController;
import com.huangder.lumibooks.tts.TtsEngine;
import com.huangder.lumibooks.tts.TtsTextExtractor;
import com.huangder.lumibooks.ui.bookshelf.BookNotesActivity;
import com.huangder.lumibooks.ui.bookshelf.BookNotesActivity_MembersInjector;
import com.huangder.lumibooks.ui.bookshelf.BookNotesViewModel;
import com.huangder.lumibooks.ui.bookshelf.BookNotesViewModel_HiltModules;
import com.huangder.lumibooks.ui.bookshelf.BookNotesViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.huangder.lumibooks.ui.bookshelf.BookNotesViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.huangder.lumibooks.ui.home.HomeViewModel;
import com.huangder.lumibooks.ui.home.HomeViewModel_HiltModules;
import com.huangder.lumibooks.ui.home.HomeViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.huangder.lumibooks.ui.home.HomeViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.huangder.lumibooks.ui.reader.ReaderViewModel;
import com.huangder.lumibooks.ui.reader.ReaderViewModel_HiltModules;
import com.huangder.lumibooks.ui.reader.ReaderViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.huangder.lumibooks.ui.reader.ReaderViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.huangder.lumibooks.ui.settings.DetailActivity;
import com.huangder.lumibooks.ui.settings.DetailActivity_MembersInjector;
import com.huangder.lumibooks.ui.settings.FeedbackActivity;
import com.huangder.lumibooks.ui.settings.FeedbackActivity_MembersInjector;
import com.huangder.lumibooks.ui.settings.SettingsActivity;
import com.huangder.lumibooks.ui.settings.SettingsActivity_MembersInjector;
import com.huangder.lumibooks.ui.settings.SettingsViewModel;
import com.huangder.lumibooks.ui.settings.SettingsViewModel_HiltModules;
import com.huangder.lumibooks.ui.settings.SettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.huangder.lumibooks.ui.settings.SettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.huangder.lumibooks.ui.settings.SponsorActivity;
import com.huangder.lumibooks.ui.settings.SponsorActivity_MembersInjector;
import com.huangder.lumibooks.ui.settings.WebViewActivity;
import com.huangder.lumibooks.ui.settings.WebViewActivity_MembersInjector;
import com.huangder.lumibooks.ui.statistics.StatisticsViewModel;
import com.huangder.lumibooks.ui.statistics.StatisticsViewModel_HiltModules;
import com.huangder.lumibooks.ui.statistics.StatisticsViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.huangder.lumibooks.ui.statistics.StatisticsViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.huangder.lumibooks.ui.welcome.WelcomeActivity;
import com.huangder.lumibooks.ui.welcome.WelcomeActivity_MembersInjector;
import com.huangder.lumibooks.ui.welcome.WelcomeViewModel;
import com.huangder.lumibooks.ui.welcome.WelcomeViewModel_HiltModules;
import com.huangder.lumibooks.ui.welcome.WelcomeViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.huangder.lumibooks.ui.welcome.WelcomeViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import dagger.hilt.android.ActivityRetainedLifecycle;
import dagger.hilt.android.ViewModelLifecycle;
import dagger.hilt.android.internal.builders.ActivityComponentBuilder;
import dagger.hilt.android.internal.builders.ActivityRetainedComponentBuilder;
import dagger.hilt.android.internal.builders.FragmentComponentBuilder;
import dagger.hilt.android.internal.builders.ServiceComponentBuilder;
import dagger.hilt.android.internal.builders.ViewComponentBuilder;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.hilt.android.internal.builders.ViewWithFragmentComponentBuilder;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories_InternalFactoryFactory_Factory;
import dagger.hilt.android.internal.managers.ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory;
import dagger.hilt.android.internal.managers.SavedStateHandleHolder;
import dagger.hilt.android.internal.modules.ApplicationContextModule;
import dagger.hilt.android.internal.modules.ApplicationContextModule_ProvideApplicationFactory;
import dagger.hilt.android.internal.modules.ApplicationContextModule_ProvideContextFactory;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
import dagger.internal.LazyClassKeyMap;
import dagger.internal.MapBuilder;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.SingleCheck;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

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
public final class DaggerEBookReaderApp_HiltComponents_SingletonC {
  private DaggerEBookReaderApp_HiltComponents_SingletonC() {
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private ApplicationContextModule applicationContextModule;

    private Builder() {
    }

    public Builder applicationContextModule(ApplicationContextModule applicationContextModule) {
      this.applicationContextModule = Preconditions.checkNotNull(applicationContextModule);
      return this;
    }

    public EBookReaderApp_HiltComponents.SingletonC build() {
      Preconditions.checkBuilderRequirement(applicationContextModule, ApplicationContextModule.class);
      return new SingletonCImpl(applicationContextModule);
    }
  }

  private static final class ActivityRetainedCBuilder implements EBookReaderApp_HiltComponents.ActivityRetainedC.Builder {
    private final SingletonCImpl singletonCImpl;

    private SavedStateHandleHolder savedStateHandleHolder;

    private ActivityRetainedCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ActivityRetainedCBuilder savedStateHandleHolder(
        SavedStateHandleHolder savedStateHandleHolder) {
      this.savedStateHandleHolder = Preconditions.checkNotNull(savedStateHandleHolder);
      return this;
    }

    @Override
    public EBookReaderApp_HiltComponents.ActivityRetainedC build() {
      Preconditions.checkBuilderRequirement(savedStateHandleHolder, SavedStateHandleHolder.class);
      return new ActivityRetainedCImpl(singletonCImpl, savedStateHandleHolder);
    }
  }

  private static final class ActivityCBuilder implements EBookReaderApp_HiltComponents.ActivityC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private Activity activity;

    private ActivityCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ActivityCBuilder activity(Activity activity) {
      this.activity = Preconditions.checkNotNull(activity);
      return this;
    }

    @Override
    public EBookReaderApp_HiltComponents.ActivityC build() {
      Preconditions.checkBuilderRequirement(activity, Activity.class);
      return new ActivityCImpl(singletonCImpl, activityRetainedCImpl, activity);
    }
  }

  private static final class FragmentCBuilder implements EBookReaderApp_HiltComponents.FragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private Fragment fragment;

    private FragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public FragmentCBuilder fragment(Fragment fragment) {
      this.fragment = Preconditions.checkNotNull(fragment);
      return this;
    }

    @Override
    public EBookReaderApp_HiltComponents.FragmentC build() {
      Preconditions.checkBuilderRequirement(fragment, Fragment.class);
      return new FragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragment);
    }
  }

  private static final class ViewWithFragmentCBuilder implements EBookReaderApp_HiltComponents.ViewWithFragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private View view;

    private ViewWithFragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;
    }

    @Override
    public ViewWithFragmentCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public EBookReaderApp_HiltComponents.ViewWithFragmentC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewWithFragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl, view);
    }
  }

  private static final class ViewCBuilder implements EBookReaderApp_HiltComponents.ViewC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private View view;

    private ViewCBuilder(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public ViewCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public EBookReaderApp_HiltComponents.ViewC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, view);
    }
  }

  private static final class ViewModelCBuilder implements EBookReaderApp_HiltComponents.ViewModelC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private SavedStateHandle savedStateHandle;

    private ViewModelLifecycle viewModelLifecycle;

    private ViewModelCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ViewModelCBuilder savedStateHandle(SavedStateHandle handle) {
      this.savedStateHandle = Preconditions.checkNotNull(handle);
      return this;
    }

    @Override
    public ViewModelCBuilder viewModelLifecycle(ViewModelLifecycle viewModelLifecycle) {
      this.viewModelLifecycle = Preconditions.checkNotNull(viewModelLifecycle);
      return this;
    }

    @Override
    public EBookReaderApp_HiltComponents.ViewModelC build() {
      Preconditions.checkBuilderRequirement(savedStateHandle, SavedStateHandle.class);
      Preconditions.checkBuilderRequirement(viewModelLifecycle, ViewModelLifecycle.class);
      return new ViewModelCImpl(singletonCImpl, activityRetainedCImpl, savedStateHandle, viewModelLifecycle);
    }
  }

  private static final class ServiceCBuilder implements EBookReaderApp_HiltComponents.ServiceC.Builder {
    private final SingletonCImpl singletonCImpl;

    private Service service;

    private ServiceCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ServiceCBuilder service(Service service) {
      this.service = Preconditions.checkNotNull(service);
      return this;
    }

    @Override
    public EBookReaderApp_HiltComponents.ServiceC build() {
      Preconditions.checkBuilderRequirement(service, Service.class);
      return new ServiceCImpl(singletonCImpl, service);
    }
  }

  private static final class ViewWithFragmentCImpl extends EBookReaderApp_HiltComponents.ViewWithFragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private final ViewWithFragmentCImpl viewWithFragmentCImpl = this;

    ViewWithFragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;


    }
  }

  private static final class FragmentCImpl extends EBookReaderApp_HiltComponents.FragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl = this;

    FragmentCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, Fragment fragmentParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return activityCImpl.getHiltInternalFactoryFactory();
    }

    @Override
    public ViewWithFragmentComponentBuilder viewWithFragmentComponentBuilder() {
      return new ViewWithFragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl);
    }
  }

  private static final class ViewCImpl extends EBookReaderApp_HiltComponents.ViewC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final ViewCImpl viewCImpl = this;

    ViewCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }
  }

  private static final class ActivityCImpl extends EBookReaderApp_HiltComponents.ActivityC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl = this;

    ActivityCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        Activity activityParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;


    }

    Map keySetMapOfClassOfAndBooleanBuilder() {
      MapBuilder mapBuilder = MapBuilder.<String, Boolean>newMapBuilder(6);
      mapBuilder.put(BookNotesViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, BookNotesViewModel_HiltModules.KeyModule.provide());
      mapBuilder.put(HomeViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, HomeViewModel_HiltModules.KeyModule.provide());
      mapBuilder.put(ReaderViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, ReaderViewModel_HiltModules.KeyModule.provide());
      mapBuilder.put(SettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, SettingsViewModel_HiltModules.KeyModule.provide());
      mapBuilder.put(StatisticsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, StatisticsViewModel_HiltModules.KeyModule.provide());
      mapBuilder.put(WelcomeViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, WelcomeViewModel_HiltModules.KeyModule.provide());
      return mapBuilder.build();
    }

    @Override
    public void injectMainActivity(MainActivity mainActivity) {
      injectMainActivity2(mainActivity);
    }

    @Override
    public void injectBookNotesActivity(BookNotesActivity bookNotesActivity) {
      injectBookNotesActivity2(bookNotesActivity);
    }

    @Override
    public void injectDetailActivity(DetailActivity detailActivity) {
      injectDetailActivity2(detailActivity);
    }

    @Override
    public void injectFeedbackActivity(FeedbackActivity feedbackActivity) {
      injectFeedbackActivity2(feedbackActivity);
    }

    @Override
    public void injectSettingsActivity(SettingsActivity settingsActivity) {
      injectSettingsActivity2(settingsActivity);
    }

    @Override
    public void injectSponsorActivity(SponsorActivity sponsorActivity) {
      injectSponsorActivity2(sponsorActivity);
    }

    @Override
    public void injectWebViewActivity(WebViewActivity webViewActivity) {
      injectWebViewActivity2(webViewActivity);
    }

    @Override
    public void injectWelcomeActivity(WelcomeActivity welcomeActivity) {
      injectWelcomeActivity2(welcomeActivity);
    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return DefaultViewModelFactories_InternalFactoryFactory_Factory.newInstance(getViewModelKeys(), new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl));
    }

    @Override
    public Map<Class<?>, Boolean> getViewModelKeys() {
      return LazyClassKeyMap.<Boolean>of(keySetMapOfClassOfAndBooleanBuilder());
    }

    @Override
    public ViewModelComponentBuilder getViewModelComponentBuilder() {
      return new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public FragmentComponentBuilder fragmentComponentBuilder() {
      return new FragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @Override
    public ViewComponentBuilder viewComponentBuilder() {
      return new ViewCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    private MainActivity injectMainActivity2(MainActivity instance) {
      MainActivity_MembersInjector.injectDataStoreManager(instance, singletonCImpl.provideDataStoreManagerProvider.get());
      MainActivity_MembersInjector.injectBookRepository(instance, singletonCImpl.provideBookRepositoryProvider.get());
      return instance;
    }

    private BookNotesActivity injectBookNotesActivity2(BookNotesActivity instance2) {
      BookNotesActivity_MembersInjector.injectDataStoreManager(instance2, singletonCImpl.provideDataStoreManagerProvider.get());
      return instance2;
    }

    private DetailActivity injectDetailActivity2(DetailActivity instance3) {
      DetailActivity_MembersInjector.injectDataStoreManager(instance3, singletonCImpl.provideDataStoreManagerProvider.get());
      return instance3;
    }

    private FeedbackActivity injectFeedbackActivity2(FeedbackActivity instance4) {
      FeedbackActivity_MembersInjector.injectDataStoreManager(instance4, singletonCImpl.provideDataStoreManagerProvider.get());
      return instance4;
    }

    private SettingsActivity injectSettingsActivity2(SettingsActivity instance5) {
      SettingsActivity_MembersInjector.injectDataStoreManager(instance5, singletonCImpl.provideDataStoreManagerProvider.get());
      return instance5;
    }

    private SponsorActivity injectSponsorActivity2(SponsorActivity instance6) {
      SponsorActivity_MembersInjector.injectDataStoreManager(instance6, singletonCImpl.provideDataStoreManagerProvider.get());
      return instance6;
    }

    private WebViewActivity injectWebViewActivity2(WebViewActivity instance7) {
      WebViewActivity_MembersInjector.injectDataStoreManager(instance7, singletonCImpl.provideDataStoreManagerProvider.get());
      return instance7;
    }

    private WelcomeActivity injectWelcomeActivity2(WelcomeActivity instance8) {
      WelcomeActivity_MembersInjector.injectDataStoreManager(instance8, singletonCImpl.provideDataStoreManagerProvider.get());
      return instance8;
    }
  }

  private static final class ViewModelCImpl extends EBookReaderApp_HiltComponents.ViewModelC {
    private final SavedStateHandle savedStateHandle;

    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ViewModelCImpl viewModelCImpl = this;

    Provider<BookNotesViewModel> bookNotesViewModelProvider;

    Provider<HomeViewModel> homeViewModelProvider;

    Provider<ReaderViewModel> readerViewModelProvider;

    Provider<SettingsViewModel> settingsViewModelProvider;

    Provider<StatisticsViewModel> statisticsViewModelProvider;

    Provider<WelcomeViewModel> welcomeViewModelProvider;

    ViewModelCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        SavedStateHandle savedStateHandleParam, ViewModelLifecycle viewModelLifecycleParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.savedStateHandle = savedStateHandleParam;
      initialize(savedStateHandleParam, viewModelLifecycleParam);

    }

    Map hiltViewModelMapMapOfClassOfAndProviderOfViewModelBuilder() {
      MapBuilder mapBuilder = MapBuilder.<String, javax.inject.Provider<ViewModel>>newMapBuilder(6);
      mapBuilder.put(BookNotesViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (bookNotesViewModelProvider)));
      mapBuilder.put(HomeViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (homeViewModelProvider)));
      mapBuilder.put(ReaderViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (readerViewModelProvider)));
      mapBuilder.put(SettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (settingsViewModelProvider)));
      mapBuilder.put(StatisticsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (statisticsViewModelProvider)));
      mapBuilder.put(WelcomeViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (welcomeViewModelProvider)));
      return mapBuilder.build();
    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandle savedStateHandleParam,
        final ViewModelLifecycle viewModelLifecycleParam) {
      this.bookNotesViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 0);
      this.homeViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 1);
      this.readerViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 2);
      this.settingsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 3);
      this.statisticsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 4);
      this.welcomeViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 5);
    }

    @Override
    public Map<Class<?>, javax.inject.Provider<ViewModel>> getHiltViewModelMap() {
      return LazyClassKeyMap.<javax.inject.Provider<ViewModel>>of(hiltViewModelMapMapOfClassOfAndProviderOfViewModelBuilder());
    }

    @Override
    public Map<Class<?>, Object> getHiltViewModelAssistedMap() {
      return Collections.<Class<?>, Object>emptyMap();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final ViewModelCImpl viewModelCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          ViewModelCImpl viewModelCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.viewModelCImpl = viewModelCImpl;
        this.id = id;
      }

      @Override
      @SuppressWarnings("unchecked")
      public T get() {
        switch (id) {
          case 0: // com.huangder.lumibooks.ui.bookshelf.BookNotesViewModel
          return (T) new BookNotesViewModel(viewModelCImpl.savedStateHandle, singletonCImpl.provideBookRepositoryProvider.get(), singletonCImpl.provideReadingRepositoryProvider.get());

          case 1: // com.huangder.lumibooks.ui.home.HomeViewModel
          return (T) new HomeViewModel(singletonCImpl.provideBookRepositoryProvider.get(), singletonCImpl.provideReadingRepositoryProvider.get(), singletonCImpl.provideDataStoreManagerProvider.get(), ApplicationContextModule_ProvideApplicationFactory.provideApplication(singletonCImpl.applicationContextModule));

          case 2: // com.huangder.lumibooks.ui.reader.ReaderViewModel
          return (T) new ReaderViewModel(viewModelCImpl.savedStateHandle, ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.provideBookRepositoryProvider.get(), singletonCImpl.provideReadingRepositoryProvider.get(), singletonCImpl.provideDataStoreManagerProvider.get(), singletonCImpl.provideTtsControllerProvider.get(), singletonCImpl.pdfConversionManagerProvider.get(), singletonCImpl.mineruManualImportManagerProvider.get(), singletonCImpl.mineruTokenStoreProvider.get());

          case 3: // com.huangder.lumibooks.ui.settings.SettingsViewModel
          return (T) new SettingsViewModel(singletonCImpl.provideDataStoreManagerProvider.get(), singletonCImpl.provideBookRepositoryProvider.get(), singletonCImpl.mineruTokenStoreProvider.get(), singletonCImpl.mineruManualImportManagerProvider.get(), ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 4: // com.huangder.lumibooks.ui.statistics.StatisticsViewModel
          return (T) new StatisticsViewModel(singletonCImpl.provideReadingRepositoryProvider.get(), singletonCImpl.provideBookRepositoryProvider.get(), singletonCImpl.provideDataStoreManagerProvider.get());

          case 5: // com.huangder.lumibooks.ui.welcome.WelcomeViewModel
          return (T) new WelcomeViewModel(singletonCImpl.provideDataStoreManagerProvider.get());

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ActivityRetainedCImpl extends EBookReaderApp_HiltComponents.ActivityRetainedC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl = this;

    Provider<ActivityRetainedLifecycle> provideActivityRetainedLifecycleProvider;

    ActivityRetainedCImpl(SingletonCImpl singletonCImpl,
        SavedStateHandleHolder savedStateHandleHolderParam) {
      this.singletonCImpl = singletonCImpl;

      initialize(savedStateHandleHolderParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandleHolder savedStateHandleHolderParam) {
      this.provideActivityRetainedLifecycleProvider = DoubleCheck.provider(new SwitchingProvider<ActivityRetainedLifecycle>(singletonCImpl, activityRetainedCImpl, 0));
    }

    @Override
    public ActivityComponentBuilder activityComponentBuilder() {
      return new ActivityCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public ActivityRetainedLifecycle getActivityRetainedLifecycle() {
      return provideActivityRetainedLifecycleProvider.get();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.id = id;
      }

      @Override
      @SuppressWarnings("unchecked")
      public T get() {
        switch (id) {
          case 0: // dagger.hilt.android.ActivityRetainedLifecycle
          return (T) ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory.provideActivityRetainedLifecycle();

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ServiceCImpl extends EBookReaderApp_HiltComponents.ServiceC {
    private final SingletonCImpl singletonCImpl;

    private final ServiceCImpl serviceCImpl = this;

    ServiceCImpl(SingletonCImpl singletonCImpl, Service serviceParam) {
      this.singletonCImpl = singletonCImpl;


    }

    @Override
    public void injectTtsForegroundService(TtsForegroundService ttsForegroundService) {
      injectTtsForegroundService2(ttsForegroundService);
    }

    private TtsForegroundService injectTtsForegroundService2(TtsForegroundService instance) {
      TtsForegroundService_MembersInjector.injectTtsController(instance, singletonCImpl.provideTtsControllerProvider.get());
      TtsForegroundService_MembersInjector.injectNotificationManager(instance, singletonCImpl.ttsNotificationManagerProvider.get());
      return instance;
    }
  }

  private static final class SingletonCImpl extends EBookReaderApp_HiltComponents.SingletonC {
    private final ApplicationContextModule applicationContextModule;

    private final SingletonCImpl singletonCImpl = this;

    Provider<AppDatabase> provideAppDatabaseProvider;

    Provider<BookDao> provideBookDaoProvider;

    Provider<BookRepository> provideBookRepositoryProvider;

    Provider<DataStoreManager> provideDataStoreManagerProvider;

    Provider<MineruApiClient> mineruApiClientProvider;

    Provider<MineruEpubBuilder> mineruEpubBuilderProvider;

    Provider<MineruTokenStore> mineruTokenStoreProvider;

    Provider<MineruPdfConversionWorker_AssistedFactory> mineruPdfConversionWorker_AssistedFactoryProvider;

    Provider<PdfConversionWorker_AssistedFactory> pdfConversionWorker_AssistedFactoryProvider;

    Provider<ReadingRecordDao> provideReadingRecordDaoProvider;

    Provider<BookmarkDao> provideBookmarkDaoProvider;

    Provider<NoteDao> provideNoteDaoProvider;

    Provider<ReadingRepository> provideReadingRepositoryProvider;

    Provider<TtsEngine> provideTtsEngineProvider;

    Provider<TtsTextExtractor> provideTtsTextExtractorProvider;

    Provider<TtsController> provideTtsControllerProvider;

    Provider<PdfConversionManager> pdfConversionManagerProvider;

    Provider<MineruManualImportManager> mineruManualImportManagerProvider;

    Provider<TtsNotificationManager> ttsNotificationManagerProvider;

    SingletonCImpl(ApplicationContextModule applicationContextModuleParam) {
      this.applicationContextModule = applicationContextModuleParam;
      initialize(applicationContextModuleParam);

    }

    Map mapOfStringAndProviderOfWorkerAssistedFactoryOfBuilder() {
      MapBuilder mapBuilder = MapBuilder.<String, javax.inject.Provider<WorkerAssistedFactory<? extends ListenableWorker>>>newMapBuilder(2);
      mapBuilder.put("com.huangder.lumibooks.pdfconversion.MineruPdfConversionWorker", ((Provider) (mineruPdfConversionWorker_AssistedFactoryProvider)));
      mapBuilder.put("com.huangder.lumibooks.pdfconversion.PdfConversionWorker", ((Provider) (pdfConversionWorker_AssistedFactoryProvider)));
      return mapBuilder.build();
    }

    Map<String, javax.inject.Provider<WorkerAssistedFactory<? extends ListenableWorker>>> mapOfStringAndProviderOfWorkerAssistedFactoryOf(
        ) {
      return mapOfStringAndProviderOfWorkerAssistedFactoryOfBuilder();
    }

    HiltWorkerFactory hiltWorkerFactory() {
      return WorkerFactoryModule_ProvideFactoryFactory.provideFactory(mapOfStringAndProviderOfWorkerAssistedFactoryOf());
    }

    @SuppressWarnings("unchecked")
    private void initialize(final ApplicationContextModule applicationContextModuleParam) {
      this.provideAppDatabaseProvider = DoubleCheck.provider(new SwitchingProvider<AppDatabase>(singletonCImpl, 3));
      this.provideBookDaoProvider = DoubleCheck.provider(new SwitchingProvider<BookDao>(singletonCImpl, 2));
      this.provideBookRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<BookRepository>(singletonCImpl, 1));
      this.provideDataStoreManagerProvider = DoubleCheck.provider(new SwitchingProvider<DataStoreManager>(singletonCImpl, 4));
      this.mineruApiClientProvider = DoubleCheck.provider(new SwitchingProvider<MineruApiClient>(singletonCImpl, 5));
      this.mineruEpubBuilderProvider = DoubleCheck.provider(new SwitchingProvider<MineruEpubBuilder>(singletonCImpl, 6));
      this.mineruTokenStoreProvider = DoubleCheck.provider(new SwitchingProvider<MineruTokenStore>(singletonCImpl, 7));
      this.mineruPdfConversionWorker_AssistedFactoryProvider = SingleCheck.provider(new SwitchingProvider<MineruPdfConversionWorker_AssistedFactory>(singletonCImpl, 0));
      this.pdfConversionWorker_AssistedFactoryProvider = SingleCheck.provider(new SwitchingProvider<PdfConversionWorker_AssistedFactory>(singletonCImpl, 8));
      this.provideReadingRecordDaoProvider = DoubleCheck.provider(new SwitchingProvider<ReadingRecordDao>(singletonCImpl, 10));
      this.provideBookmarkDaoProvider = DoubleCheck.provider(new SwitchingProvider<BookmarkDao>(singletonCImpl, 11));
      this.provideNoteDaoProvider = DoubleCheck.provider(new SwitchingProvider<NoteDao>(singletonCImpl, 12));
      this.provideReadingRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<ReadingRepository>(singletonCImpl, 9));
      this.provideTtsEngineProvider = DoubleCheck.provider(new SwitchingProvider<TtsEngine>(singletonCImpl, 14));
      this.provideTtsTextExtractorProvider = DoubleCheck.provider(new SwitchingProvider<TtsTextExtractor>(singletonCImpl, 15));
      this.provideTtsControllerProvider = DoubleCheck.provider(new SwitchingProvider<TtsController>(singletonCImpl, 13));
      this.pdfConversionManagerProvider = DoubleCheck.provider(new SwitchingProvider<PdfConversionManager>(singletonCImpl, 16));
      this.mineruManualImportManagerProvider = DoubleCheck.provider(new SwitchingProvider<MineruManualImportManager>(singletonCImpl, 17));
      this.ttsNotificationManagerProvider = DoubleCheck.provider(new SwitchingProvider<TtsNotificationManager>(singletonCImpl, 18));
    }

    @Override
    public void injectEBookReaderApp(EBookReaderApp eBookReaderApp) {
      injectEBookReaderApp2(eBookReaderApp);
    }

    @Override
    public Set<Boolean> getDisableFragmentGetContextFix() {
      return Collections.<Boolean>emptySet();
    }

    @Override
    public ActivityRetainedComponentBuilder retainedComponentBuilder() {
      return new ActivityRetainedCBuilder(singletonCImpl);
    }

    @Override
    public ServiceComponentBuilder serviceComponentBuilder() {
      return new ServiceCBuilder(singletonCImpl);
    }

    private EBookReaderApp injectEBookReaderApp2(EBookReaderApp instance) {
      EBookReaderApp_MembersInjector.injectWorkerFactory(instance, hiltWorkerFactory());
      return instance;
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.id = id;
      }

      @Override
      @SuppressWarnings("unchecked")
      public T get() {
        switch (id) {
          case 0: // com.huangder.lumibooks.pdfconversion.MineruPdfConversionWorker_AssistedFactory
          return (T) new MineruPdfConversionWorker_AssistedFactory() {
            @Override
            public MineruPdfConversionWorker create(Context appContext,
                WorkerParameters workerParameters) {
              return new MineruPdfConversionWorker(appContext, workerParameters, singletonCImpl.provideBookRepositoryProvider.get(), singletonCImpl.provideAppDatabaseProvider.get(), singletonCImpl.provideDataStoreManagerProvider.get(), singletonCImpl.mineruApiClientProvider.get(), singletonCImpl.mineruEpubBuilderProvider.get(), singletonCImpl.mineruTokenStoreProvider.get());
            }
          };

          case 1: // com.huangder.lumibooks.domain.repository.BookRepository
          return (T) AppModule_ProvideBookRepositoryFactory.provideBookRepository(singletonCImpl.provideBookDaoProvider.get());

          case 2: // com.huangder.lumibooks.data.local.dao.BookDao
          return (T) AppModule_ProvideBookDaoFactory.provideBookDao(singletonCImpl.provideAppDatabaseProvider.get());

          case 3: // com.huangder.lumibooks.data.local.database.AppDatabase
          return (T) AppModule_ProvideAppDatabaseFactory.provideAppDatabase(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 4: // com.huangder.lumibooks.data.local.DataStoreManager
          return (T) AppModule_ProvideDataStoreManagerFactory.provideDataStoreManager(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 5: // com.huangder.lumibooks.mineru.MineruApiClient
          return (T) new MineruApiClient();

          case 6: // com.huangder.lumibooks.mineru.MineruEpubBuilder
          return (T) new MineruEpubBuilder();

          case 7: // com.huangder.lumibooks.mineru.MineruTokenStore
          return (T) new MineruTokenStore(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 8: // com.huangder.lumibooks.pdfconversion.PdfConversionWorker_AssistedFactory
          return (T) new PdfConversionWorker_AssistedFactory() {
            @Override
            public PdfConversionWorker create(Context appContext2,
                WorkerParameters workerParameters2) {
              return new PdfConversionWorker(appContext2, workerParameters2, singletonCImpl.provideBookRepositoryProvider.get(), singletonCImpl.provideAppDatabaseProvider.get(), new PdfTextExtractor());
            }
          };

          case 9: // com.huangder.lumibooks.domain.repository.ReadingRepository
          return (T) AppModule_ProvideReadingRepositoryFactory.provideReadingRepository(singletonCImpl.provideReadingRecordDaoProvider.get(), singletonCImpl.provideBookmarkDaoProvider.get(), singletonCImpl.provideNoteDaoProvider.get());

          case 10: // com.huangder.lumibooks.data.local.dao.ReadingRecordDao
          return (T) AppModule_ProvideReadingRecordDaoFactory.provideReadingRecordDao(singletonCImpl.provideAppDatabaseProvider.get());

          case 11: // com.huangder.lumibooks.data.local.dao.BookmarkDao
          return (T) AppModule_ProvideBookmarkDaoFactory.provideBookmarkDao(singletonCImpl.provideAppDatabaseProvider.get());

          case 12: // com.huangder.lumibooks.data.local.dao.NoteDao
          return (T) AppModule_ProvideNoteDaoFactory.provideNoteDao(singletonCImpl.provideAppDatabaseProvider.get());

          case 13: // com.huangder.lumibooks.tts.TtsController
          return (T) AppModule_ProvideTtsControllerFactory.provideTtsController(singletonCImpl.provideTtsEngineProvider.get(), singletonCImpl.provideTtsTextExtractorProvider.get(), singletonCImpl.provideDataStoreManagerProvider.get());

          case 14: // com.huangder.lumibooks.tts.TtsEngine
          return (T) AppModule_ProvideTtsEngineFactory.provideTtsEngine(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 15: // com.huangder.lumibooks.tts.TtsTextExtractor
          return (T) AppModule_ProvideTtsTextExtractorFactory.provideTtsTextExtractor();

          case 16: // com.huangder.lumibooks.pdfconversion.PdfConversionManager
          return (T) new PdfConversionManager(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.provideBookRepositoryProvider.get());

          case 17: // com.huangder.lumibooks.mineru.MineruManualImportManager
          return (T) new MineruManualImportManager(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.provideBookRepositoryProvider.get(), singletonCImpl.provideAppDatabaseProvider.get(), singletonCImpl.mineruEpubBuilderProvider.get());

          case 18: // com.huangder.lumibooks.service.TtsNotificationManager
          return (T) new TtsNotificationManager(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          default: throw new AssertionError(id);
        }
      }
    }
  }
}
