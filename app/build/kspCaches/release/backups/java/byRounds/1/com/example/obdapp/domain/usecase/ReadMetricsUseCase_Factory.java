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
public final class ReadMetricsUseCase_Factory implements Factory<ReadMetricsUseCase> {
  private final Provider<ObdRepository> repositoryProvider;

  public ReadMetricsUseCase_Factory(Provider<ObdRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public ReadMetricsUseCase get() {
    return newInstance(repositoryProvider.get());
  }

  public static ReadMetricsUseCase_Factory create(Provider<ObdRepository> repositoryProvider) {
    return new ReadMetricsUseCase_Factory(repositoryProvider);
  }

  public static ReadMetricsUseCase newInstance(ObdRepository repository) {
    return new ReadMetricsUseCase(repository);
  }
}
