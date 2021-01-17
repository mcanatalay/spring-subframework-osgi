package org.springframework.sso;

public class SSOLogger {
    private static boolean status = false;

    private final String name;

    public static void setStatus(boolean status){
        SSOLogger.status = status;
    }

    public static final SSOLogger getLogger(String name){
        return new SSOLogger(name);
    }

    private SSOLogger(String name){
        this.name = name;
    }

    public void info(String text){
        if(status){
            System.out.println(name + ":\t" + text);
        }
    }

    public void error(String text){
        if(status){
            System.err.println(name + ":\t" + text);
        }
    }
}
