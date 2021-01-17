package org.springframework.sso;

public interface SSOContextListener  {
    void onRegister(SSOReference<?> reference);

    void onUnregister(SSOReference<?> reference);
}
