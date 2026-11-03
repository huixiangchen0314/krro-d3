package top.kzre.krro.d3.core.util.cow;

import top.kzre.krro.core.util.BufferedChannel;
import top.kzre.krro.core.util.IChannel;

import java.util.function.Consumer;

public final class COWChannelHolder<T extends CopyOnWrite<T>> implements AutoCloseable {
    private final IChannel<T> channel;

    private COWChannelHolder(IChannel<T> channel) {
        this.channel = channel;
    }

    public static <T extends CopyOnWrite<T>> COWChannelHolder<T> create(T value) {
        if (value == null) {
            throw new NullPointerException("value is null");
        }
        BufferedChannel<T> chan = new BufferedChannel<>(1, (disposed) -> {
            try {
                disposed.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        try {
            // 第一发送不可能出错,运行时抛出，实际不会发生
            chan.offer(value);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return new COWChannelHolder<T>(chan);
    }

    /**
     * 读取资产库
     */
    public void read(Consumer<T> consumer) throws Exception {
        // 本方法不处理异常，抛出外部处理
        T sig = channel.get();
        if (sig == null) {
            // 读取时，项目被关闭
            throw new IllegalStateException("channel is closed");
        }
        T shared = sig.shared();
        if (!channel.offer(sig)) {
            shared.close();
            // 归还时候项目被关闭
            throw new IllegalStateException("channel is closed");
        }
        try{
            // 消费
            consumer.accept(shared);
        }finally {
            shared.close();
        }
    }

    public void write(Consumer<T> consumer) throws InterruptedException {
        // 本方法不处理异常，抛出外部处理
        T sig = channel.get();
        if (sig == null) {
            // 读取时，项目被关闭
            throw new IllegalStateException("channel is closed");
        }
        boolean returnSuccess;
        try{
            // 消费
            consumer.accept(sig);
        }finally {
            returnSuccess = channel.offer(sig);
        }

        if (!returnSuccess) {
            throw new IllegalStateException("channel is closed");
        }
    }

    @Override
    public void close() {
        // 关闭通道，通道里面的值也会被关闭
        channel.close();
    }
}
