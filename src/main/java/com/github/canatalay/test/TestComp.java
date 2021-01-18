package com.github.canatalay.test;

import javax.annotation.PostConstruct;

import org.apache.felix.gogo.shell.Shell;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.sso.annotation.PostDynamicWire;
import org.springframework.stereotype.Component;

@Component
public class TestComp implements TestInterface {

    @Autowired
    private Shell test;

    public TestComp(){
        System.out.println("Hello world");
    }
    
    @PostDynamicWire
    public void init(){
        print();
    }

    @Override
    public void print() {
        System.out.println(test.hashCode());        
    }
}
