package top.kzre.krro.d3.core.geometry;

import top.kzre.krro.d3.core.util.cow.*;
import top.kzre.krro.util.arena.*;

import java.util.Collections;

public final class Meshes {

    private Meshes() {}

    /**
     * 单位 cube——边长 2，中心在原点。
     *
     * <p>8 顶点 + 24 面角 + 12 边。面角模型：
     * 顶点位置共享，UV 按面角独立，法线由 AngleClusteredNormals
     * 按 90° 夹角自动分离。所有面从外部看 CCW。
     *
     * <p>单材质槽（slot 0），6 个面全部归一个 SubMesh。
     */
    public static Mesh cube(
            ArenaTemplate<?, FloatArenaView> floatTemplate,
            ArenaTemplate<?, IntArenaView>   intTemplate) {

        float[] positions = {
                -1, -1, -1,   // 0
                1, -1, -1,   // 1
                1,  1, -1,   // 2
                -1,  1, -1,   // 3
                -1, -1,  1,   // 4
                1, -1,  1,   // 5
                1,  1,  1,   // 6
                -1,  1,  1,   // 7
        };

        int[] cornerVerts = {
                1, 2, 6, 5,   // +X
                0, 4, 7, 3,   // -X
                3, 7, 6, 2,   // +Y
                0, 1, 5, 4,   // -Y
                4, 5, 6, 7,   // +Z
                0, 3, 2, 1,   // -Z
        };

        int[] faceOffsets = { 0, 4, 8, 12, 16, 20, 24 };

        float[] uv0 = new float[48];
        for (int f = 0; f < 6; f++) {
            int b = f * 8;
            uv0[b + 0] = 0; uv0[b + 1] = 0;
            uv0[b + 2] = 1; uv0[b + 3] = 0;
            uv0[b + 4] = 1; uv0[b + 5] = 1;
            uv0[b + 6] = 0; uv0[b + 7] = 1;
        }

        int[] edgeEndpoints = {
                0, 1,  0, 3,  0, 4,
                1, 2,  1, 5,
                2, 3,  2, 6,
                3, 7,
                4, 5,  4, 7,
                5, 6,
                6, 7,
        };

        CopyOnWriteFloats positionsCow     = allocFloats(floatTemplate, positions);
        CopyOnWriteInts   cornerVertsCow   = allocInts(intTemplate, cornerVerts);
        CopyOnWriteInts   faceOffsetsCow   = allocInts(intTemplate, faceOffsets);
        CopyOnWriteFloats uv0Cow           = allocFloats(floatTemplate, uv0);
        CopyOnWriteInts   edgeEndpointsCow = allocInts(intTemplate, edgeEndpoints);

        CopyOnWriteObject<ListResource<CopyOnWriteFloats>> cornerUvs =
                new DefaultCopyOnWriteObject<>(
                        new ListResource<>(
                                Collections.singletonList(uv0Cow)));

        CopyOnWriteObject<ShallowListResource<SubMesh>> submeshes =
                new DefaultCopyOnWriteObject<>(
                        new ShallowListResource<>(
                                Collections.singletonList(new SubMesh(0, 0, 6))));

        return new Mesh(
                positionsCow, cornerVertsCow, faceOffsetsCow,
                cornerUvs, edgeEndpointsCow, submeshes);
    }

    // ═══════════════════════════════════════════════

    private static CopyOnWriteFloats allocFloats(
            ArenaTemplate<?, FloatArenaView> template, float[] data) {
        int bytes = data.length * Float.BYTES;
        AllocateResult<FloatArenaView> r = template.allocate(bytes);
        if (r.isFailure()) {
            throw new IllegalStateException(
                    "float allocation failed: " + bytes + " bytes");
        }
        FloatArenaView view = r.getView();
        try (FloatArenaView.BufferSession session = view.buffer()) {
            session.floatBuffer().put(data);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }

        return new DefaultCopyOnWriteFloats(view);
    }

    private static CopyOnWriteInts allocInts(
            ArenaTemplate<?, IntArenaView> template, int[] data) {
        int bytes = data.length * Integer.BYTES;
        AllocateResult<IntArenaView> r = template.allocate(bytes);
        if (r.isFailure()) {
            throw new IllegalStateException(
                    "int allocation failed: " + bytes + " bytes");
        }
        IntArenaView view = r.getView();
        try (IntArenaView.BufferSession session = view.buffer()) {
            session.intBuffer().put(data);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }

        return new DefaultCopyOnWriteInts(view);
    }
}