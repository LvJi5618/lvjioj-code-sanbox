package com.lvji.lvjiojcodesandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.LogContainerResultCallback;

import java.util.List;

public class DockerDemo {
    public static void main(String[] args)  throws  InterruptedException{
        // 获取DockerClient
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
/*        PingCmd pingCmd = dockerClient.pingCmd();
        pingCmd.exec();*/
        /**
         * 1. 拉取镜像
         */
        String image = "nginx:latest";
        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
        PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
            @Override
            public void onNext(PullResponseItem item) {
                System.out.println("下载镜像:" + item.getStatus());
                super.onNext(item);
            }
        };
        // 因为拉取镜像可能会花费很长时间,所以拉取镜像要异步操作,这就需要一个回调函数 PullImageResultCallback
        // awaitCompletion 等待异步拉取镜像完成,异步变同步
        pullImageCmd.
                exec(pullImageResultCallback).
                awaitCompletion();
        System.out.println("拉取镜像" + image + "完成！");
        /**
         * 2. 创建容器
         */
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        CreateContainerResponse createContainerResponse = containerCmd.
                // 创建容器时附加参数
                withCmd("echo", "hello docker").
                exec();
        String containerId = createContainerResponse.getId();
        System.out.println("createContainerResponse:" + createContainerResponse);
        /**
         * 3. 查看容器状态
         */
        ListContainersCmd listContainersCmd = dockerClient.listContainersCmd();
        List<Container> containers = listContainersCmd.
                // 查看所有容器的运行记录,相当于 docket ps -a
                withShowAll(true).
                exec();
        for (Container container : containers) {
            System.out.println(container);
        }
        /**
         * 4. 启动容器
         */
        StartContainerCmd startContainerCmd = dockerClient.startContainerCmd(containerId);
        startContainerCmd.exec();
        /**
         * 5. 查看日志
         */
        LogContainerResultCallback logContainerResultCallback = new LogContainerResultCallback() {
            @Override
            public void onNext(Frame item) {
                System.out.println(item.getStreamType());
                System.out.println("日志:" + new String(item.getPayload()));
                super.onNext(item);
            }
        };
        dockerClient.logContainerCmd(containerId).
                // 开启标准错误输出流
                withStdErr(true).
                // 开启标准输出流
                withStdOut(true).
                // 异步执行输出日志操作
                exec(logContainerResultCallback).
                // 阻塞等待日志输出
                awaitCompletion();
        /**
         *6. 删除容器
         */
        dockerClient.removeContainerCmd(containerId).
                withForce(true).
                exec();
        /**
         * 7. 删除镜像
         */
        dockerClient.removeImageCmd(image).
                withForce(true).
                exec();
    }
}
