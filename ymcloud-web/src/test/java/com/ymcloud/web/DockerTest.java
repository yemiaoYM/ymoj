package com.ymcloud.web;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.ymcloud.common.utils.FileUtils;
import com.ymcloud.judge.docker.container.CodeExecContainer;
import com.ymcloud.judge.docker.factory.CodeExecContainerFactory;
import com.ymcloud.judge.bo.CodeRunResult;
import com.ymcloud.judge.sandbox.SandBox;
import com.ymcloud.judge.sandbox.SandBoxFactory;
import com.ymcloud.pojo.bo.ProblemCaseInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @PackageName: com.ymcloud.web
 * @ClassName: DockerTest
 * @Author: Yemiao
 * @CreateTime: 2025-08-27  13:53
 * @Description: docker测试
 */
@SpringBootTest
public class DockerTest {

    @Autowired
    private DockerClient dockerClient;

    @Test
    public void testCreateContainer() {
        String hostWorkingDir="E:/Java/ymcloud/files/tmp/submission_10011";
        String containerWorkingDir="/app";

        long memoryLimitKb = 128*1024L;
        String imageName="oj-cpp-env";

        CodeExecContainerFactory factory=new CodeExecContainerFactory(
                dockerClient,
                hostWorkingDir,
                containerWorkingDir,
                memoryLimitKb,
                imageName);
        CodeExecContainer container = factory.createDockerContainer();

        String containerId = container.getContainerId();
        System.out.println(containerId);

        container.start();

        SandBoxFactory sandBoxFactory = new SandBoxFactory("C++");
        SandBox sandBox = sandBoxFactory.createSandBox(container);

        sandBox.compile(container);

        String in=" ";
        CodeRunResult execute = sandBox.execute(container, in+"\n", 1000L);

        System.out.println(execute);

//        int countFile = FileUtils.countFile(hostTestCaseDir);
//        System.out.println(countFile);
//
//        for (int i = 1; i <= countFile; i++) {
//            String inputFile = hostTestCaseDir+"/"+i+".in";
//            try{
//                String in=FileUtils.readFileString(inputFile);
//                System.out.println("[第"+i+"个]测试样例开始评测...");
//                CodeRunResult execute = sandBox.execute(container, in);
//                System.out.println("[第"+i+"个]测试样例的结果为："+execute);
//            } catch (Exception e){
//                System.out.println("评测出现问题"+e);
//            }
//        }

//        container.remove(true);
    }

    @Test
    public void testInspectContainer() {
        String containerId="0df2634c851580937c88035960b49657c4d61a523ab1accf2155a9423c940ac6";
        InspectContainerResponse exec = dockerClient.inspectContainerCmd(containerId).exec();
        InspectContainerResponse.ContainerState state = exec.getState();
        System.out.println(state);
    }

    @Test
    public void testFilePath() {
        String hostWorkingDir="E:/Java/ymcloud/files/tmp/submission_10020";
        String absolutePath = FileUtils.getAbsolutePath(hostWorkingDir);

        System.out.println(absolutePath);
    }

    @Test
    public void testCodeExec() {
        File inputFile = new File("E:/Java/ymcloud/files/private/problem/problem_7/in/5.in");
        File outputFile = new File("E:/Java/ymcloud/files/tmp/submission_10075/5.out");


        String execId = dockerClient.execCreateCmd("e143cfe4c50cd41017669e338d328b92046c0365d59e45f97ef46f3574521191")
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withAttachStdin(true)
                .withCmd(new String[]{"./main"})
                .exec().getId();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(inputFile);
             OutputStream out = new FileOutputStream(outputFile)) {

            dockerClient.execStartCmd(execId)
                    .withStdIn(in)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            try {
                                if (frame.getStreamType() == StreamType.STDOUT) {
                                    out.write(frame.getPayload());
                                } else if (frame.getStreamType() == StreamType.STDERR) {
                                    err.write(frame.getPayload());
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }).awaitCompletion(10, TimeUnit.SECONDS);
        }catch (Exception e){
            e.printStackTrace();
        }

        System.out.println(err);
    }


    @Test
    public void parseTimeAndMemory(){
        String str= """
                1 2
                3
                __TIME__:1.64 3116""";
        System.out.println(str);

        String timeInfo = StrUtil.subAfter(str, "__TIME__:", true);
        List<String> strings = StrUtil.splitTrim(timeInfo, " ");

        System.out.println(strings);
    }

    @Test
    public void caseToJson(){
        String baseDir="E:/Java/ymcloud/files/private/problem/";
        for(int k=1;k<=2;k++){
            String dir=baseDir+k;
            int countFile = FileUtils.countFile(dir + "/in");

            List<ProblemCaseInfo> list=new ArrayList<>();
            for(int i=1;i<=countFile;i++){
                ProblemCaseInfo caseInfo = new ProblemCaseInfo(i,"/in/"+i+".in","/out/"+i+".out");
                list.add(caseInfo);
            }

            String jsonString = JSON.toJSONString(list);
            System.out.println(jsonString);


            FileUtil.writeUtf8String(jsonString,dir+"/info.json");
        }
    }

}
