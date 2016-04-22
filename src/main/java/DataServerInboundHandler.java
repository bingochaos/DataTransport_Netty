
import com.sun.nio.zipfs.ZipFileSystem;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import io.netty.util.CharsetUtil;
import org.apache.log4j.Logger;

import javax.activation.MimetypesFileTypeMap;
import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Created by bingoc on 16/4/21.
 */
public class DataServerInboundHandler extends SimpleChannelInboundHandler<HttpObject> {

    private static final Logger logger = Logger.getLogger(DataServerInboundHandler.class.getName());

    private HttpRequest request;

    private boolean readingChunks;

    private HttpData partialContent;

    private String type = "message";

    private Channel channelSend;
    private ChannelHandlerContext channelHandlerContext;

    private final StringBuilder responseContent = new StringBuilder();

    private static final HttpDataFactory factory =
            new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE); // Disk if size exceed

    private HttpPostRequestDecoder decoder;

    static {
        DiskFileUpload.deleteOnExitTemporaryFile = true; // should delete file
        // on exit (in normal
        // exit)
        DiskFileUpload.baseDirectory = null; // system temp directory
        DiskAttribute.deleteOnExitTemporaryFile = true; // should delete file on
        // exit (in normal exit)
        DiskAttribute.baseDirectory = null; // system temp directory
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.out.println(cause.toString());
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println(ctx.toString());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, HttpObject httpObject) throws Exception {
        System.out.println(httpObject.toString());

        channelSend = channelHandlerContext.channel();
        this.channelHandlerContext = channelHandlerContext;

        if (httpObject instanceof HttpRequest) {
            request = (HttpRequest) httpObject;

           String uri = request.getUri();

            if (request.getMethod() == HttpMethod.POST) {
                if (decoder != null) {
                    decoder.cleanFiles();
                    decoder = null;
                }
                try {
                    decoder = new HttpPostRequestDecoder(factory, request);
                } catch (Exception e) {
                    e.printStackTrace();
                    channelHandlerContext.channel().close();
                    return;
                }
            }
        }

        if (decoder != null && httpObject instanceof HttpContent) {
            HttpContent chunk = (HttpContent) httpObject;

            try {
                decoder.offer(chunk);
            } catch (Exception e) {
                e.printStackTrace();
                channelHandlerContext.channel().close();
                return;
            }

            readHttpDataChunkByChunk();

            if (chunk instanceof LastHttpContent) {
                //reset();
                return;
            }
        }
    }

    private void readHttpDataChunkByChunk() throws IOException {
        try {
            while (decoder.hasNext()) {
                InterfaceHttpData data = decoder.next();
                if(data != null){
                    try {
                        writeHttpData(data);
                    } finally {
                        data.release();
                    }
                }
            }
        } catch (HttpPostRequestDecoder.EndOfDataDecoderException e) {
            System.out.println(e.toString());
        }
    }

    private void writeHttpData(InterfaceHttpData data) throws IOException {
        if(data.getHttpDataType() == InterfaceHttpData.HttpDataType.FileUpload) {
            FileUpload fileUpload = (FileUpload)data;
            if(fileUpload.isCompleted()) {
                StringBuffer filebuf = new StringBuffer();
                System.out.println(fileUpload.isInMemory());

                File result = new File(fileUpload.getFilename() + ".zip");
                ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(result));
                zipOutputStream.putNextEntry(new ZipEntry(fileUpload.getName()));
                zipOutputStream.write(fileUpload.get());
                zipOutputStream.finish();
                zipOutputStream.close();

                RandomAccessFile randomAccessFile = null;

                try{
                    randomAccessFile = new RandomAccessFile(result, "r");
                } catch (FileNotFoundException e) {
                    writeResponse(channelSend, HttpResponseStatus.INTERNAL_SERVER_ERROR, e.toString());
                }

                writeDownLoadResponse(channelHandlerContext, randomAccessFile, result);

            } else if(data.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute) {
                Attribute attribute = (Attribute) data;
            }
        }
    }

    private void writeDownLoadResponse(ChannelHandlerContext ctx, RandomAccessFile raf, File file) throws IOException {
        long fileLength = raf.length();

        //判断是否关闭请求响应连接
        boolean close = HttpHeaders.Values.CLOSE.equalsIgnoreCase(request.headers().get(HttpHeaders.Names.CONNECTION))
                || request.getProtocolVersion().equals(HttpVersion.HTTP_1_0)
                && !HttpHeaders.Values.KEEP_ALIVE.equalsIgnoreCase(request.headers().get(HttpHeaders.Names.CONNECTION));

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        HttpHeaders.setContentLength(response, fileLength);

        setContentHeader(response, file);

        if (!close) {
            response.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
            response.headers().set("name", file.getName());
        }

        ctx.write(response);
        System.out.println("读取大小："+fileLength);

        final FileRegion region = new DefaultFileRegion(raf.getChannel(), 0, fileLength);
        ChannelFuture writeFuture = ctx.write(region, ctx.newProgressivePromise());
        writeFuture.addListener(new ChannelProgressiveFutureListener() {
            public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
                if (total < 0) {
                    System.err.println(future.channel() + " Transfer progress: " + progress);
                } else {
                    System.err.println(future.channel() + " Transfer progress: " + progress + " / " + total);
                }
            }

            public void operationComplete(ChannelProgressiveFuture future) {
            }
        });

        ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        if(close) {
            raf.close();
            lastContentFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }

    public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
    public static final int HTTP_CACHE_SECONDS = 60;

    private static void setContentHeader(HttpResponse response, File file) {
        MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
        response.headers().set(HttpHeaders.Names.CONTENT_TYPE, mimeTypesMap.getContentType(file.getPath()));

        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        // Date header
        Calendar time = new GregorianCalendar();
        response.headers().set(HttpHeaders.Names.DATE, dateFormatter.format(time.getTime()));

        // Add cache headers
        time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
        response.headers().set(HttpHeaders.Names.EXPIRES, dateFormatter.format(time.getTime()));
        response.headers().set(HttpHeaders.Names.CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
        response.headers().set(HttpHeaders.Names.LAST_MODIFIED, dateFormatter.format(new Date(file.lastModified())));
    }

    private void writeResponse(Channel channel, HttpResponseStatus httpResponseStatus, String returnMsg) {
        String resultStr = "";
        if(httpResponseStatus.code() == HttpResponseStatus.OK.code()) {
            resultStr += "正常接收";
            if("message".equals(type)) {
                resultStr += "字符串。";
            } else if("upload".equals(type)) {
                resultStr += "上传文件。";
            } else if("download".equals(type)) {
                resultStr += "下载文件名。";
            }
        } else if(httpResponseStatus.code() == HttpResponseStatus.INTERNAL_SERVER_ERROR.code()) {
            resultStr += "接收";
            if("message".equals(type)) {
                resultStr += "字符串";
            } else if("upload".equals(type)) {
                resultStr += "上传文件";
            } else if("download".equals(type)) {
                resultStr += "下载文件名";
            }
            resultStr += "的过程中出现异常："+returnMsg;
        }

        ByteBuf buf = Unpooled.copiedBuffer(resultStr, CharsetUtil.UTF_8);

        //判断是否关闭请求响应连接
        boolean close = HttpHeaders.Values.CLOSE.equalsIgnoreCase(request.headers().get(HttpHeaders.Names.CONNECTION))
                || request.getProtocolVersion().equals(HttpVersion.HTTP_1_0)
                && !HttpHeaders.Values.KEEP_ALIVE.equalsIgnoreCase(request.headers().get(HttpHeaders.Names.CONNECTION));

        //构建请求响应对象
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, httpResponseStatus, buf);
        response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");

        if (!close) {
            //若该请求响应是最后的响应，则在响应头中没有必要添加'Content-Length'
            response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, buf.readableBytes());
        }

        //发送请求响应
        ChannelFuture future = channel.writeAndFlush(response);
        //发送请求响应操作结束后关闭连接
        if (close) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

}
