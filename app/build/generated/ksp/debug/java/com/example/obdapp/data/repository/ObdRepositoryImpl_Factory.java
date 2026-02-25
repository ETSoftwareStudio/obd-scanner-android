package com.example.obdapp.data.repository;

import com.example.obdapp.data.connection.ObdTransport;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class ObdRepositoryImpl_Factory implements Factory<ObdRepositoryImpl> {
  private final Provider<ObdTransport> transportProvider;

  public ObdRepositoryImpl_Factory(Provider<ObdTransport> transportProvider) {
    this.transportProvider = transportProvider;
  }

  @Override
  public ObdRepositoryImpl get() {
    return newInstance(transportProvider.get());
  }

  public static ObdRepositoryImpl_Factory create(Provider<ObdTransport> transportProvider) {
    return new ObdRepositoryImpl_Factory(transportProvider);
  }

  public static ObdRepositoryImpl newInstance(ObdTransport transport) {
    return new ObdRepositoryImpl(transport);
  }
}
