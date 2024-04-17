import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * 运行危险程序
 */
public class Main {
    public static void main(String[] args) throws Exception{
        String userDir = System.getProperty("user.dir");
        String filePath = userDir + File.separator + "src/main/resources/木马程序.bat";
        Process process = Runtime.getRuntime().exec(filePath);
        process.waitFor();
        // 分批获取进程的正常输出
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        // 逐行读取输出内容
        String outputLine;
        while ((outputLine = bufferedReader.readLine()) != null){
            System.out.println(outputLine);
        }
        System.out.println("异常程序执行成功");
    }
}