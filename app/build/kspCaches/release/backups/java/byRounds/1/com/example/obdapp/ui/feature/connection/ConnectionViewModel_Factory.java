package com.example.obdapp.ui.feature.connection;

import com.example.obdapp.domain.repository.ObdRepository;
import com.example.obdapp.domain.usecase.ConnectDeviceUseCase;
import com.example.obdapp.domain.usecase.GetPairedDevicesUseCase;
import com.example.obdapp.util.PreferencesManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
    "KotlinInternalInJava"
})
public final class ConnectionViewModel_Factory implements Factory<ConnectionViewModel> {
  private final Provider<GetPairedDevicesUseCase> getPairedDevicesUseCaseProvider;

  private final Provider<ConnectDeviceUseCase> connectDeviceUseCaseProvider;

  private final Provider<ObdRepository> repositoryProvider;

  private final Provider<PreferencesManager> preferencesManagerProvider;

  public ConnectionViewModel_Factory(
      Provider<GetPairedDevicesUseCase> getPairedDevicesUseCaseProvider,
      Provider<ConnectDeviceUseCase> connectDeviceUseCaseProvider,
      Provider<ObdRepository> repositoryProvider,
      Provider<PreferencesManager> preferencesManagerProvider) {
    this.getPairedDevicesUseCaseProvider = getPairedDevicesUseCaseProvider;
    this.connectDeviceUseCaseProvider = connectDeviceUseCaseProvider;
    this.repositoryProvider = repositoryProvider;
    this.preferencesManagerProvider = preferencesManagerProvider;
  }

  @Override
  public ConnectionViewModel get() {
    return newInstance(getPairedDevicesUseCaseProvider.get(), connectDeviceUseCaseProvider.get(), repositoryProvider.get(), preferencesManagerProvider.get());
  }

  public static ConnectionViewModel_Factory create(
      Provider<GetPairedDevicesUseCase> getPairedDevicesUseCaseProvider,
      Provider<ConnectDeviceUseCase> connectDeviceUseCaseProvider,
      Provider<ObdRepository> repositoryProvider,
      Provider<PreferencesManager> preferencesManagerProvider) {
    return new ConnectionViewModel_Factory(getPairedDevicesUseCaseProvider, connectDeviceUseCaseProvider, repositoryProvider, preferencesManagerProvider);
  }

  public static ConnectionViewModel newInstance(GetPairedDevicesUseCase getPairedDevicesUseCase,
      ConnectDeviceUseCase connectDeviceUseCase, ObdRepository repository,
      PreferencesManager preferencesManager) {
    return new ConnectionViewModel(getPairedDevicesUseCase, connectDeviceUseCase, repository, preferencesManager);
  }
}
