package com.lvji.lvjiojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import com.lvji.lvjiojcodesandbox.model.ExecuteCodeRequest;
import com.lvji.lvjiojcodesandbox.model.ExecuteCodeResponse;
import com.lvji.lvjiojcodesandbox.model.JudgeInfo;
import com.lvji.lvjiojcodesandbox.model.ProcessExecuteMsg;
import com.lvji.lvjiojcodesandbox.security.DefaultSecurityManager;
import com.lvji.lvjiojcodesandbox.security.DenySecurityManager;
import com.lvji.lvjiojcodesandbox.utils.ProcessUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Java原生实现代码沙箱
 */
public class JavaNativeCodeSandBox implements CodeSandBox {

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final String SECURITY_MANAGER_PATH = "D:\\intelliJ IDEA\\Program\\lvjioj-code-sandbox\\src\\main\\resources\\security";

    private static final String SECURITY_MANAGER_CLASS_NAME = "MySecurityManager";

    // 用户程序执行时间不得超过 5 s
    public static final long TIME_OUT = 5000L;

    //代码黑名单
    private static final List<String> blackList = Arrays.asList("Files","exec");

    // 字典树
    private static final WordTree WORD_TREE;

    static {
        // 初始化字典树
        WORD_TREE = new WordTree();
        WORD_TREE.addWords(blackList);
    }
    public static void main(String[] args) {
        JavaNativeCodeSandBox javaNativeCodeSandBox = new JavaNativeCodeSandBox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        //String code = ResourceUtil.readStr("testcode/simpleComputeByArgs/Main.java", StandardCharsets.UTF_8);
        String code = ResourceUtil.readStr("testcode/unsafe/RunFileError.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        List<String> inputList = Arrays.asList("1 2","3 4");
        executeCodeRequest.setInputList(inputList);
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandBox.executeCode(executeCodeRequest);
        System.out.println("executeCodeResponse = " + executeCodeResponse);
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        //System.setSecurityManager(new DenySecurityManager());

        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        List<String> inputList = executeCodeRequest.getInputList();

        /**
         * 校验用户代码中是否包含代码黑名单中的禁止词
         */
//        FoundWord foundWord = WORD_TREE.matchWord(code);
//        if(foundWord != null){
//            System.out.println("包含禁止词:" + foundWord.getFoundWord());
//            return null;
//        }

        /**
         * 1.把用户的代码保存为 .java 源文件
         */
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        // 判断全局代码目录是否存在,不存在创建
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
        String compileCmd = String.format("javac -encoding utf-8 \"%s\"", userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ProcessExecuteMsg processExecuteMsg = ProcessUtils.runProcessAndGetMsg(compileProcess, "编译");
            System.out.println(processExecuteMsg);
        } catch (Exception e) {
            return getErrorResponse(e);
        }
        /**
         * 3.执行用户提交的代码,获取执行结果
         */
        List<ProcessExecuteMsg> processExecuteMsgList = new ArrayList<>();
        for (String inputArgs : inputList) {
            //String runCmd = String.format("java -Dfile.encoding=UTF-8 -cp \"%s\" Main %s", userCodeParentPath, inputArgs);
            //String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp \"%s\" Main %s", userCodeParentPath, inputArgs);
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp \"%s\";\"%s\" -Djava.security.manager=%s Main %s", userCodeParentPath, SECURITY_MANAGER_PATH,SECURITY_MANAGER_CLASS_NAME,inputArgs);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                // 超时控制
                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                        System.out.println("用户程序执行超时,已强制中断！");
                        runProcess.destroy();
                    }catch (InterruptedException e){
                        throw new RuntimeException(e);
                    }
                }).start();
                ProcessExecuteMsg processExecuteMsg = ProcessUtils.runProcessAndGetMsg(runProcess, "运行");
                System.out.println(processExecuteMsg);
                // 获取每条输入对应的执行结果
                processExecuteMsgList.add(processExecuteMsg);
            }catch (Exception e){
                return getErrorResponse(e);
            }
        }
        /**
         * 4.收集整理输出结果
         */
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        // 从每条输入对应的输出中,取用时最大的那条输出,便于判断代码执行是否超时
        long maxTime = 0;
        for (ProcessExecuteMsg processExecuteMsg : processExecuteMsgList) {
            String errorMessage = processExecuteMsg.getErrorMessage();
            if(StrUtil.isNotBlank(errorMessage)){
                executeCodeResponse.setMessage(errorMessage);
                // 用户提交的代码在执行过程中存在错误
                executeCodeResponse.setStatus(3);
                break;
            }
            outputList.add(processExecuteMsg.getMessage());
            Long time = processExecuteMsg.getTime();
            if(time != null){
                maxTime = Math.max(time,maxTime);
            }
        }
        // 正常运行完成
        if(outputList.size() == processExecuteMsgList.size()){
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        /**
         * todo 要借助第三方库来获取代码运行时的内存占用,此处不做实现
         * judgeInfo.setMemory();
         */
        executeCodeResponse.setJudgeInfo(judgeInfo);
        /**
         * 5.文件清理，释放空间
         */
        if(userCodeFile.getParentFile() != null){
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除:" + (del ? "成功" : "失败"));
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
