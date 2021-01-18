package org.springframework.sso;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.sso.annotation.PostDynamicWire;

public class SpringDynamicWiringAdmin{
    private static final SSOLogger LOGGER = SSOLogger.getLogger(SpringDynamicWiringAdmin.class.getSimpleName());
    private static final Map<GenericApplicationContext, SpringDynamicWiringAdmin> instanceMap = new HashMap<>();

    private final GenericApplicationContext context;
    private final Set<Class<?>> registredBeans;
    private final List<BeanDynamicContext> dynamicContexts;
    private boolean status;

    public static final SpringDynamicWiringAdmin run(GenericApplicationContext context){
        if(instanceMap.containsKey(context)){
            return instanceMap.get(context);
        } else{
            SpringDynamicWiringAdmin admin = new SpringDynamicWiringAdmin(context);
            instanceMap.put(context, admin);
            admin.hook();
            
            return admin;
        }
    }

    public static final boolean isBeanSatisfied(GenericApplicationContext context, Class<?>... clazzes){
        if(clazzes == null || clazzes.length == 0){
            return true;
        }

        if(instanceMap.containsKey(context)){
            List<Class<?>> notSatisfiedBeanTypes = instanceMap.get(context).getNotSatisfiedBeanTypes();
            return Stream.of(clazzes)
                .noneMatch(clazz -> notSatisfiedBeanTypes.contains(clazz));
        } else{
            return true;
        }
    }

    private SpringDynamicWiringAdmin(GenericApplicationContext context){
        this.context = context;
        this.registredBeans = Collections.synchronizedSet(new HashSet<>());
        this.dynamicContexts = Collections.synchronizedList(new ArrayList<>());
        this.status = false;
    }

    public List<Class<?>> getNotSatisfiedBeanTypes(){
        return dynamicContexts.stream()
            .map(beanContext -> beanContext.getClazz())
            .collect(Collectors.toList());
    }

    public void hook(){
        if(status){
            return;
        }
        this.status = true;

        registredBeans.addAll(
            Stream.of(context.getBeanDefinitionNames())
                .map(name -> context.getType(name))
                .collect(Collectors.toSet())
        );

        dynamicContexts.addAll(
            Stream.of(context.getBeanDefinitionNames())
                .map(name -> new BeanDynamicContext(name, context.getType(name)))
                .collect(Collectors.toSet())
        );

        context.addApplicationListener(new ApplicationListener<ApplicationEvent>(){
            public void onApplicationEvent(ApplicationEvent event) {
                if(event instanceof ApplicationStartedEvent){
                    for(BeanDynamicContext beanContext : dynamicContexts){
                        beanContext.setBean(context.getBean(beanContext.getName()));
                        if(beanContext.isCompleted(registredBeans)){
                            beanContext.finish();
                        }
                    }
                    dynamicContexts.removeIf(beanContext -> beanContext.isCompleted(registredBeans));

                    context.getBeanFactory().addBeanPostProcessor(new BeanPostProcessor(){
                        @Override
                        public Object postProcessAfterInitialization(Object bean, String beanName)
                                throws BeansException {
                            if(dynamicContexts.isEmpty()){
                                return null;
                            }

                            if(!registredBeans.contains(bean.getClass())){
                                registredBeans.add(bean.getClass());
                                for(BeanDynamicContext beanContext : dynamicContexts){
                                    beanContext.wire(bean.getClass(), bean);
                                }
                                for(BeanDynamicContext beanContext : dynamicContexts){
                                    if(beanContext.isCompleted(registredBeans)){
                                        beanContext.finish();
                                    }
                                }
                                dynamicContexts.removeIf(beanContext -> beanContext.isCompleted(registredBeans));
                            }
                            return null;
                        }
                    });
                }
            };
        });
    }

    private static class BeanDynamicContext{
        private final String name;
        private final Class<?> clazz;
        private Object bean;

        private final List<Field> wireFields;
        private final List<Method> wireMethods;
        private Method postDynamicWireMethod;

        public String getName(){
            return name;
        }

        public Class<?> getClazz() {
            return clazz;
        }

        public void setBean(Object bean){
            if(this.bean != null){
                return;
            }
            this.bean = bean;
        }

        public void wire(Class<?> clazz, Object dynamicBean){
            for(Field field : wireFields){
                if(field.getType().equals(clazz)){
                    try{
                        field.setAccessible(true);
                        field.set(bean, dynamicBean);
                        LOGGER.info("Bean " + clazz.toString() + " : dynamically wired to field of bean " + name);
                    } catch(Exception e){
                    }
                }
            }

            for(Method method : wireMethods){
                if(method.getParameterTypes()[0].equals(clazz)){
                    try{
                        method.setAccessible(true);
                        method.invoke(bean, dynamicBean);
                        LOGGER.info("Bean " + clazz.toString() + " : dynamically wired to method of bean " + name);
                    } catch(Exception e){
                    }
                }
            }
        }

        public void finish(){
            if(postDynamicWireMethod != null){
                try{
                    postDynamicWireMethod.setAccessible(true);
                    postDynamicWireMethod.invoke(bean);
                    this.postDynamicWireMethod = null;
                    LOGGER.info("For Bean " + name + " all dynamic wires finished!");
                } catch(Exception e){
                }
            }
        }

        public boolean isCompleted(Set<Class<?>> beansInScope){
            boolean isFieldsWired = wireFields.stream()
                .map(field -> field.getType())
                .noneMatch(clazz -> !beansInScope.contains(clazz));

            boolean isMethodsWired = wireMethods.stream()
                .map(method -> method.getParameterTypes()[0])
                .noneMatch(clazz -> !beansInScope.contains(clazz));

            return isMethodsWired && isFieldsWired;
        }

        public BeanDynamicContext(String name, Class<?> clazz){
            this.name = name;
            this.clazz = clazz;
            
            this.wireFields = Stream.of(clazz.getDeclaredFields())
                .filter(field -> field.getAnnotation(Autowired.class) != null)
                .filter(field -> !field.getAnnotation(Autowired.class).required())
                .collect(Collectors.toList());
            
            this.wireMethods = Stream.of(clazz.getDeclaredMethods())
                .filter(method -> method.getParameterTypes() != null  && method.getParameterTypes().length == 1)
                .filter(method -> method.getAnnotation(Autowired.class) != null)
                .filter(method -> !method.getAnnotation(Autowired.class).required())
                .collect(Collectors.toList());

            List<Method> postMethods = Stream.of(clazz.getDeclaredMethods())
                .filter(method -> method.getParameterTypes() == null  || method.getParameterTypes().length == 0)
                .filter(method -> method.getAnnotation(PostDynamicWire.class) != null)
                .collect(Collectors.toList());
                
            if(postMethods.size() == 1){
                this.postDynamicWireMethod = postMethods.get(0);
            }
        }
    }
}