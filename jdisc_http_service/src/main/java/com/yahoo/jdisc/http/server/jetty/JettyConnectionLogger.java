// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.container.logging.ConnectionLog;
import com.yahoo.container.logging.ConnectionLogEntry;
import com.yahoo.io.HexDump;
import com.yahoo.jdisc.http.ServerConfig;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.SocketChannelEndPoint;
import org.eclipse.jetty.io.ssl.SslConnection;
import org.eclipse.jetty.io.ssl.SslHandshakeListener;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.server.ProxyConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.component.AbstractLifeCycle;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.StandardConstants;
import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Jetty integration for jdisc connection log ({@link ConnectionLog}).
 *
 * @author bjorncs
 */
class JettyConnectionLogger extends AbstractLifeCycle implements Connection.Listener, HttpChannel.Listener, SslHandshakeListener {

    static final String CONNECTION_ID_REQUEST_ATTRIBUTE = "jdisc.request.connection.id";

    private static final Logger log = Logger.getLogger(JettyConnectionLogger.class.getName());

    private final ConcurrentMap<SocketChannelEndPoint, ConnectionInfo> connectionInfo = new ConcurrentHashMap<>();
    private final ConcurrentMap<SSLEngine, ConnectionInfo> sslToConnectionInfo = new ConcurrentHashMap<>();

    private final boolean enabled;
    private final ConnectionLog connectionLog;

    JettyConnectionLogger(ServerConfig.ConnectionLog config, ConnectionLog connectionLog) {
        this.enabled = config.enabled();
        this.connectionLog = connectionLog;
        log.log(Level.FINE, () -> "Jetty connection logger is " + (config.enabled() ? "enabled" : "disabled"));
    }

    //
    // AbstractLifeCycle methods start
    //
    @Override
    protected void doStop() {
        handleListenerInvocation("AbstractLifeCycle", "doStop", "", List.of(), () -> {
            log.log(Level.FINE, () -> "Jetty connection logger is stopped");
        });
    }

    @Override
    protected void doStart() {
        handleListenerInvocation("AbstractLifeCycle", "doStart", "", List.of(), () -> {
            log.log(Level.FINE, () -> "Jetty connection logger is started");
        });
    }
    //
    // AbstractLifeCycle methods stop
    //

    //
    // Connection.Listener methods start
    //
    @Override
    public void onOpened(Connection connection) {
        handleListenerInvocation("Connection.Listener", "onOpened", "%h", List.of(connection), () -> {
            SocketChannelEndPoint endpoint = findUnderlyingSocketEndpoint(connection.getEndPoint());
            ConnectionInfo info = connectionInfo.get(endpoint);
            if (info == null) {
                info = ConnectionInfo.from(endpoint);
                connectionInfo.put(endpoint, info);
            }
            // TODO Store details on proxy-protocol
            if (connection instanceof SslConnection) {
                SSLEngine sslEngine = ((SslConnection) connection).getSSLEngine();
                sslToConnectionInfo.put(sslEngine, info);
            }
        });
    }

    @Override
    public void onClosed(Connection connection) {
        handleListenerInvocation("Connection.Listener", "onClosed", "%h", List.of(connection), () -> {
            SocketChannelEndPoint endpoint = findUnderlyingSocketEndpoint(connection.getEndPoint());
            ConnectionInfo info = connectionInfo.get(endpoint);
            if (info == null) return; // Closed connection already handled
            if (connection instanceof HttpConnection) {
                info.setHttpBytes(connection.getBytesIn(), connection.getBytesOut());
            }
            if (!endpoint.isOpen()) {
                connectionLog.log(info.toLogEntry());
                connectionInfo.remove(endpoint);
            }
        });
    }
    //
    // Connection.Listener methods end
    //

    //
    // HttpChannel.Listener methods start
    //
    @Override
    public void onRequestBegin(Request request) {
        handleListenerInvocation("HttpChannel.Listener", "onRequestBegin", "%h", List.of(request), () -> {
            SocketChannelEndPoint endpoint = findUnderlyingSocketEndpoint(request.getHttpChannel().getEndPoint());
            ConnectionInfo info = Objects.requireNonNull(connectionInfo.get(endpoint));
            info.incrementRequests();
            request.setAttribute(CONNECTION_ID_REQUEST_ATTRIBUTE, info.uuid());
        });
    }

    @Override
    public void onResponseBegin(Request request) {
        handleListenerInvocation("HttpChannel.Listener", "onResponseBegin", "%h", List.of(request), () -> {
            SocketChannelEndPoint endpoint = findUnderlyingSocketEndpoint(request.getHttpChannel().getEndPoint());
            ConnectionInfo info = Objects.requireNonNull(connectionInfo.get(endpoint));
            info.incrementResponses();
        });
    }
    //
    // HttpChannel.Listener methods end
    //

    //
    // SslHandshakeListener methods start
    //
    @Override
    public void handshakeSucceeded(Event event) {
        SSLEngine sslEngine = event.getSSLEngine();
        handleListenerInvocation("SslHandshakeListener", "handshakeSucceeded", "sslEngine=%h", List.of(sslEngine), () -> {
            ConnectionInfo info = sslToConnectionInfo.remove(sslEngine);
            info.setSslSessionDetails(sslEngine.getSession());
        });
    }

    @Override
    public void handshakeFailed(Event event, Throwable failure) {
        SSLEngine sslEngine = event.getSSLEngine();
        handleListenerInvocation("SslHandshakeListener", "handshakeFailed", "sslEngine=%h,failure=%s", List.of(sslEngine, failure), () -> {
            log.log(Level.FINE, failure, failure::toString);
            ConnectionInfo info = sslToConnectionInfo.remove(sslEngine);
            info.setSslHandshakeFailure((SSLHandshakeException)failure);
        });
    }
    //
    // SslHandshakeListener methods end
    //

    private void handleListenerInvocation(
            String listenerType, String methodName, String methodArgumentsFormat, List<Object> methodArguments, ListenerHandler handler) {
        if (!enabled) return;
        try {
            log.log(Level.FINE, () -> String.format(listenerType + "." + methodName + "(" + methodArgumentsFormat + ")", methodArguments.toArray()));
            handler.run();
        } catch (Exception e) {
            log.log(Level.WARNING, String.format("Exception in %s.%s listener: %s", listenerType, methodName, e.getMessage()), e);
        }
    }

    /**
     * Protocol layers are connected through each {@link Connection}'s {@link EndPoint} reference.
     * This methods iterates through the endpoints recursively to find the underlying socket endpoint.
     */
    private static SocketChannelEndPoint findUnderlyingSocketEndpoint(EndPoint endpoint) {
        if (endpoint instanceof SocketChannelEndPoint) {
            return (SocketChannelEndPoint) endpoint;
        } else if (endpoint instanceof SslConnection.DecryptedEndPoint) {
            var decryptedEndpoint = (SslConnection.DecryptedEndPoint) endpoint;
            return findUnderlyingSocketEndpoint(decryptedEndpoint.getSslConnection().getEndPoint());
        } else if (endpoint instanceof ProxyConnectionFactory.ProxyEndPoint) {
            var proxyEndpoint = (ProxyConnectionFactory.ProxyEndPoint) endpoint;
            return findUnderlyingSocketEndpoint(proxyEndpoint.unwrap());
        } else {
            throw new IllegalArgumentException("Unknown connection endpoint type: " + endpoint.getClass().getName());
        }
    }

    @FunctionalInterface private interface ListenerHandler { void run() throws Exception; }

    // TODO Include connection duration or timestamp closed
    private static class ConnectionInfo {
        private final UUID uuid;
        private final long createdAt;
        private final InetSocketAddress localAddress;
        private final InetSocketAddress peerAddress;

        private long httpBytesReceived = -1;
        private long httpBytesSent = -1;
        private long requests = -1;
        private long responses = -1;
        private byte[] sslSessionId;
        private String sslProtocol;
        private String sslCipherSuite;
        private String sslPeerSubject;
        private Date sslPeerNotBefore;
        private Date sslPeerNotAfter;
        private List<SNIServerName> sslSniServerNames;
        private String sslHandshakeFailureException;
        private String sslHandshakeFailureMessage;
        private String sslHandshakeFailureType;

        private ConnectionInfo(UUID uuid, long createdAt, InetSocketAddress localAddress, InetSocketAddress peerAddress) {
            this.uuid = uuid;
            this.createdAt = createdAt;
            this.localAddress = localAddress;
            this.peerAddress = peerAddress;
        }

        static ConnectionInfo from(SocketChannelEndPoint endpoint) {
            return new ConnectionInfo(
                    UUID.randomUUID(),
                    endpoint.getCreatedTimeStamp(),
                    endpoint.getLocalAddress(),
                    endpoint.getRemoteAddress());
        }

        synchronized UUID uuid() { return uuid; }

        synchronized ConnectionInfo setHttpBytes(long received, long sent) {
            this.httpBytesReceived = received;
            this.httpBytesSent = sent;
            return this;
        }

        synchronized ConnectionInfo incrementRequests() { ++this.requests; return this; }

        synchronized ConnectionInfo incrementResponses() { ++this.responses; return this; }

        synchronized ConnectionInfo setSslSessionDetails(SSLSession session) {
            this.sslCipherSuite = session.getCipherSuite();
            this.sslProtocol = session.getProtocol();
            this.sslSessionId = session.getId();
            if (session instanceof ExtendedSSLSession) {
                ExtendedSSLSession extendedSession = (ExtendedSSLSession) session;
                this.sslSniServerNames = extendedSession.getRequestedServerNames();
            }
            try {
                this.sslPeerSubject = session.getPeerPrincipal().getName();
                X509Certificate peerCertificate = (X509Certificate) session.getPeerCertificates()[0];
                this.sslPeerNotBefore = peerCertificate.getNotBefore();
                this.sslPeerNotAfter = peerCertificate.getNotAfter();
            } catch (SSLPeerUnverifiedException e) {
                // Throw if peer is not authenticated (e.g when client auth is disabled)
                // JSSE provides no means of checking for client authentication without catching this exception
            }
            return this;
        }

        synchronized ConnectionInfo setSslHandshakeFailure(SSLHandshakeException exception) {
            this.sslHandshakeFailureException = exception.getClass().getName();
            this.sslHandshakeFailureMessage = exception.getMessage();
            this.sslHandshakeFailureType = SslHandshakeFailure.fromSslHandshakeException(exception)
                    .map(SslHandshakeFailure::failureType)
                    .orElse("UNKNOWN");
            return this;
        }

        synchronized ConnectionLogEntry toLogEntry() {
            ConnectionLogEntry.Builder builder = ConnectionLogEntry.builder(uuid, Instant.ofEpochMilli(createdAt));
            if (httpBytesReceived >= 0) {
                builder.withHttpBytesReceived(httpBytesReceived);
            }
            if (httpBytesSent >= 0) {
                builder.withHttpBytesSent(httpBytesSent);
            }
            if (requests >= 0) {
                builder.withRequests(requests);
            }
            if (responses >= 0) {
                builder.withResponses(responses);
            }
            if (peerAddress != null) {
                builder.withPeerAddress(peerAddress.getHostString())
                        .withPeerPort(peerAddress.getPort());
            }
            if (localAddress != null) {
                builder.withLocalAddress(localAddress.getHostString())
                        .withLocalPort(localAddress.getPort());
            }
            if (sslProtocol != null && sslCipherSuite != null && sslSessionId != null) {
                builder.withSslProtocol(sslProtocol)
                        .withSslCipherSuite(sslCipherSuite)
                        .withSslSessionId(HexDump.toHexString(sslSessionId));
            }
            if (sslSniServerNames != null) {
                sslSniServerNames.stream()
                        .filter(name -> name instanceof SNIHostName && name.getType() == StandardConstants.SNI_HOST_NAME)
                        .map(name -> ((SNIHostName) name).getAsciiName())
                        .findAny()
                        .ifPresent(builder::withSslSniServerName);
            }
            if (sslPeerSubject != null && sslPeerNotAfter != null && sslPeerNotBefore != null) {
                builder.withSslPeerSubject(sslPeerSubject)
                        .withSslPeerNotAfter(sslPeerNotAfter.toInstant())
                        .withSslPeerNotBefore(sslPeerNotBefore.toInstant());
            }
            if (sslHandshakeFailureException != null && sslHandshakeFailureMessage != null && sslHandshakeFailureType != null) {
                builder.withSslHandshakeFailureException(sslHandshakeFailureException)
                        .withSslHandshakeFailureMessage(sslHandshakeFailureMessage)
                        .withSslHandshakeFailureType(sslHandshakeFailureType);
            }
            return builder.build();
        }

    }
}
