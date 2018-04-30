package de.consol.labs.h2c.examples.client.jetty;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.api.Stream;
import org.eclipse.jetty.http2.api.server.ServerSessionListener;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.http.HttpClientTransportOverHTTP2;
import org.eclipse.jetty.http2.frames.DataFrame;
import org.eclipse.jetty.http2.frames.HeadersFrame;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.FuturePromise;
import org.eclipse.jetty.util.Jetty;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;

// Copied from the JavaDoc for org.eclipse.jetty.http2.client.HTTP2Client
public class JettyClientExample {

    public static void main(String[] args) throws Exception {

        if(args.length <2) {
            System.err.println("Usage:java -jar jetty-client.jar host port [alpn]");
            return;
        }
        String protocal = "HTTP://";
        String host = args[0];
        int port = Integer.valueOf(args[1]);
        boolean alpn = args.length >= 3 && args[2].equals("alpn");


        // Create and start HTTP2Client.
        HTTP2Client client = new HTTP2Client();
        HttpClientTransportOverHTTP2 transport = new HttpClientTransportOverHTTP2(client);
        SslContextFactory sslContextFactory = null;
        transport.setUseALPN(alpn);
        if (alpn) {
            protocal = "HTTPS://";
            sslContextFactory = new SslContextFactory(true);
            client.addBean(sslContextFactory);
        }
        client.start();

        // Connect to host.
        System.out.println("Connect to:"+protocal+host+":"+port);
        FuturePromise<Session> sessionPromise = new FuturePromise<>();
        client.connect(sslContextFactory, new InetSocketAddress(host, port), new ServerSessionListener.Adapter(), sessionPromise);

        // Obtain the client Session object.
        Session session = sessionPromise.get(5, TimeUnit.SECONDS);

        // Prepare the HTTP request headers.
        HttpFields requestFields = new HttpFields();
        requestFields.put("User-Agent", client.getClass().getName() + "/" + Jetty.VERSION);
        // Prepare the HTTP request object.
        MetaData.Request request = new MetaData.Request("GET", new HttpURI(protocal + host + ":" + port + "/"), HttpVersion.HTTP_2, requestFields);
        // Create the HTTP/2 HEADERS frame representing the HTTP request.
        HeadersFrame headersFrame = new HeadersFrame(request, null, true);
        final Phaser phaser = new Phaser(2);

        // Prepare the listener to receive the HTTP response frames.
        Stream.Listener responseListener = new Stream.Listener.Adapter() {

            @Override
            public void onHeaders(Stream stream, HeadersFrame frame) {
                System.out.print("onHeader:");
                System.out.println(stream);
                if (frame.isEndStream()) phaser.arrive();
            }
            @Override
            public void onData(Stream stream, DataFrame frame, Callback callback) {
                System.out.print("onData:");
                System.out.println(stream);
                byte[] bytes = new byte[frame.getData().remaining()];
                frame.getData().get(bytes);
                System.out.println("data: " + new String(bytes));
                callback.succeeded();
                if (frame.isEndStream()) phaser.arrive();
            }
        };

        session.newStream(headersFrame, new FuturePromise<>(), responseListener);
//        session.newStream(headersFrame, new FuturePromise<>(), responseListener);
//        session.newStream(headersFrame, new FuturePromise<>(), responseListener);


        phaser.awaitAdvanceInterruptibly(phaser.arrive(), 5, TimeUnit.SECONDS);

        client.stop();
    }
}
