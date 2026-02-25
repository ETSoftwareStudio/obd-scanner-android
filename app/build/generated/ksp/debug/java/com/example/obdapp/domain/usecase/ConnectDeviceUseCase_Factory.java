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
public final class ConnectDeviceUseCase_Factory implements Factory<ConnectDeviceUseCase> {
  private final Provider<ObdRepository> repositoryProvider;

  public ConnectDeviceUseCase_Factory(Provider<ObdRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public ConnectDeviceUseCase get() {
    return newInstance(repositoryProvider.get());
  }

  public static ConnectDeviceUseCase_Factory create(Provider<ObdRepository> repositoryProvider) {
    return new ConnectDeviceUseCase_Factory(repositoryProvider);
  }

  public static ConnectDeviceUseCase newInstance(ObdRepository repository) {
    return new ConnectDeviceUseCase(repository);
  }
}
