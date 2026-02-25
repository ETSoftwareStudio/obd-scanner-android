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
public final class GetPairedDevicesUseCase_Factory implements Factory<GetPairedDevicesUseCase> {
  private final Provider<ObdRepository> repositoryProvider;

  public GetPairedDevicesUseCase_Factory(Provider<ObdRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public GetPairedDevicesUseCase get() {
    return newInstance(repositoryProvider.get());
  }

  public static GetPairedDevicesUseCase_Factory create(Provider<ObdRepository> repositoryProvider) {
    return new GetPairedDevicesUseCase_Factory(repositoryProvider);
  }

  public static GetPairedDevicesUseCase newInstance(ObdRepository repository) {
    return new GetPairedDevicesUseCase(repository);
  }
}
