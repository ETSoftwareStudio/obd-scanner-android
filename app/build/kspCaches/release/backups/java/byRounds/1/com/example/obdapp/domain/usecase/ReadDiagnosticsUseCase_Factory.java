package com.example.obdapp.domain.usecase;

import com.example.obdapp.domain.repository.ObdRepository;
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
public final class ReadDiagnosticsUseCase_Factory implements Factory<ReadDiagnosticsUseCase> {
  private final Provider<ObdRepository> repositoryProvider;

  public ReadDiagnosticsUseCase_Factory(Provider<ObdRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public ReadDiagnosticsUseCase get() {
    return newInstance(repositoryProvider.get());
  }

  public static ReadDiagnosticsUseCase_Factory create(Provider<ObdRepository> repositoryProvider) {
    return new ReadDiagnosticsUseCase_Factory(repositoryProvider);
  }

  public static ReadDiagnosticsUseCase newInstance(ObdRepository repository) {
    return new ReadDiagnosticsUseCase(repository);
  }
}
