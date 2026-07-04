package com.huangder.lumibooks;

import android.app.Activity;
import android.app.Service;
import android.view.View;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
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
import com.huangder.lumibooks.domain.repository.BookRepository;
import com.huangder.lumibooks.domain.repository.ReadingRepository;
import com.huangder.lumibooks.ui.home.HomeViewModel;
import com.huangder.lumibooks.ui.home.HomeViewModel_HiltModules;
import com.huangder.lumibooks.ui.reader.ReaderViewModel;
import com.huangder.lumibooks.ui.reader.ReaderViewModel_HiltModules;
import com.huangder.lumibooks.ui.settings.SettingsViewModel;
import com.huangder.lumibooks.ui.settings.SettingsViewModel_HiltModules;
import com.huangder.lumibooks.ui.statistics.StatisticsViewModel;
import com.huangder.lumibooks.ui.statistics.StatisticsViewModel_HiltModules;
import com.huangder.lumibooks.ui.welcome.WelcomeViewModel;
import com.huangder.lumibooks.ui.welcome.WelcomeViewModel_HiltModules;
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
import dagger.hilt.android.internal.modules.ApplicationContextModule_ProvideContextFactory;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
import dagger.internal.IdentifierNameString;
import dagger.internal.KeepFieldType;
import dagger.internal.LazyClassKeyMap;
import dagger.internal.MapBuilder;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
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
    "deprecation"
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

    private ViewWithFragmentCImpl(SingletonCImpl singletonCImpl,
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

    private FragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        Fragment fragmentParam) {
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

    private ViewCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
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

    private ActivityCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, Activity activityParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;


    }

    @Override
    public void injectMainActivity(MainActivity mainActivity) {
      injectMainActivity2(mainActivity);
    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return DefaultViewModelFactories_InternalFactoryFactory_Factory.newInstance(getViewModelKeys(), new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl));
    }

    @Override
    public Map<Class<?>, Boolean> getViewModelKeys() {
      return LazyClassKeyMap.<Boolean>of(MapBuilder.<String, Boolean>newMapBuilder(5).put(LazyClassKeyProvider.com_huangder_lumibooks_ui_home_HomeViewModel, HomeViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_huangder_lumibooks_ui_reader_ReaderViewModel, ReaderViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_huangder_lumibooks_ui_settings_SettingsViewModel, SettingsViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_huangder_lumibooks_ui_statistics_StatisticsViewModel, StatisticsViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_huangder_lumibooks_ui_welcome_WelcomeViewModel, WelcomeViewModel_HiltModules.KeyModule.provide()).build());
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
      return instance;
    }

    @IdentifierNameString
    private static final class LazyClassKeyProvider {
      static String com_huangder_lumibooks_ui_statistics_StatisticsViewModel = "com.huangder.lumibooks.ui.statistics.StatisticsViewModel";

      static String com_huangder_lumibooks_ui_home_HomeViewModel = "com.huangder.lumibooks.ui.home.HomeViewModel";

      static String com_huangder_lumibooks_ui_settings_SettingsViewModel = "com.huangder.lumibooks.ui.settings.SettingsViewModel";

      static String com_huangder_lumibooks_ui_reader_ReaderViewModel = "com.huangder.lumibooks.ui.reader.ReaderViewModel";

      static String com_huangder_lumibooks_ui_welcome_WelcomeViewModel = "com.huangder.lumibooks.ui.welcome.WelcomeViewModel";

      @KeepFieldType
      StatisticsViewModel com_huangder_lumibooks_ui_statistics_StatisticsViewModel2;

      @KeepFieldType
      HomeViewModel com_huangder_lumibooks_ui_home_HomeViewModel2;

      @KeepFieldType
      SettingsViewModel com_huangder_lumibooks_ui_settings_SettingsViewModel2;

      @KeepFieldType
      ReaderViewModel com_huangder_lumibooks_ui_reader_ReaderViewModel2;

      @KeepFieldType
      WelcomeViewModel com_huangder_lumibooks_ui_welcome_WelcomeViewModel2;
    }
  }

  private static final class ViewModelCImpl extends EBookReaderApp_HiltComponents.ViewModelC {
    private final SavedStateHandle savedStateHandle;

    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ViewModelCImpl viewModelCImpl = this;

    private Provider<HomeViewModel> homeViewModelProvider;

    private Provider<ReaderViewModel> readerViewModelProvider;

    private Provider<SettingsViewModel> settingsViewModelProvider;

    private Provider<StatisticsViewModel> statisticsViewModelProvider;

    private Provider<WelcomeViewModel> welcomeViewModelProvider;

    private ViewModelCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, SavedStateHandle savedStateHandleParam,
        ViewModelLifecycle viewModelLifecycleParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.savedStateHandle = savedStateHandleParam;
      initialize(savedStateHandleParam, viewModelLifecycleParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandle savedStateHandleParam,
        final ViewModelLifecycle viewModelLifecycleParam) {
      this.homeViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 0);
      this.readerViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 1);
      this.settingsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 2);
      this.statisticsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 3);
      this.welcomeViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 4);
    }

    @Override
    public Map<Class<?>, javax.inject.Provider<ViewModel>> getHiltViewModelMap() {
      return LazyClassKeyMap.<javax.inject.Provider<ViewModel>>of(MapBuilder.<String, javax.inject.Provider<ViewModel>>newMapBuilder(5).put(LazyClassKeyProvider.com_huangder_lumibooks_ui_home_HomeViewModel, ((Provider) homeViewModelProvider)).put(LazyClassKeyProvider.com_huangder_lumibooks_ui_reader_ReaderViewModel, ((Provider) readerViewModelProvider)).put(LazyClassKeyProvider.com_huangder_lumibooks_ui_settings_SettingsViewModel, ((Provider) settingsViewModelProvider)).put(LazyClassKeyProvider.com_huangder_lumibooks_ui_statistics_StatisticsViewModel, ((Provider) statisticsViewModelProvider)).put(LazyClassKeyProvider.com_huangder_lumibooks_ui_welcome_WelcomeViewModel, ((Provider) welcomeViewModelProvider)).build());
    }

    @Override
    public Map<Class<?>, Object> getHiltViewModelAssistedMap() {
      return Collections.<Class<?>, Object>emptyMap();
    }

    @IdentifierNameString
    private static final class LazyClassKeyProvider {
      static String com_huangder_lumibooks_ui_reader_ReaderViewModel = "com.huangder.lumibooks.ui.reader.ReaderViewModel";

      static String com_huangder_lumibooks_ui_home_HomeViewModel = "com.huangder.lumibooks.ui.home.HomeViewModel";

      static String com_huangder_lumibooks_ui_welcome_WelcomeViewModel = "com.huangder.lumibooks.ui.welcome.WelcomeViewModel";

      static String com_huangder_lumibooks_ui_statistics_StatisticsViewModel = "com.huangder.lumibooks.ui.statistics.StatisticsViewModel";

      static String com_huangder_lumibooks_ui_settings_SettingsViewModel = "com.huangder.lumibooks.ui.settings.SettingsViewModel";

      @KeepFieldType
      ReaderViewModel com_huangder_lumibooks_ui_reader_ReaderViewModel2;

      @KeepFieldType
      HomeViewModel com_huangder_lumibooks_ui_home_HomeViewModel2;

      @KeepFieldType
      WelcomeViewModel com_huangder_lumibooks_ui_welcome_WelcomeViewModel2;

      @KeepFieldType
      StatisticsViewModel com_huangder_lumibooks_ui_statistics_StatisticsViewModel2;

      @KeepFieldType
      SettingsViewModel com_huangder_lumibooks_ui_settings_SettingsViewModel2;
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

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.huangder.lumibooks.ui.home.HomeViewModel 
          return (T) new HomeViewModel(singletonCImpl.provideBookRepositoryProvider.get(), singletonCImpl.provideReadingRepositoryProvider.get(), singletonCImpl.provideDataStoreManagerProvider.get());

          case 1: // com.huangder.lumibooks.ui.reader.ReaderViewModel 
          return (T) new ReaderViewModel(viewModelCImpl.savedStateHandle, ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.provideBookRepositoryProvider.get(), singletonCImpl.provideReadingRepositoryProvider.get(), singletonCImpl.provideDataStoreManagerProvider.get());

          case 2: // com.huangder.lumibooks.ui.settings.SettingsViewModel 
          return (T) new SettingsViewModel(singletonCImpl.provideDataStoreManagerProvider.get(), ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 3: // com.huangder.lumibooks.ui.statistics.StatisticsViewModel 
          return (T) new StatisticsViewModel(singletonCImpl.provideReadingRepositoryProvider.get(), singletonCImpl.provideBookRepositoryProvider.get(), singletonCImpl.provideDataStoreManagerProvider.get());

          case 4: // com.huangder.lumibooks.ui.welcome.WelcomeViewModel 
          return (T) new WelcomeViewModel(singletonCImpl.provideDataStoreManagerProvider.get());

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ActivityRetainedCImpl extends EBookReaderApp_HiltComponents.ActivityRetainedC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl = this;

    private Provider<ActivityRetainedLifecycle> provideActivityRetainedLifecycleProvider;

    private ActivityRetainedCImpl(SingletonCImpl singletonCImpl,
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

      @SuppressWarnings("unchecked")
      @Override
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

    private ServiceCImpl(SingletonCImpl singletonCImpl, Service serviceParam) {
      this.singletonCImpl = singletonCImpl;


    }
  }

  private static final class SingletonCImpl extends EBookReaderApp_HiltComponents.SingletonC {
    private final ApplicationContextModule applicationContextModule;

    private final SingletonCImpl singletonCImpl = this;

    private Provider<DataStoreManager> provideDataStoreManagerProvider;

    private Provider<AppDatabase> provideAppDatabaseProvider;

    private Provider<BookDao> provideBookDaoProvider;

    private Provider<BookRepository> provideBookRepositoryProvider;

    private Provider<ReadingRecordDao> provideReadingRecordDaoProvider;

    private Provider<BookmarkDao> provideBookmarkDaoProvider;

    private Provider<NoteDao> provideNoteDaoProvider;

    private Provider<ReadingRepository> provideReadingRepositoryProvider;

    private SingletonCImpl(ApplicationContextModule applicationContextModuleParam) {
      this.applicationContextModule = applicationContextModuleParam;
      initialize(applicationContextModuleParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final ApplicationContextModule applicationContextModuleParam) {
      this.provideDataStoreManagerProvider = DoubleCheck.provider(new SwitchingProvider<DataStoreManager>(singletonCImpl, 0));
      this.provideAppDatabaseProvider = DoubleCheck.provider(new SwitchingProvider<AppDatabase>(singletonCImpl, 3));
      this.provideBookDaoProvider = DoubleCheck.provider(new SwitchingProvider<BookDao>(singletonCImpl, 2));
      this.provideBookRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<BookRepository>(singletonCImpl, 1));
      this.provideReadingRecordDaoProvider = DoubleCheck.provider(new SwitchingProvider<ReadingRecordDao>(singletonCImpl, 5));
      this.provideBookmarkDaoProvider = DoubleCheck.provider(new SwitchingProvider<BookmarkDao>(singletonCImpl, 6));
      this.provideNoteDaoProvider = DoubleCheck.provider(new SwitchingProvider<NoteDao>(singletonCImpl, 7));
      this.provideReadingRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<ReadingRepository>(singletonCImpl, 4));
    }

    @Override
    public void injectEBookReaderApp(EBookReaderApp eBookReaderApp) {
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

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.huangder.lumibooks.data.local.DataStoreManager 
          return (T) AppModule_ProvideDataStoreManagerFactory.provideDataStoreManager(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 1: // com.huangder.lumibooks.domain.repository.BookRepository 
          return (T) AppModule_ProvideBookRepositoryFactory.provideBookRepository(singletonCImpl.provideBookDaoProvider.get());

          case 2: // com.huangder.lumibooks.data.local.dao.BookDao 
          return (T) AppModule_ProvideBookDaoFactory.provideBookDao(singletonCImpl.provideAppDatabaseProvider.get());

          case 3: // com.huangder.lumibooks.data.local.database.AppDatabase 
          return (T) AppModule_ProvideAppDatabaseFactory.provideAppDatabase(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 4: // com.huangder.lumibooks.domain.repository.ReadingRepository 
          return (T) AppModule_ProvideReadingRepositoryFactory.provideReadingRepository(singletonCImpl.provideReadingRecordDaoProvider.get(), singletonCImpl.provideBookmarkDaoProvider.get(), singletonCImpl.provideNoteDaoProvider.get());

          case 5: // com.huangder.lumibooks.data.local.dao.ReadingRecordDao 
          return (T) AppModule_ProvideReadingRecordDaoFactory.provideReadingRecordDao(singletonCImpl.provideAppDatabaseProvider.get());

          case 6: // com.huangder.lumibooks.data.local.dao.BookmarkDao 
          return (T) AppModule_ProvideBookmarkDaoFactory.provideBookmarkDao(singletonCImpl.provideAppDatabaseProvider.get());

          case 7: // com.huangder.lumibooks.data.local.dao.NoteDao 
          return (T) AppModule_ProvideNoteDaoFactory.provideNoteDao(singletonCImpl.provideAppDatabaseProvider.get());

          default: throw new AssertionError(id);
        }
      }
    }
  }
}
