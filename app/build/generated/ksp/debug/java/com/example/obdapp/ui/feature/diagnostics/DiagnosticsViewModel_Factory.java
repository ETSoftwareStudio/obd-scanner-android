package com.example.obdapp.ui.feature.diagnostics;

import com.example.obdapp.domain.repository.ObdRepository;
import com.example.obdapp.domain.usecase.ReadDiagnosticsUseCase;
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
public final class DiagnosticsViewModel_Factory implements Factory<DiagnosticsViewModel> {
  private final Provider<ReadDiagnosticsUseCase> readDiagnosticsUseCaseProvider;

  private final Provider<ObdRepository> repositoryProvider;

  public DiagnosticsViewModel_Factory(
      Provider<ReadDiagnosticsUseCase> readDiagnosticsUseCaseProvider,
      Provider<ObdRepository> repositoryProvider) {
    this.readDiagnosticsUseCaseProvider = readDiagnosticsUseCaseProvider;
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public DiagnosticsViewModel get() {
    return newInstance(readDiagnosticsUseCaseProvider.get(), repositoryProvider.get());
  }

  public static DiagnosticsViewModel_Factory create(
      Provider<ReadDiagnosticsUseCase> readDiagnosticsUseCaseProvider,
      Provider<ObdRepository> repositoryProvider) {
    return new DiagnosticsViewModel_Factory(readDiagnosticsUseCaseProvider, repositoryProvider);
  }

  public static DiagnosticsViewModel newInstance(ReadDiagnosticsUseCase readDiagnosticsUseCase,
      ObdRepository repository) {
    return new DiagnosticsViewModel(readDiagnosticsUseCase, repository);
  }
}
