package com.example.obdapp.ui.feature.dashboard;

import com.example.obdapp.domain.repository.ObdRepository;
import com.example.obdapp.domain.usecase.DisconnectUseCase;
import com.example.obdapp.domain.usecase.ReadMetricsUseCase;
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
public final class DashboardViewModel_Factory implements Factory<DashboardViewModel> {
  private final Provider<ReadMetricsUseCase> readMetricsUseCaseProvider;

  private final Provider<DisconnectUseCase> disconnectUseCaseProvider;

  private final Provider<ObdRepository> repositoryProvider;

  private final Provider<PreferencesManager> preferencesManagerProvider;

  public DashboardViewModel_Factory(Provider<ReadMetricsUseCase> readMetricsUseCaseProvider,
      Provider<DisconnectUseCase> disconnectUseCaseProvider,
      Provider<ObdRepository> repositoryProvider,
      Provider<PreferencesManager> preferencesManagerProvider) {
    this.readMetricsUseCaseProvider = readMetricsUseCaseProvider;
    this.disconnectUseCaseProvider = disconnectUseCaseProvider;
    this.repositoryProvider = repositoryProvider;
    this.preferencesManagerProvider = preferencesManagerProvider;
  }

  @Override
  public DashboardViewModel get() {
    return newInstance(readMetricsUseCaseProvider.get(), disconnectUseCaseProvider.get(), repositoryProvider.get(), preferencesManagerProvider.get());
  }

  public static DashboardViewModel_Factory create(
      Provider<ReadMetricsUseCase> readMetricsUseCaseProvider,
      Provider<DisconnectUseCase> disconnectUseCaseProvider,
      Provider<ObdRepository> repositoryProvider,
      Provider<PreferencesManager> preferencesManagerProvider) {
    return new DashboardViewModel_Factory(readMetricsUseCaseProvider, disconnectUseCaseProvider, repositoryProvider, preferencesManagerProvider);
  }

  public static DashboardViewModel newInstance(ReadMetricsUseCase readMetricsUseCase,
      DisconnectUseCase disconnectUseCase, ObdRepository repository,
      PreferencesManager preferencesManager) {
    return new DashboardViewModel(readMetricsUseCase, disconnectUseCase, repository, preferencesManager);
  }
}
