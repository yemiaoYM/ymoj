package com.ymcloud.judge;

import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.ymcloud.judge.bo.DockerFrameCallback;
import com.ymcloud.judge.exception.OutputLimitExceededException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerFrameCallbackTest {

    @Test
    void intentionalOutputAbortKeepsFirstFailureWhenTransportReportsClosedStream() throws Exception {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        DockerFrameCallback callback = new DockerFrameCallback(stdout, new ByteArrayOutputStream(), 4);
        callback.onNext(new Frame(StreamType.STDOUT, new byte[]{1, 2, 3}));
        callback.onNext(new Frame(StreamType.STDOUT, new byte[]{4, 5}));
        callback.onError(new java.io.IOException("Stream closed"));
        callback.onNext(new Frame(StreamType.STDOUT, new byte[]{6}));
        assertInstanceOf(OutputLimitExceededException.class, callback.getFailure());
        assertEquals(3, stdout.size());
        assertTrue(callback.awaitCompletion(100, java.util.concurrent.TimeUnit.MILLISECONDS));
    }
}
