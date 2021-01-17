package org.springframework.sso;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.support.GenericApplicationContext;

public class SSOContext<A> {
    private static final SSOLogger LOGGER = SSOLogger.getLogger(SSOContext.class.getSimpleName());

    public static final String SERVICE_BEAN_FLAG = "flag.bean";
    public static final String SERVICE_PID = "service.pid";

    public static final Integer TYPE_BEAN = 1;
    public static final Integer TYPE_SERVICE = 2;
    public static final Integer TYPE_TRANSFORMED_BEAN = 10;
    public static final Integer TYPE_TRANSFORMED_SERVICE = 20;

    private final Class<A> appClazz;
    private final GenericApplicationContext springContext;
    private final BundleContext osgiContext;
    private final List<SSOContextListener> listeners;

    private final List<String> processedBeans;
    private boolean status;

    public SSOContext(Class<A> appClazz, GenericApplicationContext springContext, BundleContext osgiContext) {
        this.appClazz = appClazz;
        this.springContext = springContext;
        this.osgiContext = osgiContext;
        this.listeners = Collections.synchronizedList(new ArrayList<>());

        this.processedBeans = Collections.synchronizedList(new ArrayList<>());
        this.status = false;
    }

    public void start(){
        if(status){
            return;
        }

        hookOSGi();
        hookSpring();
    }

    public SSOsgiContext getOSGiContext(){
        return new SSOsgiContext(osgiContext);
    }

    public void addListener(SSOContextListener listener){
        if(listeners.contains(listener)){
            return;
        } else{
            listeners.add(listener);
        }
    }

    public void removeListener(SSOContextListener listener){
        if(!listeners.contains(listener)){
            return;
        } else{
            listeners.remove(listener);
        }
    }

    public List<SSOContextListener> getListeners(){
        return new ArrayList<>(listeners);
    }

    public void register(SSOReference<?> reference) {
        try {
            this.register0(SSOReference.cast(reference.getClazz(), reference));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void unregister(SSOReference<?> reference) {
        try {
            this.unregister0(SSOReference.cast(reference.getClazz(), reference));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<SSOReference<?>> all(Integer type){
        try{
            return all0(type);
        } catch(Exception e){
            return Collections.emptyList();
        }
    }

    public <E> List<SSOReference<E>> get(Integer type, Class<E> clazz, String name){
        try{
            return get0(type, clazz, name);
        } catch(Exception e){
            return Collections.emptyList();
        }
    }

    public <E> boolean exist(Integer type, Class<E> clazz, String name){
        return exist0(type, clazz, name);
    }

    private final List<SSOReference<?>> all0(Integer type) throws Exception{
        if(type == TYPE_BEAN || type == TYPE_TRANSFORMED_SERVICE){
            return Stream.of(springContext.getBeanDefinitionNames())
                .map(defName -> SSOReference.create(type, null, defName, null))
                .collect(Collectors.toList());
        } else if(type == TYPE_SERVICE || type == TYPE_TRANSFORMED_BEAN){
            return serviceReferences(type).stream()
                .map(reference -> transform(reference))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    private final <E> boolean exist0(Integer type, Class<E> clazz, String name){
        try{
            return !get0(type, clazz, name).isEmpty();
        } catch(Exception e){
            return false;
        }
    }

    private final <E> List<SSOReference<E>> get0(Integer type, Class<E> clazz, String name) throws Exception{
        if(type == TYPE_BEAN || type == TYPE_TRANSFORMED_SERVICE){
            return Stream.of(springContext.getBeanDefinitionNames())
                .filter(defName -> name.equals(defName))
                .filter(defName -> 
                        (type == TYPE_BEAN && !exist0(TYPE_SERVICE, clazz, defName))
                    ||
                        (type == TYPE_TRANSFORMED_SERVICE && exist0(TYPE_SERVICE, clazz, defName))
                )
                .map(defName -> SSOReference.create(type, clazz, name, null))
                .collect(Collectors.toList());
        } else if(type == TYPE_SERVICE || type == TYPE_TRANSFORMED_BEAN){
            return serviceReferences(type).stream()
                    .map(reference -> transform(reference))
                    .flatMap(Collection::stream)
                    .map(sso -> SSOReference.cast(clazz, sso))
                    .filter(sso -> sso.getName().equals(name) && sso.getClazz().equals(clazz))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    private final <E> void register0(SSOReference<E> reference) throws Exception{
        if(exist0(reference.getType(), reference.getClazz(), reference.getName()) ||
            exist0(transform(reference.getType(), true), reference.getClazz(), reference.getName())){
            return;
        }

        if(reference.getType() == TYPE_BEAN || reference.getType() == TYPE_TRANSFORMED_SERVICE){
            if(reference.getClazz().isInstance(reference.getObj())){
                springContext.registerBean(reference.getName(), reference.getClazz(), () -> (E) reference.getObj());
            }
        } else if(reference.getType() == TYPE_SERVICE || reference.getType() == TYPE_TRANSFORMED_BEAN){
            Dictionary<String, String> configs = new Hashtable<>();
            configs.put(SERVICE_PID, reference.getName());
            if(reference.isTransformed()){
                configs.put(SERVICE_BEAN_FLAG, "true");
            }
    
            osgiContext.registerService(reference.getClazz(), (E) reference.getObj(), configs);
            
            //TODO remove this
            if(reference.getType() == TYPE_TRANSFORMED_BEAN){
                Class<?>[] interfaces = reference.getObj().getClass().getInterfaces();
                if(interfaces != null){
                    for(Class<?> clazz : interfaces){
                        if(!clazz.getName().startsWith("java")){
                            registerInterfacesForOSGi(reference.getName(), clazz, reference.getObj());
                        }
                    }
                }
            }
        }

        LOGGER.info(reference.getType() + " " + reference.getName() + " REGISTERED!");
    }

    @Deprecated
    private final <E> void registerInterfacesForOSGi(String name, Class<E> clazz, Object bean){
        Dictionary<String, String> configs = new Hashtable<>();
        configs.put(SERVICE_PID, name);
        configs.put(SERVICE_BEAN_FLAG, "true");

        osgiContext.registerService(clazz, (E) bean, configs);
        LOGGER.info(TYPE_TRANSFORMED_BEAN + " " + clazz + "(" + name + ")" + " INTERFACE REGISTERED!");
    }

    private final <E> void unregister0(SSOReference<E> reference) throws Exception{
        if(!exist0(reference.getType(), reference.getClazz(), reference.getName())){
            return;
        }

        if(reference.getType() == TYPE_BEAN || reference.getType() == TYPE_TRANSFORMED_SERVICE){
            springContext.removeBeanDefinition(reference.getName());
        } else if(reference.getType() == TYPE_SERVICE || reference.getType() == TYPE_TRANSFORMED_BEAN){
            serviceReferences(reference.getType()).stream()
                .filter((ref) -> {
                    List<SSOReference<?>> others = transform(ref);
                    
                    return  others.stream().anyMatch(other ->
                            other.getName().equals(reference.getName())
                        &&
                            other.getClazz().equals(reference.getClazz())
                    );
                })
                .forEach(ref -> osgiContext.ungetService(ref));
        }

        LOGGER.info(reference.getType() + " " + reference.getName() + " UNREGISTERED!");
    }

    private final void hookOSGi(){
        osgiContext.addServiceListener(new ServiceListener(){
            @Override
            public void serviceChanged(ServiceEvent event) {
                if(event.getType() == ServiceEvent.REGISTERED){
                    transform(event.getServiceReference()).forEach(ref -> publishRegister(ref));
                } else if(event.getType() == ServiceEvent.UNREGISTERING){
                    transform(event.getServiceReference()).forEach(ref -> publishUnregister(ref));
                }
            }
        });
    }

    private final void hookSpring(){
        springContext.getBeanFactory().addBeanPostProcessor(new BeanPostProcessor(){
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if(
                    !bean.getClass().getName().startsWith("org.springframework") &&
                    !bean.getClass().getName().startsWith(appClazz.getName()) &&
                    !processedBeans.contains(beanName)
                ){
                    processedBeans.add(beanName);
                    publishRegister(transform(bean, beanName));
                }
                return null;
            }
        });
    }

    private final SSOReference<?> transform(Object bean, String beanName){
        return SSOReference.create(
            exist0(TYPE_TRANSFORMED_SERVICE, bean.getClass(), beanName) ? TYPE_TRANSFORMED_SERVICE : TYPE_BEAN,
            bean.getClass(),
            beanName,
            bean
        );
    }

    private final List<SSOReference<?>> transform(ServiceReference<?> reference){
        Object service = osgiContext.getService(reference);
        String name = (String) reference.getProperty(SERVICE_PID);

        List<SSOReference<?>> refs = new ArrayList<>();
        for(String clazzName : (String[]) reference.getProperty(Constants.OBJECTCLASS)){
            try{
                Class<?> clazz = Class.forName(clazzName);
                refs.add(SSOReference.create(
                    reference.getProperty(SERVICE_BEAN_FLAG) != null ? TYPE_TRANSFORMED_BEAN : TYPE_SERVICE,
                    clazz,
                    name == null ? clazzName : name,
                    service
                ));
            } catch(Exception e){
                ;
            }
        }

        return refs;
    }

    private final Integer transform(Integer type, boolean opposite){
        if(type == TYPE_BEAN){
            return !opposite ? TYPE_TRANSFORMED_BEAN : TYPE_SERVICE;
        } else if(type == TYPE_SERVICE){
            return !opposite ? TYPE_TRANSFORMED_SERVICE : TYPE_BEAN;
        } else if(type == TYPE_TRANSFORMED_BEAN){
            return !opposite ? TYPE_BEAN : TYPE_SERVICE;
        } else if(type == TYPE_TRANSFORMED_SERVICE){
            return !opposite ? TYPE_SERVICE : TYPE_BEAN;
        } else{
            return type;
        }
    }

    private final void publishRegister(SSOReference<?> reference){
        for(SSOContextListener listener : listeners){
            try{
                listener.onRegister(reference);
            } catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    private final void publishUnregister(SSOReference<?> reference){
        for(SSOContextListener listener : listeners){
            try{
                listener.onUnregister(reference);
            } catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    private final List<ServiceReference<?>> serviceReferences(Integer type){
        List<ServiceReference<?>> references = new ArrayList<>();
        Bundle[] bundles = osgiContext.getBundles();
        if(bundles != null){
            for(Bundle bundle : bundles){
                ServiceReference<?>[] refs = bundle.getRegisteredServices();
                if(refs != null){
                    for(ServiceReference<?> reference : refs){
                        references.add(reference);
                    }
                }
            }
        }

        return references.stream()
            .filter(reference ->
                    (type == TYPE_SERVICE && reference.getProperty(SERVICE_BEAN_FLAG) == null)
                ||
                    (type == TYPE_TRANSFORMED_BEAN && reference.getProperty(SERVICE_BEAN_FLAG) != null)
            )
            .collect(Collectors.toList());
    }
}