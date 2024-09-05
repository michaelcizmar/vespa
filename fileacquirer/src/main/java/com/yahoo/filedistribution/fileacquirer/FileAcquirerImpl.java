// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.filedistribution.fileacquirer;

import com.yahoo.config.FileReference;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.filedistribution.fileacquirer.FileAcquirerImpl.FileDistributionErrorCode.fileReferenceNotFound;
import static com.yahoo.jrt.ErrorCode.ABORT;
import static com.yahoo.jrt.ErrorCode.CONNECTION;
import static com.yahoo.jrt.ErrorCode.GENERAL_ERROR;
import static com.yahoo.jrt.ErrorCode.OVERLOAD;
import static com.yahoo.jrt.ErrorCode.TIMEOUT;
import static com.yahoo.net.HostName.getLocalhost;

/**
 * Retrieves the path to a file or directory on the local file system
 * that has been transferred with the vespa file distribution
 * mechanism.
 *
 * Intended to be the only real implementation of FileAcquirer.
 *
 * @author Tony Vaagenes
 */
class FileAcquirerImpl implements FileAcquirer {

    static final class FileDistributionErrorCode {

        public static final int baseErrorCode = 0x10000;
        public static final int baseFileProviderErrorCode = baseErrorCode + 0x1000;
        public static final int fileReferenceNotFound = baseFileProviderErrorCode;

    }

    private static final Logger log = Logger.getLogger(FileAcquirerImpl.class.getName());

    private final Supervisor supervisor = new Supervisor(new Transport("fileaquirer"));

    private class Connection {

        private static final int configProxyRpcPort = 19090;

        private final Lock targetLock = new ReentrantLock();
        private final Spec spec = new Spec(getLocalhost(), configProxyRpcPort);
        private long pauseTime = 0; //milliseconds

        private Target target;
        private long nextLogTime = 0;
        private long logCount = 0;

        private void connect(Timer timer) throws InterruptedException {
            while (timer.isTimeLeft()) {
                pause();
                target = supervisor.connect(spec);
                // ping to check if connection is working
                Request request = new Request("frt.rpc.ping");
                target.invokeSync(request, Duration.ofSeconds(5));
                if (request.isError()) {
                    logWarning();
                    target.close();
                } else {
                    log.log(Level.FINE, () -> "Successfully connected to '" + spec + "', this = " + System.identityHashCode(this));
                    pauseTime = 0;
                    logCount = 0;
                    return;
                }
            }
        }

        private void pause() throws InterruptedException {
            if (pauseTime > 0) {
                Thread.sleep(pauseTime);
                pauseTime = Math.min((long)(pauseTime*1.5), TimeUnit.MINUTES.toMillis(1));
            } else {
                pauseTime = 500;
            }
        }

        private void logWarning() {
            if (logCount == 0 || System.currentTimeMillis() > nextLogTime ) {
                log.warning("Could not connect to the config proxy '" + spec + "'" + " - " + this + "@" + System.identityHashCode(this));

                nextLogTime = System.currentTimeMillis() +
                        Math.min(TimeUnit.DAYS.toMillis(1),
                                TimeUnit.SECONDS.toMillis(30) * (++logCount));
                log.info("Next log time = " + nextLogTime + ", current = " + System.currentTimeMillis());
            }
        }

        public Target getTarget(Timer timer) throws InterruptedException {
            TimeUnit unit = TimeUnit.MILLISECONDS;

            targetLock.tryLock(timer.timeLeft(unit) , unit );
            try {
                if (target == null || !target.isValid())
                    connect(timer);
                return target;
            } finally {
                targetLock.unlock();
            }
        }
    }

    private final Connection connection = new Connection();

    private boolean temporaryError(int errorCode) {
        return switch (errorCode) {
            case ABORT, CONNECTION, GENERAL_ERROR, OVERLOAD, TIMEOUT, fileReferenceNotFound -> true;
            default -> false;
        };
    }

    public void shutdown() {
        supervisor.transport().shutdown().join();
    }

    /**
     * Returns the path to a file or directory corresponding to the
     * given file reference.  File references are produced by the
     * config system.
     *
     * @throws TimeoutException if the file or directory could not be retrieved in time.
     */
    public File waitFor(FileReference fileReference, long timeout, TimeUnit timeUnit) throws InterruptedException {
        Timer timer = new Timer(timeout, timeUnit);
        do {
            Target target = connection.getTarget(timer);
            if (target == null)
                break;

            Request request = new Request("waitFor");
            request.parameters().add(new StringValue(fileReference.value()));

            double rpcTimeout = Math.min(timer.timeLeft(TimeUnit.SECONDS), 60.0);
            log.log(Level.FINE, () -> "InvokeSync waitFor " + fileReference + " with " + rpcTimeout + " seconds timeout");
            target.invokeSync(request, rpcTimeout);

            if (request.checkReturnTypes("s")) {
                return new File(request.returnValues().get(0).asString());
            } else if (!request.isError()) {
                throw new RuntimeException("Invalid response: " + request.returnValues());
            } else if (temporaryError(request.errorCode())) {
                log.log(Level.INFO, "Retrying waitFor for " + fileReference + ": " + request.errorCode() + " -- " + request.errorMessage());
                Thread.sleep(1000);
            } else {
                throw new RuntimeException("Wait for " + fileReference + " failed: " + request.errorMessage() + " (" + request.errorCode() + ")");
            }
        } while ( timer.isTimeLeft() );

        throw new TimeoutException("Timed out waiting for " + fileReference + " after " + timeout + " " + timeUnit.name().toLowerCase());
    }

}
