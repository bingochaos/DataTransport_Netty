import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import io.netty.util.CharsetUtil;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Created by bingoc on 16/4/21.
 */
public class DataClientInboundHandler extends SimpleChannelInboundHandler<HttpObject> {

    private boolean readingChunks = false;
    private File downloadFile = null;
    private FileOutputStream fOutputStream = null;

    private Object waitObject = new Object();
    private StringBuffer resultBuffer = new StringBuffer();

    private int succCode = 200;

    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg)
            throws Exception {

        System.out.println(msg.toString());
        if (msg instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) msg;

            succCode = response.getStatus().code();

            if (succCode == 200) {
                setDownLoadFile(response.headers().get("name"));
                readingChunks = true;
            }
        }

        if (msg instanceof HttpContent) {
            HttpContent chunk = (HttpContent) msg;
            if (chunk instanceof LastHttpContent) {
                readingChunks = false;
            }

            ByteBuf buffer = chunk.content();
            byte[] dst = new byte[buffer.readableBytes()];
            if(succCode == 200) {
                while(buffer.isReadable()) {
                    buffer.readBytes(dst);
                    fOutputStream.write(dst);
                }

                if (null != fOutputStream) {
                    fOutputStream.flush();
                }
            }
        }

        if (!readingChunks) {
            if (null != fOutputStream) {
                fOutputStream.flush();
                fOutputStream.close();

                downloadFile = null;
                fOutputStream = null;

                resultBuffer.append(succCode);
            }
//            ctx.channel().close();
        }
    }

    private void setDownLoadFile(String filename) throws Exception {
        if(null == fOutputStream) {
            downloadFile = new File(DataClient.ziped_logpath+filename);
            if(!downloadFile.exists()) {
                downloadFile.createNewFile();
            }
            fOutputStream = new FileOutputStream(downloadFile);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        waitObject = null;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            throws Exception {
        resultBuffer.setLength(0);
        resultBuffer.append(500);
        System.out.println("管道异常：" + cause.getMessage());
        cause.printStackTrace();
        ctx.channel().close();
    }
}
