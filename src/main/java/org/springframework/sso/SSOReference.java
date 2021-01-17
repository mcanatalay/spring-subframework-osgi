package org.springframework.sso;

public class SSOReference<E> {
    private final E obj;
    private final Class<E> clazz;
    private final String name;
    private final Integer type;

    public static <T> SSOReference<T> create(Integer type, Class<T> clazz, String name, Object obj){
        return new SSOReference<T>(type, clazz, name, (T) obj);
    }

    public static <T> SSOReference<T> cast(Class<T> clazz, SSOReference<?> reference){
        return (SSOReference<T>) reference; 
    }

    private SSOReference(Integer type, Class<E> clazz, String name, E obj){
        this.type = type;
        this.clazz = clazz;
        this.name = name;
        this.obj = obj;
    }

    public Class<E> getClazz() {
        return clazz;
    }
    
    public String getName() {
        return name;
    }

    public E getObj() {
        return obj;
    }

    public Integer getType() {
        return type;
    }

    public boolean isTransformed(){
        return type == SSOContext.TYPE_TRANSFORMED_SERVICE || type == SSOContext.TYPE_TRANSFORMED_BEAN;
    }

    public SSOReference<E> trasform(){
        if(type == SSOContext.TYPE_BEAN){
            return new SSOReference<E>(SSOContext.TYPE_TRANSFORMED_BEAN, clazz, name, obj);
        } else if(type == SSOContext.TYPE_SERVICE){
            return new SSOReference<E>(SSOContext.TYPE_TRANSFORMED_SERVICE, clazz, name, obj);
        }

        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof SSOReference)){
            return false;
        } else{
            SSOReference<?> ref = (SSOReference<?>) obj;
            return getType() == ref.type && getName().equals(ref.getName()); 
        }
    }
}
