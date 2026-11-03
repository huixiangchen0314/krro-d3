package top.kzre.krro.d3.core.scene;

/**
 * 相机——场景对象，纯观察配置。
 *
 * <p><b>位姿不在这里</b>：相机节点的局部变换存在
 * {@link Hierarchy#transforms()}，全局变换由 {@link HierarchyEditor#getGlobalTransforms()}
 * 缓存。{@code view} 矩阵是相机全局变换的逆。
 *
 * <p><b>交互状态不在这里</b>：轨道控制器的取景中心、距离、yaw/pitch
 * 是编辑器层的状态，由编辑器自己维护。库层不知道“围绕什么转”。
 *
 * <p><b>渲染配置不在这里</b>：GI、后处理、clear flags 等是视口和
 * 渲染管线的职责，在调度层组装进 {@code RenderRequest}。
 *
 * <p><b>不可变</b>：所有字段 {@code final}。修改相机配置 = 创建新实例
 * + 替换 {@code objects} 表里的条目。
 *
 * <p><b>不实现 {@code CopyOnWrite}</b>：纯配置，没有需要释放的资源。
 */
public final class Camera implements SceneObject {

    /** 投影模式。 */
    public enum Projection {
        PERSPECTIVE,
        ORTHOGRAPHIC
    }

    private final long id;
    private final Projection projection;

    /** 透视投影的垂直 FOV（度）。 */
    private final float fovYDeg;

    /** 正交投影下视口高度对应的世界单位。 */
    private final float orthoHeight;

    private final float near;
    private final float far;

    public Camera(long id,
                  Projection projection,
                  float fovYDeg,
                  float orthoHeight,
                  float near,
                  float far) {
        if (projection == null) {
            throw new NullPointerException("projection");
        }
        if (fovYDeg <= 0f || fovYDeg >= 180f) {
            throw new IllegalArgumentException(
                    "fovYDeg must be in (0, 180): " + fovYDeg);
        }
        if (orthoHeight <= 0f) {
            throw new IllegalArgumentException(
                    "orthoHeight must be > 0: " + orthoHeight);
        }
        if (near <= 0f || far <= near) {
            throw new IllegalArgumentException(
                    "require 0 < near < far: near=" + near + ", far=" + far);
        }

        this.id = id;
        this.projection = projection;
        this.fovYDeg = fovYDeg;
        this.orthoHeight = orthoHeight;
        this.near = near;
        this.far = far;
    }

    @Override
    public long getId() { return id; }

    public Projection getProjection() { return projection; }

    public float getFovYDeg() { return fovYDeg; }

    public float getOrthoHeight() { return orthoHeight; }

    public float getNear() { return near; }

    public float getFar() { return far; }
}