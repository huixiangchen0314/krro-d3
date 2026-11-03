package top.kzre.krro.d3.core.util.cow;

public interface AutoCloseableSession<V>  extends AutoCloseable {
    V get();

}
