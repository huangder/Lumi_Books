package com.huangder.lumibooks;

import android.content.Context;
import android.os.Bundle;
import androidx.activity.ComponentActivity;
import androidx.activity.contextaware.OnContextAvailableListener;
import androidx.annotation.CallSuper;
import androidx.lifecycle.ViewModelProvider;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories;
import dagger.hilt.android.internal.managers.ActivityComponentManager;
import dagger.hilt.internal.GeneratedComponentManagerHolder;
import dagger.hilt.internal.UnsafeCasts;
import java.lang.Object;
import java.lang.Override;
import javax.annotation.processing.Generated;

/**
 * A generated base class to be extended by the @dagger.hilt.android.AndroidEntryPoint annotated class. If using the Gradle plugin, this is swapped as the base class via bytecode transformation.
 */
@Generated("dagger.hilt.android.processor.internal.androidentrypoint.ActivityGenerator")
public abstract class Hilt_MainActivity extends ComponentActivity implements GeneratedComponentManagerHolder {
  private volatile ActivityComponentManager componentManager;

  private final Object componentManagerLock = new Object();

  private boolean injected = false;

  Hilt_MainActivity() {
    super();
    _initHiltInternal();
  }

  Hilt_MainActivity(int contentLayoutId) {
    super(contentLayoutId);
    _initHiltInternal();
  }

  private void _initHiltInternal() {
    addOnContextAvailableListener(new OnContextAvailableListener() {
      @Override
      public void onContextAvailable(Context context) {
        inject();
      }
    });
  }

  private void initSavedStateHandleHolders() {
    componentManager().initSavedStateHandleHolders();
  }

  @CallSuper
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    initSavedStateHandleHolders();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    componentManager().clearSavedStateHandleHolders();
  }

  @Override
  public final Object generatedComponent() {
    return this.componentManager().generatedComponent();
  }

  protected ActivityComponentManager createComponentManager() {
    return new ActivityComponentManager(this);
  }

  @Override
  public final ActivityComponentManager componentManager() {
    if (componentManager == null) {
      synchronized (componentManagerLock) {
        if (componentManager == null) {
          componentManager = createComponentManager();
        }
      }
    }
    return componentManager;
  }

  protected void inject() {
    if (!injected) {
      injected = true;
      ((MainActivity_GeneratedInjector) this.generatedComponent()).injectMainActivity(UnsafeCasts.<MainActivity>unsafeCast(this));
    }
  }

  @Override
  public ViewModelProvider.Factory getDefaultViewModelProviderFactory() {
    return DefaultViewModelFactories.getActivityFactory(this, super.getDefaultViewModelProviderFactory());
  }
}
