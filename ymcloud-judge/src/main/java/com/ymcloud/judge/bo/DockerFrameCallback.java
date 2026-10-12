package com.ymcloud.judge.bo;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.ymcloud.judge.exception.OutputLimitExceededException;
import com.ymcloud.judge.exception.SandBoxException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 收集有界的 stdout/stderr，并保留首次异常原因。
 */
public class DockerFrameCallback extends ResultCallback.Adapter<Frame> {

    private final OutputStream stdout;
    private final OutputStream stderr;
    private final long maxBytes;
    private long bytes;
    private final AtomicReference<RuntimeException> failure = new AtomicReference<>();

    public DockerFrameCallback(OutputStream stdout, OutputStream stderr) {
        this(stdout, stderr, 1024 * 1024);
    }

    public DockerFrameCallback(OutputStream stdout, OutputStream stderr, long maxBytes) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.maxBytes = maxBytes;
    }

    @Override
    public void onNext(Frame frame) {
        if (failure.get() != null) {
            return;
        }
        try {
            bytes += frame.getPayload().length;
            if (bytes > maxBytes) {
                failure.compareAndSet(null, new OutputLimitExceededException());
                super.onComplete();
                return;
            }
            if (frame.getStreamType() == StreamType.STDOUT) {
                stdout.write(frame.getPayload());
            } else if (frame.getStreamType() == StreamType.STDERR) {
                stderr.write(frame.getPayload());
            }
        } catch (IOException e) {
            onError(e);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        if (failure.compareAndSet(null, new SandBoxException("容器内部发生错误", throwable))) {
            super.onError(throwable);
        } else {
            // 主动截断流会引发 StreamClosedException，必须保留原始输出超限原因。
            super.onComplete();
        }
    }

    public RuntimeException getFailure() {
        return failure.get();
    }

}
