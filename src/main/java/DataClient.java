
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.*;
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.multipart.*;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;


/**
 * Created by bingoc on 16/4/21.
 */
public class DataClient {

    static final String BASE_URL = System.getProperty("baseUrl", "http://127.0.0.1:8080/");
    static final String FILE = "src/main/resources/log/BJSY89142313.json";
    static final String logpath = "src/main/resources/log/";
    static final String ziped_logpath = "src/main/resources/ziped_log/";
    static HttpDataFactory dataFactory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);
    static ChannelFuture channelFuture;

//    public void connect(String host, int port) throws Exception {
//        NioEventLoopGroup workerGroup = new NioEventLoopGroup();
//
//        try {
//            Bootstrap bootstrap = new Bootstrap();
//            bootstrap.group(workerGroup)
//                    .channel(NioSocketChannel.class)
//                    .option(ChannelOption.SO_KEEPALIVE, true);
//            bootstrap.handler(new ChannelInitializer<SocketChannel>() {
//                @Override
//                protected void initChannel(SocketChannel socketChannel) throws Exception {
//                    socketChannel.pipeline().addLast(new HttpRequestEncoder());
//                    socketChannel.pipeline().addLast(new HttpResponseDecoder());
//                    socketChannel.pipeline().addLast(new DataClientInboundHandler());
//                }
//            });
//
//            ChannelFuture channelFuture = bootstrap.connect(host, port).sync();
//
//            URI uri = new URI("http://127.0.0.1:8080");
//            String msg = "Are you OK?";
//            DefaultFullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.toASCIIString(), Unpooled.wrappedBuffer(msg.getBytes("UTF-8")));
//
//            httpRequest.headers().set(HttpHeaders.Names.HOST, host);
//            httpRequest.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
//            httpRequest.headers().set(HttpHeaders.Names.CONTENT_LENGTH, httpRequest.content().readableBytes());
//
//            channelFuture.channel().write(httpRequest);
//            channelFuture.channel().flush();
//            channelFuture.channel().closeFuture().sync();
//        } finally {
//            workerGroup.shutdownGracefully();
//        }
//    }

    public static void main(String args[]) throws Exception {

        String postSimple, postFile, get;
        if(BASE_URL.endsWith("/")){
            postSimple = BASE_URL + "formpost";
            postFile = BASE_URL + "formpostmultipart";
            get = BASE_URL + "formget";
        } else {
            postSimple = BASE_URL + "/formpost";
            postFile = BASE_URL + "/formpostmultipart";
            get = BASE_URL + "/formget";
        }

        URI uriSimple = new URI(postSimple);
        String scheme = uriSimple.getScheme() == null? "http" : uriSimple.getScheme();
        String host = uriSimple.getHost() == null? "127.0.0.1" : uriSimple.getHost();
        int port = uriSimple.getPort();

        URI uriFile = new URI(postFile);

        NioEventLoopGroup workerGroup = new NioEventLoopGroup();

        DiskFileUpload.deleteOnExitTemporaryFile = true;
        DiskFileUpload.baseDirectory = null;
        DiskAttribute.deleteOnExitTemporaryFile = true;
        DiskAttribute.baseDirectory = null;

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workerGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new DataClientInitializer());

            channelFuture = bootstrap.connect(new InetSocketAddress("localhost",8080)).sync();



            HashSet<String> files = new HashSet<String>(Arrays.asList(new File(logpath).list()));
            HashSet<String> ziped_files = new HashSet<String>(Arrays.asList(new File(ziped_logpath).list()));

            for(String file: files)
            {
                if(!ziped_files.contains(file + ".zip")) {
                    DataClient dataClient = new DataClient();
                    dataClient.upLoad(new File(logpath + file));
                }
            }

            channelFuture.channel().close().sync();

            channelFuture.channel().closeFuture().sync();



        } finally {
           workerGroup.shutdownGracefully();
        }

    }

    private void upLoad(File file){

        if(file.isHidden() || !file.isFile()) {
            System.out.println(file.getName() + "不存在");
            return;
        }

        try {
            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, file.getName());

            HttpPostRequestEncoder bodyRequestEncoder = new HttpPostRequestEncoder(dataFactory, request, true);

            bodyRequestEncoder.addBodyAttribute("getform", "POST");
            bodyRequestEncoder.addBodyFileUpload("myfile", file, "application/x-zip-compressed", false);

            bodyRequestEncoder.finalizeRequest();

            Channel channel = channelFuture.channel();

            if(channel.isActive() && channel.isWritable()) {
                channel.writeAndFlush(request);

                if(bodyRequestEncoder.isChunked()){
                    channel.writeAndFlush(bodyRequestEncoder).awaitUninterruptibly();
                }

                bodyRequestEncoder.cleanFiles();
            }


        } catch (Exception e){
            e.printStackTrace();
        }


    }



//    private static List<Map.Entry<String, String>> formget(Bootstrap bootstrap, String host, int port, String get, URI uriSimple) throws Exception {
//
//        Channel channel = bootstrap.connect(host, port).sync().channel();
//
//        QueryStringEncoder encoder = new QueryStringEncoder(get);
//
//        encoder.addParam("getform", "GET");
//
//        URI uriGet = new URI(encoder.toString());
//        HttpRequest httpRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uriGet.toASCIIString());
//        HttpHeaders headers = httpRequest.headers();
//        headers.set(HttpHeaders.Names.HOST, host);
//        headers.set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
//        headers.set(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP + "," + HttpHeaders.Values.DEFLATE);
//
//        headers.set(HttpHeaders.Names.COOKIE, ClientCookieEncoder.STRICT.encode(new DefaultCookie("my-cookie", "foo"), new DefaultCookie("another-cookie", "bar")));
//
//        channel.writeAndFlush(httpRequest);
//
//        channel.closeFuture().sync();
//
//        return headers.entries();
//
//    }
//
//    private static List<InterfaceHttpData> formpost(Bootstrap bootstrap, String host, int port, URI uriSimple, File file, HttpDataFactory factory, List<Map.Entry<String, String>> headers) throws Exception{
//
//        ChannelFuture channelFuture = bootstrap.connect(new InetSocketAddress(host, port));
//
//        Channel channel = channelFuture.sync().channel();
//
//        HttpRequest httpRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uriSimple.toASCIIString());
//
//        HttpPostRequestEncoder bodyRequestEncoder = new HttpPostRequestEncoder(factory, httpRequest, false);//是否分段
//
//        for(Map.Entry<String, String> entry: headers){
//            httpRequest.headers().set(entry.getKey(), entry.getValue());
//        }
//
//        bodyRequestEncoder.addBodyAttribute("getform", "POST");
//        bodyRequestEncoder.addBodyAttribute("info", "first value");
//        bodyRequestEncoder.addBodyFileUpload("myfile", file, "application/x-zip-compressed", false);
//
//        httpRequest = bodyRequestEncoder.finalizeRequest();
//
//        List<InterfaceHttpData> bodylist = bodyRequestEncoder.getBodyListAttributes();
//
//        channel.write(httpRequest);
//
//        if(bodyRequestEncoder.isChunked()) {
//            channel.write(bodyRequestEncoder);
//        }
//        channel.flush();
//
//        channel.closeFuture().sync();
//        return bodylist;
//    }
//
//    private static void formpostmultipart(Bootstrap bootstrap, String host, int port, URI uriFile, HttpDataFactory dataFactory,
//                                          Iterable<Map.Entry<String, String>> headers, List<InterfaceHttpData> bodylist) throws Exception {
//        ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));
//
//        Channel channel = future.sync().channel();
//
//        HttpRequest httpRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uriFile.toASCIIString());
//
//        HttpPostRequestEncoder bodyRequestEncoder = new HttpPostRequestEncoder(dataFactory, httpRequest, true);
//
//        for(Map.Entry<String, String> entry : headers) {
//            httpRequest.headers().set(entry.getKey(), entry.getValue());
//        }
//
//        bodyRequestEncoder.setBodyHttpDatas(bodylist);
//
//        bodyRequestEncoder.finalizeRequest();
//
//        channel.write(httpRequest);
//
//        if(bodyRequestEncoder.isChunked()) {
//            channel.write(bodyRequestEncoder);
//        }
//        channel.flush();
//
//        bodyRequestEncoder.cleanFiles();
//
//        channel.closeFuture().sync();
//
//    }

}
