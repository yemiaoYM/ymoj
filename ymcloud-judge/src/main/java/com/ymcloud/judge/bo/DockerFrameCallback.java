package com.ymcloud.judge.bo;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.ymcloud.common.exception.judge.SandBoxException;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @PackageName: com.ymcloud.judge.domain
 * @ClassName: DockerFrameCallback
 * @Author: Yemiao
 * @CreateTime: 2025-08-29  14:34
 * @Description: docker容器执行反馈处理类
 */
public class DockerFrameCallback extends ResultCallback.Adapter<Frame>{

    private final OutputStream stdout;
    private final OutputStream stderr;

    public DockerFrameCallback(OutputStream stdout, OutputStream stderr) {
        this.stdout = stdout;
        this.stderr = stderr;
    }

    @Override
    public void onNext(Frame frame) {
        try {
            if (frame.getStreamType() == StreamType.STDOUT) {
                stdout.write(frame.getPayload());
            } else if (frame.getStreamType() == StreamType.STDERR) {
                stderr.write(frame.getPayload());
            }
        } catch (IOException e) {
            throw new SandBoxException("容器流发生错误", e);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        throw new SandBoxException("容器内部发生错误", throwable);
    }

}
