package com.banano.kaliumwallet.di.application;


import javax.inject.Named;

import com.banano.kaliumwallet.di.persistence.PersistenceModule;
import com.banano.kaliumwallet.util.SharedPreferencesUtil;
import dagger.Component;
import io.realm.Realm;

@Component(modules = {ApplicationModule.class, PersistenceModule.class})
@ApplicationScope
public interface ApplicationComponent {
    // persistence module
    SharedPreferencesUtil provideSharedPreferencesUtil();

    // database
    Realm provideRealm();

    // encryption key
    @Named("encryption_key")
    byte[] providesEncryptionKey();
}
