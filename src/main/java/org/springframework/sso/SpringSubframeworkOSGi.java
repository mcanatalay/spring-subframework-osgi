package org.springframework.sso;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.concierge.Concierge;
import org.osgi.framework.BundleContext;
import org.osgi.framework.launch.Framework;
import org.springframework.context.support.GenericApplicationContext;

public class SpringSubframeworkOSGi<A> {
    private static final Map<Class<?>, SpringSubframeworkOSGi<?>> ssoMap = new HashMap<>();

    public static <A> SpringSubframeworkOSGi<A> get(Class<A> appClazz){
        return (SpringSubframeworkOSGi<A>) ssoMap.get(appClazz);
    }

    public static <A> SpringSubframeworkOSGi<A> run(Class<A> appClazz, GenericApplicationContext context)
        throws SSOAlreadyExistsException, SSOInstantiationException, SSOAlreadyRunningException, SSORunningException{
        return run(appClazz, context, false, new String[]{}).init();
    }

    public static <A> SpringSubframeworkOSGi<A> run(Class<A> appClazz, GenericApplicationContext context, String args[])
        throws SSOAlreadyExistsException, SSOInstantiationException, SSOAlreadyRunningException, SSORunningException{
        return run(appClazz, context, false, args).init();
    }

    public static <A> SpringSubframeworkOSGi<A> run(Class<A> appClazz, GenericApplicationContext context, boolean debug)
        throws SSOAlreadyExistsException, SSOInstantiationException, SSOAlreadyRunningException, SSORunningException{
        return run(appClazz, context, debug, new String[]{}).init();
    }

    public static <A> SpringSubframeworkOSGi<A> run(Class<A> appClazz, GenericApplicationContext context, boolean debug, String args[])
        throws SSOAlreadyExistsException, SSOInstantiationException, SSOAlreadyRunningException, SSORunningException{
        if(get(appClazz) != null){
            throw new SSOAlreadyExistsException();
        }

        SSOLogger.setStatus(debug);

        SpringSubframeworkOSGi<A> sso = new SpringSubframeworkOSGi<>(appClazz, context, args).init();
        ssoMap.put(appClazz, sso);
        return sso;
    }

    private final SSOContext<A> context;
    private boolean status;

    private SpringSubframeworkOSGi(Class<A> appClazz, GenericApplicationContext springContext, String args[]) throws SSOInstantiationException{        
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

        this.context = new SSOContext<>(appClazz, springContext, osgiContext);
        this.status = false;
    }

    public SpringSubframeworkOSGi<A> init() throws SSOAlreadyRunningException, SSORunningException{
        if(status){
            throw new SSOAlreadyRunningException();
        }

        context.start();

        try{
            hook();
            bindOSGiToSpring();
            context.getOSGiContext().startBundles();
        } catch(Exception e){
            throw new SSORunningException(e);
        }

        return this;
    }

    public boolean isInitialized(){
        return status;
    }

    public SSOContext<A> getContext(){
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