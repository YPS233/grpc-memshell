package com.demo.shell.test;

import com.demo.shell.service.UserServiceImpl;
import io.grpc.*;

import java.io.IOException;



public class NsServer {
    public static void main(String[] args) throws IOException, InterruptedException{
        int port = 8082;
        Server server = ServerBuilder.forPort(port).addService(new UserServiceImpl()).build().start();
        System.out.println("server started, port : " + port);
        server.awaitTermination();
    }

}
