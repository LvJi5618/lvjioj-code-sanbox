package com.lvji.lvjiojcodesandbox;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.lvji.lvjiojcodesandbox.model.ExecuteCodeRequest;
import com.lvji.lvjiojcodesandbox.model.ExecuteCodeResponse;
import com.lvji.lvjiojcodesandbox.model.JudgeInfo;
import com.lvji.lvjiojcodesandbox.model.ProcessExecuteMsg;
import com.lvji.lvjiojcodesandbox.utils.ProcessUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Docker实现代码沙箱
 */
public class JavaDockerCodeSandBox implements CodeSandBox {

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final Boolean FIRST_INIT = true;

    public static void main(String[] args) {
        JavaDockerCodeSandBox javaNativeCodeSandBox = new JavaDockerCodeSandBox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        String code = ResourceUtil.readStr("testcode/simpleComputeByArgs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        List<String> inputList = Arrays.asList("1 2","3 4");
        executeCodeRequest.setInputList(inputList);
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandBox.executeCode(executeCodeRequest);
        System.out.println("executeCodeResponse = " + executeCodeResponse);
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {

        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        List<String> inputList = executeCodeRequest.getInputList();

        /**
         * 1.把用户的代码保存为 Main.java 源文件
         */
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        // 判断全局代码目录是否存在,不存在则创建
        if(!FileUtil.exist(globalCodePathName)){
            FileUtil.mkdir(globalCodePathName);
        }
        // 用户代码隔离
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        System.out.println("userCodeFile = " + userCodeFile);
        /**
         * 2.编译 Main.java 源文件，生成 Main.class 字节码文件
         */
        // 获取 DockerClient,DockerClient 用来操作docker
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        /**
         * 编译用户代码
         */
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ProcessExecuteMsg processExecuteMsg = ProcessUtils.runProcessAndGetMsg(compileProcess, "编译");
            System.out.println(processExecuteMsg);
        } catch (Exception e) {
            return getErrorResponse(e);
        }
        /**
         * 3.创建容器，并将编译好的 Main.clss 文件上传到容器环境中
         */
        /**
         * 第一次执行时,拉取编译和运行用户代码所需的 jdk 镜像
         */
        String jdkImage = "openjdk:8-alpine";
        if (FIRST_INIT){
            PullImageCmd pullJdkImageCmd = dockerClient.pullImageCmd(jdkImage);
            PullImageResultCallback pullJdkImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载 openjdk:8-alpine 镜像:" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullJdkImageCmd
                        .exec(pullJdkImageResultCallback)
                        .awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("拉取 openjdk:8-alpine 镜像异常");
                return getErrorResponse(e);
            }
        }
        System.out.println("下载 openjdk:8-alpine 镜像完成！");
        /**
         * 创建容器
         */
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(jdkImage);
        // docker 容器配置
        HostConfig dockerHostConfig = new HostConfig();
        // 设置用户提交的代码的最大内存为 100 MB
        dockerHostConfig.withMemory(100 * 1000 * 1000L);
        dockerHostConfig.withMemorySwap(0L);
        dockerHostConfig.withCpuCount(1L);
        //dockerHostConfig.withSecurityOpts(Arrays.asList("seccomp=安全管理配置字符串"));
        // 文件路径(Volume)映射,作用是将本地的文件同步到容器中,让容器访问
        dockerHostConfig.setBinds(new Bind(userCodeParentPath,new Volume("/app")));
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(dockerHostConfig)
                .withNetworkDisabled(true)
                .withReadonlyRootfs(true)
                // 将本地终端与 docker 容器关联,以便与 docker 容器交互
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withAttachStdout(true)
                // 创建一个交互终端
                .withTty(true)
                .exec();
        System.out.println("createContainerResponse:" + createContainerResponse);
        String containerId = createContainerResponse.getId();

        /**
         * 4. 启动容器，执行用户代码，得到输出结果
         */
        // 启动容器
        StartContainerCmd startContainerCmd = dockerClient.startContainerCmd(containerId);
        startContainerCmd.exec();
        // 执行 java -cp /app Main 1 2 命令,运行用户代码,并得到执行结果
        List<ProcessExecuteMsg> processExecuteMsgList = new ArrayList<>();
        final String[] message = {null};
        final String[] errorMessage = {null};
        long time = 0L;
        final boolean[] timeout = {true};
        final long[] maxMemory = {0L};
        for (String inputArgs : inputList) {
            StopWatch stopWatch = new StopWatch();
            String[] inputArgsArray = inputArgs.split(" ");
            String[] runCmdArray = ArrayUtil.append(new String[]{"java","-cp","/app","Main"},inputArgsArray);
            ExecCreateCmdResponse createCmdResponse = dockerClient
                    // 操作已启动的 docker 容器（与 docker 容器交互）
                    .execCreateCmd(containerId)
                    .withCmd(runCmdArray)
                    .withAttachStdin(true)
                    .withAttachStderr(true)
                    .withAttachStdout(true)
                    .exec();
            System.out.println("创建执行命令:" + createCmdResponse);

            ProcessExecuteMsg processExecuteMsg = new ProcessExecuteMsg();
            String execId = createCmdResponse.getId();
            // 容器异步执行命令的回调函数
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                // 获取执行信息
                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        errorMessage[0] = new String(frame.getPayload());
                        System.out.println("错误执行信息:" + errorMessage[0]);
                    } else {
                        message[0] = new String(frame.getPayload());
                        System.out.println("用户代码正常执行信息:" + message[0]);
                    }
                    super.onNext(frame);
                }

                // 判断执行是否超时
                @Override
                public void onComplete() {
                    //如果执行完成,则表示没有超时
                    timeout[0] = false;
                    super.onComplete();
                }
            };
            // 获取程序执行占用的内存
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> statisticsResultCallback = new ResultCallback<Statistics>() {
                @Override
                public void onNext(Statistics statistics) {
                    System.out.println("内存占用:" + statistics.getMemoryStats().getUsage());
                    maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage(), maxMemory[0]);
                }

                @Override
                public void close() throws IOException {

                }

                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }
            };
            statsCmd.exec(statisticsResultCallback);
            try {
                // 统计执行时间
                stopWatch.start();
                dockerClient
                        // 操作已启动的 docker 容器（与 docker 容器交互）
                        .execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion();
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();
                //因为内存大小无时无刻都在变化。如果不关闭,会一直输出内存大小,直到程序结束
                statsCmd.close();
            } catch (InterruptedException e) {
                System.out.println("用户代码执行异常!");
                return getErrorResponse(e);
            }
            processExecuteMsg.setMessage(message[0]);
            processExecuteMsg.setErrorMessage(errorMessage[0]);
            processExecuteMsg.setMemory(maxMemory[0]);
            processExecuteMsg.setTime(time);
            processExecuteMsgList.add(processExecuteMsg);
        }
        /**
         * 5. 收集整理输出结果
         */
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        long maxTime = 0;
        for (ProcessExecuteMsg processExecuteMsg : processExecuteMsgList) {
            String errorMessageAuc = processExecuteMsg.getErrorMessage();
            // 用户提交代码中存在错误,运行失败,收集错误信息
            if(StrUtil.isNotBlank(errorMessageAuc)){
                executeCodeResponse.setMessage(errorMessageAuc);
                executeCodeResponse.setStatus(3);
                break;
            }
            outputList.add(processExecuteMsg.getMessage());
            // 取用时最大值
            Long timeAuc = processExecuteMsg.getTime();
            if(timeAuc != null){
                maxTime  = Math.max(timeAuc,maxTime);
            }
        }
        // 用户代码通过了所有测试用例,正常完成
        if(outputList.size() == processExecuteMsgList.size()){
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setMessage("代码沙箱成功！");
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        judgeInfo.setMemory(maxMemory[0]);
        executeCodeResponse.setJudgeInfo(judgeInfo);
        /**
         * 6. 文件清理，释放空间
         */
        if(userCodeFile.getParentFile() != null){
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
        }
        return executeCodeResponse;
    }

    /**
     * 获取错误响应
     * @param e
     * @return
     */
    public ExecuteCodeResponse getErrorResponse(Throwable e){
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setMessage(e.getMessage());
        // 代码沙箱错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        executeCodeResponse.setOutputList(new ArrayList<>());
        return executeCodeResponse;
    }
}
