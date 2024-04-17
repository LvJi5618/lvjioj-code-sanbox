import java.util.ArrayList;
import java.util.List;

/**
 * 无限占用内存空间,导致资源浪费
 */
public class Main {
    public static void main(String[] args) {
        List<byte[]> bytes = new ArrayList<>();
        while (true){
            bytes.add(new byte[10000]);
        }
    }
}