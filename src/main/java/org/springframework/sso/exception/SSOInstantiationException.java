package org.springframework.sso.exception;

public class SSOInstantiationException extends Exception {
    private static final long serialVersionUID = 1L;

    public SSOInstantiationException(Exception parent){
        super(parent);
    }

    public SSOInstantiationException(){
        super();
    }
}
