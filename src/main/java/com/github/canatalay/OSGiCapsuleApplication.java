package com.github.canatalay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.sso.SpringSubframeworkOSGi;

@SpringBootApplication
public class OSGiCapsuleApplication {
    public static void main(String[] args) {
        SpringApplication.run(OSGiCapsuleApplication.class, args);
    }

    public OSGiCapsuleApplication(GenericApplicationContext context, String[] args){
        try{
            SpringSubframeworkOSGi.run(context, true, args);
        } catch(Exception e){
            e.printStackTrace();
        }
    }
}