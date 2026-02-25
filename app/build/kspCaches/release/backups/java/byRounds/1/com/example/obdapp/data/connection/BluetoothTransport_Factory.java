package com.example.obdapp.data.connection;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class BluetoothTransport_Factory implements Factory<BluetoothTransport> {
  @Override
  public BluetoothTransport get() {
    return newInstance();
  }

  public static BluetoothTransport_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static BluetoothTransport newInstance() {
    return new BluetoothTransport();
  }

  private static final class InstanceHolder {
    private static final BluetoothTransport_Factory INSTANCE = new BluetoothTransport_Factory();
  }
}
