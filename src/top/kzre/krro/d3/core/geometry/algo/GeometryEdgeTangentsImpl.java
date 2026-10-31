package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;

/**
 * 几何边->近似法向实现，UV 可为null。
 */
public final class GeometryEdgeTangentsImpl implements TriangulationAlgo{
    @Override
    public void compute(CopyOnWriteFloats positions,
                        CopyOnWriteInts cornerVerts,
                        CopyOnWriteInts faceOffsets,
                        CopyOnWriteFloats uv,
                        CopyOnWriteInts out) {

    }
}
