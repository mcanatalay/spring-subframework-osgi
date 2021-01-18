package org.springframework.sso;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.concierge.Concierge;
import org.osgi.framework.BundleContext;
import org.osgi.framework.launch.Framework;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.sso.exception.SSOAlreadyExistsException;
import org.springframework.sso.exception.SSOAlreadyRunningException;
import org.springframework.sso.exception.SSOInstantiationException;

public class SpringSubframeworkOSGi {
    private static final Map<GenericApplicationContext, SpringSubframeworkOSGi> ssoMap = new HashMap<>();

    public static SpringSubframeworkOSGi get(GenericApplicationContext context){
        return ssoMap.get(context);
    }

    public static SpringSubframeworkOSGi run(GenericApplicationContext context)
        throws SSOAlreadyExistsException, SSOInstantiationException, SSOAlreadyRunningException, SSORunningException{
        return run(context, false, new String[]{}).init();
    }

    public static SpringSubframeworkOSGi run(GenericApplicationContext context, String args[])
        throws SSOAlreadyExistsException, SSOInstantiationException, SSOAlreadyRunningException, SSORunningException{
        return run(context, false, args).init();
    }

    public static SpringSubframeworkOSGi run(GenericApplicationContext context, boolean debug)
        throws SSOAlreadyExistsException, SSOInstantiationException, SSOAlreadyRunningException, SSORunningException{
        return run(context, debug, new String[]{}).init();
    }

    public static SpringSubframeworkOSGi run(GenericApplicationContext context, boolean debug, String args[])
        throws SSOAlreadyExistsException, SSOInstantiationException, SSOAlreadyRunningException, SSORunningException{
        if(get(context) != null){
            throw new SSOAlreadyExistsException();
        }

        SSOLogger.setStatus(debug);

        SpringSubframeworkOSGi sso = new SpringSubframeworkOSGi(context, args).init();
        ssoMap.put(context, sso);
        return sso;
    }

    private final SSOContext context;
    private boolean status;

    private SpringSubframeworkOSGi(GenericApplicationContext springContext, String args[]) throws SSOInstantiationException{        
        Framework osgiFramework;
        try{
            osgiFramework = Concierge.doMain(args);
            osgiFramework.start();
        } catch(Exception e){
            osgiFramework = null;
            throw new SSOInstantiationException(e);
        }

        BundleContext osgiContext = osgiFramework == null ? null : osgiFramework.getBundleContext();

        if(osgiContext == null || osgiFramework == null){
            throw new SSOInstantiationException();
        }

        this.context = new SSOContext(springContext, osgiContext);
        this.status = false;
    }

    public SpringSubframeworkOSGi init() throws SSOAlreadyRunningException, SSORunningException{
        if(status){
            throw new SSOAlreadyRunningException();
        }

        context.start();

        try{
            hook();
            bindOSGiToSpring();
        } catch(Exception e){
            throw new SSORunningException(e);
        }

        return this;
    }

    public boolean isInitialized(){
        return status;
    }

    public SSOContext getContext(){
        return context;
    }

    private void hook() throws Exception{
        context.addListener(new SSOContextListener(){
            @Override
            public void onRegister(SSOReference<?> reference) {
                if(!reference.isTransformed()){
                    context.register(reference.trasform());
                }
            }

            @Override
            public void onUnregister(SSOReference<?> reference) {
                if(reference.isTransformed()){
                    context.unregister(reference);
                }
            }
        });
    }

    private void bindOSGiToSpring(){
        List<SSOReference<?>> references = context.all(SSOContext.TYPE_SERVICE);

        for(SSOReference<?> reference : references){
            context.register(reference.trasform());
        }
    }
}