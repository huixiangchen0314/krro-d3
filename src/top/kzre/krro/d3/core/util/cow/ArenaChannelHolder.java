package top.kzre.krro.d3.core.util.cow;


import top.kzre.krro.core.util.BufferedChannel;
import top.kzre.krro.d3.core.util.ResourceUtils;
import top.kzre.krro.util.arena.*;

public final class ArenaChannelHolder
        <T extends Arena<V>, V extends ArenaView<V>>
        implements AutoCloseable{
    private final T arena;
    private final BufferedChannel<ArenaTemplate<T, V>> channel;
    public ArenaChannelHolder(T arena,
                              ArenaTemplate<T, V> arenaTemplate) {
        this.arena = arena;
        channel = new BufferedChannel<>(1, tpl->{});
        try {
            channel.offer(arenaTemplate);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public AutoCloseableSession<ArenaTemplate<T, V>> open() throws InterruptedException {
        return new ArenaSession(channel.get());
    }

    @Override
    public void close() {
        RuntimeException ex = ResourceUtils.closeDelayError(channel, arena);
        if (ex != null) {
            throw ex;
        }
    }

    private class ArenaSession implements AutoCloseableSession<ArenaTemplate<T, V>>{
        private final ArenaTemplate<T, V> tpl;

        private ArenaSession(ArenaTemplate<T, V> tpl) {
            this.tpl = tpl;
        }

        @Override
        public ArenaTemplate<T, V> get() {
            return tpl;
        }

        @Override
        public void close() throws Exception {
            channel.offer(tpl);
        }
    }
}