package top.kzre.krro.d3.core.geometry.bmesh;


import top.kzre.krro.util.arena.AbstractArenaTemplate;
import top.kzre.krro.util.arena.FloatArenaView;
import top.kzre.krro.util.arena.IntArenaView;

/**
 * 插入顶点
 */
public class AddVertex {

    /**
     * 插入孤儿顶点。
     */
//    public static int insertOrphanVertex(
//            BMesh mesh,
//            AbstractArenaTemplate<?, FloatArenaView> floatArena,
//            AbstractArenaTemplate<?, IntArenaView>   intArena,
//            float x, float y, float z) {
//
//        // 1. 下一个位置 = 顶点数
//        long index = vertCount(mesh);
//
//        // 2. 块位置
//        int blockId  = (int) (index / BLOCK_SIZE);
//        int localIdx = (int) (index % BLOCK_SIZE);
//
//        // 3. 确保块存在
//        ensureBlocks(mesh, floatArena, intArena, blockId);
//
//        // 4. 写三个属性
//        writePosition(mesh, blockId, localIdx, x, y, z);
//        writeNormal  (mesh, blockId, localIdx, 0, 0, 0);
//        writeOutEdge (mesh, blockId, localIdx, -1);
//
//        // 5. 计数 +1
//        incrementVertCount(mesh);
//
//        // 6. 返回位置 = 身份
//        return index;
//    }
}
