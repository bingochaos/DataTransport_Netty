import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.apache.log4j.Logger;

/**
 * Created by bingoc on 16/4/21.
 */
public class DataServer {

    private static Logger logger = Logger.getLogger(DataServer.class);

    private static final int PORT = 8080;

//    public void start(int port) throws Exception {
//        NioEventLoopGroup bossGroup = new NioEventLoopGroup();
//        NioEventLoopGroup workerGroup = new NioEventLoopGroup();
//        try {
//            ServerBootstrap serverBootstrap = new ServerBootstrap();
//            serverBootstrap.group(bossGroup, workerGroup)
//                    .channel(NioServerSocketChannel.class)
//                    .childHandler(new ChannelInitializer<SocketChannel>() {
//                        @Override
//                        protected void initChannel(SocketChannel socketChannel) throws Exception {
//                            socketChannel.pipeline().addLast(new HttpResponseEncoder());
//                            socketChannel.pipeline().addLast(new HttpRequestDecoder());
//
//                            socketChannel.pipeline().addLast(new DataServerInboundHandler());
//                        }
//                    }).option(ChannelOption.SO_BACKLOG, 128)
//                    .childOption(ChannelOption.SO_KEEPALIVE, true);
//
//            ChannelFuture channelFuture = serverBootstrap.bind(port).sync();
//
//            channelFuture.channel().closeFuture().sync();
//        } finally {
//            workerGroup.shutdownGracefully();
//            bossGroup.shutdownGracefully();
//        }
//    }

    public static void main(String args[]) throws Exception {
        NioEventLoopGroup bossGroup = new NioEventLoopGroup();
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup);
            b.channel(NioServerSocketChannel.class);
            b.handler(new LoggingHandler(LogLevel.INFO));
            b.option(ChannelOption.SO_BACKLOG, 128);
            b.childOption(ChannelOption.SO_KEEPALIVE, true);
            b.childHandler(new DataServerInitializer());

            ChannelFuture channelFuture = b.bind(PORT).sync();

            channelFuture.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
