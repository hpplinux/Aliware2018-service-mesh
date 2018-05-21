package com.alibaba.dubbo.performance.demo.agent.consumeragent.tcp;

import com.alibaba.dubbo.performance.demo.agent.registry.Endpoint;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;


public class TcpChannel {
    private Channel channel=null;

    public TcpChannel(EventLoopGroup group, Endpoint endpoint) throws Exception{

        Class<? extends SocketChannel> channelClass=Epoll.isAvailable() ? EpollSocketChannel.class:NioSocketChannel.class;
        channel = new Bootstrap()
                .group(group)
                .channel(channelClass)
                //.channel(EpollSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, Boolean.TRUE)
                .option(ChannelOption.TCP_NODELAY, Boolean.TRUE)
                .option(EpollChannelOption.TCP_QUICKACK,Boolean.TRUE)
                //.option(ChannelOption.SO_SNDBUF,1024*1024)
                //.option(ChannelOption.SO_RCVBUF,100*1024)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        ChannelPipeline pipeline = socketChannel.pipeline();
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,0,1,0,1));
                        pipeline.addLast(new LengthFieldPrepender(2,false));
                        pipeline.addLast(new TcpProviderAgentMsgHandler());
                    }
                })
                .connect(endpoint.getHost(), endpoint.getPort()).sync().channel();
        System.out.println("connect to"+endpoint.getHost()+":"+endpoint.getPort());
    }

    public Channel getChannel() throws Exception {
        return channel;
    }
}
