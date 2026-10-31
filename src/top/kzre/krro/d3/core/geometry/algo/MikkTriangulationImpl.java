package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;

/**
 * MIKK  标准三角化算法
 */
public final class MikkTriangulationImpl implements TriangulationAlgo{
    @Override
    public void compute(CopyOnWriteFloats positions,
                        CopyOnWriteInts cornerVerts,
                        CopyOnWriteInts faceOffsets,
                        CopyOnWriteFloats uv,
                        CopyOnWriteInts out) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
