package com.chryl.tcp.server;

import com.alibaba.fastjson.JSON;
import com.chryl.server.Const;
import com.chryl.server.ShowcaseServerConfig;
import com.chryl.server.ShowcaseWsMsgHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.tio.core.ChannelContext;
import org.tio.core.GroupContext;
import org.tio.core.Tio;
import org.tio.core.exception.AioDecodeException;
import org.tio.core.intf.Packet;
import org.tio.http.common.HttpRequest;
import org.tio.http.common.HttpResponse;
import org.tio.server.intf.ServerAioHandler;
import org.tio.websocket.common.WsRequest;
import org.tio.websocket.common.WsResponse;
import org.tio.websocket.common.WsSessionContext;
import org.tio.websocket.server.handler.IWsMsgHandler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 处理执行：为服务端的handler，
 * 功能：主要是接受client的数据，并解码
 * 发送服务端的数据，并操作db
 * <p>
 * channelContext是本服务的 channelContext,不是对方发过来的
 * <p>
 * Created By Chr on 2019/4/16.
 */
@Slf4j
public class ServerHandler implements ServerAioHandler, IWsMsgHandler {
    @Autowired
    private ShowcaseWsMsgHandler showcaseWsMsgHandler;

    /**
     * 解码：
     * 根据ByteBuffer解码成业务需要的Packet对象.
     * 如果收到的数据不全，导致解码失败，请返回null，在下次消息来时框架层会自动续上前面的收到的数据
     *
     * @param buffer         参与本次希望解码的ByteBuffer
     * @param limit          ByteBuffer的limit
     * @param position       ByteBuffer的position，不一定是0哦
     * @param readableLength ByteBuffer参与本次解码的有效数据（= limit - position）
     * @param channelContext
     * @return
     * @throws AioDecodeException
     */
    @Override
    public Packet decode(ByteBuffer buffer, int limit, int position, int readableLength, ChannelContext channelContext) throws AioDecodeException {

        //

        //#####################################//
        //提醒：buffer的开始位置并不一定是0，应用需要从buffer.position()开始读取数据
        //收到的数据组不了业务包，则返回null以告诉框架数据不够
        //拿到client的packet,对比收到的消息头格式
        /**
         * 这里修改了
         */
        /*if (readableLength < RequestPacket.HANDER_LENGTH) {
            return null;
        }*/
        if (readableLength < 0) {
            return null;
        }
        //读取消息体的长度
        //格式正确，操作消息体
        //缓冲区当前位置的 int 值
        /**
         * 这里修改了
         */
//        int bodyLength = buffer.getInt();
        /**
         * 测试
         */
//        int bodyInt = buffer.getInt();

//        buffer.flip();//4

        int bodyLength = buffer.remaining();//17
        //消息体格式不正确//数据不正确，则抛出AioDecodeException异常
        if (bodyLength < 0) {
            throw new AioDecodeException//
                    ("bodyLength [" + bodyLength + "] is not right, remote:" + channelContext.getClientNode());
        }
        //计算本次需要的数据长度
        //本次接收的数据需要的 缓冲区的长度(总长度=消息头长度+消息体长度)
        int neededLength = RequestPacket.HANDER_LENGTH + bodyLength;
        //验证 本地收到的 数据是否足够组包：防止发生 半包 和 粘包
        //收到的数据是否足够组包
        int isDataEnough = readableLength - neededLength;
        //不够消息体长度，无法用buffer组合 // 不够消息体长度(剩下的buffe组不了消息体)
        /**
         * 这里修改了
         */
        if (isDataEnough + 4 < 0) {
            return null;
        } else {//组包成功

            RequestPacket requestPacket = new RequestPacket();
            if (bodyLength > 0) {
                //本次接受的 位置的int值
                byte[] bytes = new byte[bodyLength];
//                byte[] bytes = new byte[Integer.MAX_VALUE>>4];
                buffer.get(bytes);
                requestPacket.setBody(bytes);
            }
            return requestPacket;
        }

    }


    /**
     * 编码
     *
     * @param packet
     * @param groupContext
     * @param channelContext
     * @return
     */
    @Override
    public ByteBuffer encode(Packet packet, GroupContext groupContext, ChannelContext channelContext) {
        RequestPacket requestPacket = (RequestPacket) packet;

        //要发送的数据对象，以字节数组byte[]放在Packet的body中
        byte[] body = requestPacket.getBody();
        int bodyLength = 0;
        if (body != null) {
            bodyLength = body.length;
        }
        //byteBuffer的总长度=消息头长度（headLen）+消息体长度（bodyLen）
        int byteBufferLen = RequestPacket.HANDER_LENGTH + bodyLength;
        //初始化新的ByteBuffer
        ByteBuffer buffer = ByteBuffer.allocate(byteBufferLen);
        //设置字节序：？？？？？？
        //新的字节顺序，要么是 BIG_ENDIAN，要么是 LITTLE_ENDIAN
//        buffer.order(groupContext.getByteOrder());
        buffer.order(ByteOrder.BIG_ENDIAN);
        //写入消息头
        buffer.putInt(bodyLength);
        //写入消息体
        if (body != null) {
            buffer.put(body);
        }
        return buffer;
    }

    /**
     * 数据处理:需要进行 一些校验,是否为该协议(16进制,crc校验)
     * <p>
     * 硬件发送给server的数据：为16进制，需要16进制解码
     * client发送给server的数据：目前为byte[]，不需要16进制解码，转为16进制接受给硬件即可
     * </P>
     *
     * @param packet
     * @param channelContext
     * @throws Exception
     */
    @Override
    public void handler(Packet packet, ChannelContext channelContext) throws Exception {

        //#################################################//
        //接受 client发送来的 数据
        RequestPacket requestPacket = (RequestPacket) packet;
        //ip+port
//        channelContext.getClientNode().getPort();
        //得到包装的数据
        byte[] body = requestPacket.getBody();
        //############################################
        //解码：设备发送过来的16进制
        String s1 = byte2Hex(body);
        System.out.println("（硬件发送的数据-16进制解码为）s1:" + s1);
        String s2 = BinaryToHexString(body);
        System.out.println("（硬件发送的数据-16进制解码为）s2:" + s2);
        //#####################################################
        //检验body
        if (body != null) {

            String s = new String(body, RequestPacket.CHARSET);

            try {
                TestBean testBean = JSON.parseObject(s, TestBean.class);
                System.err.println("FastJson：" + testBean.getComm());
            } catch (Exception e) {
                // e.printStackTrace();
            } finally {
                //######################################################
                //这是客户都按发送过来的
                System.err.println("数据长度是：" + s.length());
                System.err.println(" （未16进制解码）模拟服务端接收的命令数据是:" + s);
                //######################################################

                //服务端  回执 客户端
                RequestPacket reRequestPacket = new RequestPacket();

                //################################################
                reRequestPacket.setBody(s.getBytes());

//                Tio.bindUser(channelContext, "server");
//                Tio.sendToUser(channelContext.groupContext, "server", reRequestPacket);

                //客户都按主动发送
                Tio.bindUser(channelContext, "client-01");
                Tio.sendToUser(channelContext.groupContext, "client-01", requestPacket);


                //websocket
                String sbId = "";
                if (s1.contains("6B")) {
                    map.put(sbId, "1");
                }

            }
        }
    }

    //websocket
    public static Map<String, String> map = new ConcurrentHashMap<>();

    //=========================================================================//

    /**
     * 将byte[]转为16进制:不分割
     * FE0A4A4E3132715067734D34374550534976099CFE
     *
     * @param bytes
     * @return
     */
    public static String byte2Hex(byte[] bytes) {

        StringBuffer stringBuffer = new StringBuffer();
        String temp = null;

        for (int x = 0; x < bytes.length; x++) {
            temp = Integer.toHexString(bytes[x] & 0xFF);
            if (temp.length() == 1) {
                //1得到一位的进行补0操作
                stringBuffer.append("0");
            }
            stringBuffer.append(temp);
        }
        return stringBuffer.toString().toUpperCase();//转为大写
    }


    //=========================================================================//

    /**
     * 将byte[]转为16进制：分割
     * FE 0A 4A 4E 31 32 71 50 67 73 4D 34 37 45 50 53 49 76 09 9C FE
     *
     * @param bytes
     * @return
     */
    //将字节数组转换为16进制字符串
    public static String BinaryToHexString(byte[] bytes) {
        String hexStr = "0123456789ABCDEF";
        String result = "";
        String hex = "";
        for (byte b : bytes) {
            hex = String.valueOf(hexStr.charAt((b & 0xF0) >> 4));
            hex += String.valueOf(hexStr.charAt(b & 0x0F));
            result += hex + " ";//分隔符
        }
        return result;
    }

    //websocket@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
//    private static Logger log = LoggerFactory.getLogger(ShowcaseWsMsgHandler.class);
    public static final ServerHandler me = new ServerHandler();

    /**
     * 握手时走这个方法，业务可以在这里获取cookie，request参数等
     */
    @Override
    public HttpResponse handshake(HttpRequest request, HttpResponse httpResponse, ChannelContext channelContext) throws Exception {
        String clientip = request.getClientIp();
        String myname = request.getParam("name");
        Tio.bindUser(channelContext, myname);
//        channelContext.setUserid(myname);
        log.info("收到来自{}的ws握手包\r\n{}", clientip, request.toString());
        return httpResponse;
    }

    /**
     * @param httpRequest
     * @param httpResponse
     * @param channelContext
     * @throws Exception
     * @author tanyaowu
     */
    @Override
    public void onAfterHandshaked(HttpRequest httpRequest, HttpResponse httpResponse, ChannelContext channelContext) throws Exception {
        //绑定到群组，后面会有群发
        Tio.bindGroup(channelContext, Const.GROUP_ID);
        int count = Tio.getAllChannelContexts(channelContext.groupContext).getObj().size();
        String msg = "{name:'admin',message:'" + channelContext.userid + " 进来了，共【" + count + "】人在线" + "'}";
        //用tio-websocket，服务器发送到客户端的Packet都是WsResponse
        WsResponse wsResponse = WsResponse.fromText(msg, ShowcaseServerConfig.CHARSET);
        //群发
        Tio.sendToGroup(channelContext.groupContext, Const.GROUP_ID, wsResponse);
    }

    /**
     * 字节消息（binaryType = arraybuffer）过来后会走这个方法
     */
    @Override
    public Object onBytes(WsRequest wsRequest, byte[] bytes, ChannelContext channelContext) throws Exception {
        return null;
    }

    /**
     * 当客户端发close flag时，会走这个方法
     */
    @Override
    public Object onClose(WsRequest wsRequest, byte[] bytes, ChannelContext channelContext) throws Exception {
        Tio.remove(channelContext, "receive close flag");
        return null;
    }

    /*
     * 字符消息（binaryType = blob）过来后会走这个方法
     */
    @Override
    public Object onText(WsRequest wsRequest, String text, ChannelContext channelContext) throws Exception {
        WsSessionContext wsSessionContext = (WsSessionContext) channelContext.getAttribute();
        HttpRequest httpRequest = wsSessionContext.getHandshakeRequest();//获取websocket握手包
        if (log.isDebugEnabled()) {
            log.debug("握手包:{}", httpRequest);
        }
        log.info("收到ws消息:{}", text);
        if (Objects.equals("心跳内容", text)) {
            return null;
        }
        //channelContext.getToken()
        //String msg = channelContext.getClientNode().toString() + " 说：" + text;
        String msg = "{name:'" + channelContext.userid + "',message:'" + text + "'}";
        //用tio-websocket，服务器发送到客户端的Packet都是WsResponse
        WsResponse wsResponse = WsResponse.fromText(msg, ShowcaseServerConfig.CHARSET);
        //群发
        Tio.sendToGroup(channelContext.groupContext, Const.GROUP_ID, wsResponse);
        //返回值是要发送给客户端的内容，一般都是返回null

        return null;
    }
}