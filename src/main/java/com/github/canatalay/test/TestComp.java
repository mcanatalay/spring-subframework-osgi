package com.github.canatalay.test;

import javax.annotation.PostConstruct;

import org.apache.felix.gogo.shell.Shell;
import org.apache.felix.service.threadio.ThreadIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TestComp {

    @Autowired
    private Shell test;

    public TestComp(){
        System.out.println("Hello world");
    }
    
    @PostConstruct
    public void init(){
        System.out.println(test.hashCode());
    }
}
