package com.alibaba.dubbo.performance.demo.agent.consumeragent;

import com.alibaba.dubbo.performance.demo.agent.protocal.MyHttpRequestDecoder;
import com.alibaba.dubbo.performance.demo.agent.protocal.MyHttpResponseEncoder;
import com.alibaba.dubbo.performance.demo.agent.provideragent.ProviderAgent;
import com.alibaba.dubbo.performance.demo.agent.registry.Endpoint;
import com.alibaba.dubbo.performance.demo.agent.registry.EtcdRegistry;
import com.alibaba.dubbo.performance.demo.agent.registry.IRegistry;
import com.alibaba.dubbo.performance.demo.agent.registry.IpHelper;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannelConfig;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ConsumerAgent {

    private static Log log = LogFactory.getLog(ConsumerAgent.class);
    private IRegistry registry = new EtcdRegistry(System.getProperty("etcd.url"));
    private Map<String,Endpoint> endpoints = null;

    private static TCPChannelGroup channelGroup=null;

    public static TCPChannelGroup getTCPChannelGroup(){return channelGroup;}

    public void start(int port) throws Exception {

        endpoints = registry.find("com.alibaba.dubbo.performance.demo.provider.IHelloService");


        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        //EventLoopGroup bossGroup = new EpollEventLoopGroup(1);

        EventLoopGroup workerGroup = new NioEventLoopGroup();
        //EventLoopGroup workerGroup = new EpollEventLoopGroup();

        //UDPChannelManager.initChannel(workerGroup);

       // channelGroup=new TCPChannelGroup(12,workerGroup,new Endpoint(IpHelper.getHostIp(),30000));

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
            //b.group(bossGroup, workerGroup).channel(EpollServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            // server端发送的是httpResponse，所以要使用HttpResponseEncoder进行编码

                            ch.pipeline().addLast("decoder",new MyHttpRequestDecoder());

                            // server端接收到的是httpRequest，所以要使用HttpRequestDecoder进行解码
                            ch.pipeline().addLast("encoder",new MyHttpResponseEncoder());
                            //fix me:设置的最大长度会不会影响性能
                           // ch.pipeline().addLast(new HttpObjectAggregator(2048));
                            ch.pipeline().addLast(new ConsumerMsgHandler(endpoints));
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG,2048)
                    .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY,true)
                    .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .childOption(ChannelOption.SO_REUSEADDR, Boolean.TRUE)
                    .childOption(ChannelOption.AUTO_CLOSE, Boolean.FALSE)
                    .childOption(ChannelOption.ALLOW_HALF_CLOSURE, Boolean.FALSE);
            ChannelFuture f = b.bind(port).sync();
            if(f.isSuccess()){
                log.info("ConsumerAgent start on "+port);
                f.channel().closeFuture().sync();
            }

        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception{
        ConsumerAgent consumerAgent= new ConsumerAgent();
        consumerAgent.start(20000);
    }
}
