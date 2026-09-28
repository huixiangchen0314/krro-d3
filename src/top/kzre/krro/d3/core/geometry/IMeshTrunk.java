package top.kzre.krro.d3.core.geometry;

/**
 * 网格分块的只读视图与生命周期契约。
 *
 * <p>所有字段在构造时确定——接口不暴露任何修改入口。需要变更时，
 * 由上层通过 {@link #cloneTrunk()} 产出独立副本。
 *
 * <p><b>引用计数</b>：{@link #acquire()} / {@link #release()} 管理
 * COW 引用。归零时触发内部资源释放。
 *
 * <p><b>线程契约</b>：读方法无副作用——任意线程可调。引用计数
 * 是原子的——{@code acquire} / {@code release} 可并发。
 */
public interface IMeshTrunk {

    /** 顶点缓冲区。 */
    IVertexBuffer getBuffer();

    /** 本 trunk 在全局顶点编号空间中的起点。 */
    int getStartVertex();
    /** 本分块包含的顶点数。 */
    int getVertexCount();

    /** Morton 码（模型空间）。 */
    long getMorton();

    /** 当前引用计数——用于判断是否独占。 */
    int refCount();

    /** 增加引用计数。返回自增后的值。 */
    int acquire();

    /** 减少引用计数。返回自减后的值。归零时触发资源释放。 */
    int release();

    /**
     * 克隆当前分块——独立顶点缓冲、元数据一致、引用计数为 1。
     */
    IMeshTrunk cloneTrunk();
}