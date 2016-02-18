package jp.shinido.qiita.simple_nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;

public class Main {
    public static final int SIZE = 20;
    public static final int PORT = 12345;

    public static void main(String[] args) throws IOException{
        Thread th = new Thread(()->{
            try {
                server();
            } catch(IOException e){
                e.printStackTrace();
            }
        });
        th.start();

        client();
    }
    
    public static void server() throws IOException {
        Selector selector = Selector.open();
        ServerSocketChannel serverCh = ServerSocketChannel.open();
        serverCh.configureBlocking(false);
        serverCh.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        serverCh.bind(new InetSocketAddress("localhost", PORT));
        serverCh.register(selector, SelectionKey.OP_ACCEPT);

        loop:while(true){
            selector.select(); // blocking
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            while(keys.hasNext()){
                SelectionKey key = keys.next();
                keys.remove(); // DON'T FORGET!

                if(key.isAcceptable()){
                    // new connection
                    System.out.println("SERVER: New Connection");
                    SocketChannel ch = serverCh.accept();
                    ch.configureBlocking(false);
                    ByteBuffer buffer = ByteBuffer.allocate(SIZE);
                    ch.register(selector, SelectionKey.OP_READ, buffer);
                    continue;
                }

                if(key.isReadable()){
                    SocketChannel ch = (SocketChannel) key.channel();
                    ByteBuffer buffer = (ByteBuffer) key.attachment();
                    int readByte = ch.read(buffer);
                    System.out.println("SERVER: Read "+readByte);
                    if(readByte < 0){
                        System.out.println("SERVER: Closed");
                        try {
                            ch.close();
                        } catch(IOException e){
                        }
                        break loop;
                    }

                    if(!buffer.hasRemaining()){
                        buffer.flip();
                        ch.register(selector, SelectionKey.OP_WRITE, buffer);
                    }
                }

                if(key.isWritable()){
                    SocketChannel ch = (SocketChannel) key.channel();
                    ByteBuffer buffer = (ByteBuffer) key.attachment();
                    int writeByte = ch.write(buffer);
                    System.out.println("SERVER: Write "+writeByte);
                    if(!buffer.hasRemaining()){
                        buffer.clear();
                        ch.register(selector, SelectionKey.OP_READ, buffer);
                    }
                }
            }
        }
        selector.close();
    }
    
    
    public static void client() throws IOException {
        Selector selector = Selector.open();
        SocketChannel socketCh = SocketChannel.open();
        socketCh.configureBlocking(false);
        socketCh.connect(new InetSocketAddress("localhost", PORT));
        socketCh.register(selector, SelectionKey.OP_CONNECT);
        ByteBuffer buffer = ByteBuffer.allocate(SIZE);
        Random rand = new Random();
        while(buffer.hasRemaining()){
            buffer.put((byte) (rand.nextInt() % 128));
        }
        buffer.flip();

        loop:while(true){
            selector.select();
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            while(keys.hasNext()){
                SelectionKey key = keys.next();
                keys.remove();

                if(key.isConnectable()){
                    if(!socketCh.finishConnect()){
                        throw new IOException("Connect Fail");
                    }
                    System.out.println("CLIENT: Connected");
                    socketCh.register(selector, SelectionKey.OP_WRITE);
                }

                if(key.isWritable()){
                    socketCh.write(buffer);
                    if(!buffer.hasRemaining()){
                        System.out.println("CLIENT: Write "+ Arrays.toString(buffer.array()));
                        ByteBuffer readBuf = ByteBuffer.allocate(SIZE);
                        socketCh.register(selector, SelectionKey.OP_READ, readBuf);
                    }
                }

                if(key.isReadable()){
                    ByteBuffer readBuf = (ByteBuffer) key.attachment();
                    socketCh.read(readBuf);
                    if(!readBuf.hasRemaining()){
                        System.out.println("CLIENT: Read  "+ Arrays.toString(readBuf.array()));
                    }
                    socketCh.close();
                    break loop;
                }
            }
        }
        selector.close();
        System.out.println("CLIENT: Done");
    }
}
