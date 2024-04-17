package com.lvji.lvjiojcodesandbox.utils;

import com.lvji.lvjiojcodesandbox.model.ProcessExecuteMsg;
import org.springframework.util.StopWatch;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 进程工具类
 */
public class ProcessUtils {

    /**
     * 执行进程,并获取进程执行信息
     * @param runProcess
     * @param opName
     * @return
     */
    public static ProcessExecuteMsg runProcessAndGetMsg(Process runProcess, String opName) {
        ProcessExecuteMsg processExecuteMsg = new ProcessExecuteMsg();

        try {
            // 计算代码执行时间
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            // 等待程序执行，获取执行码
            int exitValue = runProcess.waitFor();
            processExecuteMsg.setExitValue(exitValue);
            // 代码正常执行退出
            if (exitValue == 0) {
                System.out.println(opName + "成功");
                /**
                 * 分批获取进程的正常输出
                 */
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                StringBuilder compileOutputStringBuilder = new StringBuilder();
                // 逐行读取
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    compileOutputStringBuilder.append(compileOutputLine).append("\n");
                }
                processExecuteMsg.setMessage(compileOutputStringBuilder.toString());
            } else {
                //代码异常执行退出
                System.out.println(opName + "失败,错误码：" + exitValue);
                /**
                 * 分批获取进程的正常输出,即 System.out.println(opName + "失败,错误码：" + exitValue)
                 */
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                StringBuilder compileOutputStringBuilder = new StringBuilder();
                // 逐行读取
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    compileOutputStringBuilder.append(compileOutputLine).append("\n");
                }
                processExecuteMsg.setMessage(compileOutputStringBuilder.toString());
                /**
                 * 分批获取进程的错误输出
                 */
                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(runProcess.getErrorStream()));
                StringBuilder errorCompileOutputStringBuilder = new StringBuilder();
                // 逐行读取
                String errorCompileOutputLine;
                while ((errorCompileOutputLine = errorBufferedReader.readLine()) != null) {
                    errorCompileOutputStringBuilder.append(errorCompileOutputLine).append("\n");
                }
                processExecuteMsg.setErrorMessage(errorCompileOutputStringBuilder.toString());
            }
            stopWatch.stop();
            processExecuteMsg.setTime(stopWatch.getLastTaskTimeMillis());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return processExecuteMsg;
    }
}
