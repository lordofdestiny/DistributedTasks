package rs.ac.bg.etf.kdp.utils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;

public class ClassMethodInvoker extends Thread {
    final Class<?> threadClass;
    private final Object instance;
    private final Method method;
    private final Object[] methodArgs;

    public ClassMethodInvoker(String className, Object[] ctorArgs,
                              String methodName, Object[] methodArgs)
            throws ReflectiveOperationException {
        super(className+"::"+methodName);
        threadClass = Class.forName(className);
        instance = getConstructor(ctorArgs);
        method = getMethod(methodName, methodArgs);
        this.methodArgs = methodArgs;
    }

    @Override
    public void run() {
        try {
            method.invoke(instance, methodArgs);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private Object getConstructor(Object[] ctorArgs)
            throws ReflectiveOperationException {
        final var ctorArgTypes = Arrays.stream(ctorArgs)
                .map(Object::getClass)
                .toArray(Class[]::new);
        return threadClass
                .getConstructor(ctorArgTypes)
                .newInstance(ctorArgs);
    }

    private Method getMethod(String methodName, Object[] methodArgs)
            throws ReflectiveOperationException {
        final var methodParamTypes = Stream.of(methodArgs)
                .map(Object::getClass).toArray(Class[]::new);
        return threadClass.getMethod(methodName, methodParamTypes);
    }
}
