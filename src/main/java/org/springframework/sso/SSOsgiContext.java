package org.springframework.sso;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

public class SSOsgiContext {
    private final BundleContext context;

    public SSOsgiContext(BundleContext context){
        this.context = context;
    }

    public void register(Class<?> clazz, String name, Object obj){
        context.registerService(clazz.getName(), obj, null);
    }

    public List<String> startBundles(){
        try{
            Bundle[] bundles = context.getBundles();
            if(bundles != null){
                for(Bundle bundle : Stream.of(bundles).sorted().collect(Collectors.toList())){
                    bundle.start();
                }

                return Stream.of(bundles).sorted().map(bundle -> bundle.getSymbolicName()).collect(Collectors.toList());
            }
        } catch(Exception e){
            e.printStackTrace();
        }

        return Collections.emptyList();
    };

    public Long installBundle(String bundleLocation){
        try{
            return context.installBundle(bundleLocation).getBundleId();
        } catch(Exception e){
            return null;
        }
    }

    public String startBundle(Long bundleID){
        if(bundleID != null){
            try{
                Bundle bundle = context.getBundle(bundleID);
                if(bundle != null){
                    bundle.start();
                    return bundle.getSymbolicName();
                }
            } catch (Exception e){
                e.printStackTrace();
            }
        }

        return null;
    };

    public String startBundle(String bundleLocation){
        return startBundle(installBundle(bundleLocation));
    };
}
