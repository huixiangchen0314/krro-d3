package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.geometry.Point;
import top.kzre.krro.d3.core.geometry.Ray;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;
import top.kzre.krro.util.arena.FloatArenaView;
import top.kzre.krro.util.arena.IntArenaView;

/**
 * 射线-多边形面相交实现。
 *
 * <p><b>算法</b>：
 * <ol>
 *   <li>Newell 法线——对多边形顶点求和得到平面法线（模长 = 2 × 面积）</li>
 *   <li>法线模长平方低于 {@link #NORMAL_EPS_SQ} → 退化面，miss</li>
 *   <li>射线-平面求交：{@code t = -(N·(O-P0)) / (N·D)}</li>
 *   <li>{@code |N·D| / |N| < DENOM_EPS}（射线近平行平面）→ miss</li>
 *   <li>{@code t < 0}（交点在射线反方向）→ miss</li>
 *   <li>交点投影到 2D（丢弃 {@code |N|} 最大轴）</li>
 *   <li>PNPOLY 流式点在多边形内检测——不分配临时数组</li>
 * </ol>
 *
 * <p><b>坐标系契约</b>：{@code positions} 与 {@code ray} 必须处于同一坐标系
 * （通常是世界坐标系）。算法不做任何空间变换。
 *
 * <p><b>数据约定</b>：{@code positions} 是 per-vertex 位置，
 * {@code cornerVerts[faceStart .. faceStart+nPoly)} 是面角到顶点的索引——
 * 与 {@code MikkTriangulationImpl} / {@code GeometryEdgeTangentsImpl}
 * 的约定一致。
 *
 * <p><b>精度</b>：中间计算全部用 {@code double}，仅 {@code Result.t} 与
 * {@link Point} 坐标存 float。避免 Newell 法线求和、{@code t} 分母、
 * 交点坐标在小面 / 大 t 场景下的精度损失。
 *
 * <p><b>退化处理</b>（一律 miss，不抛异常）：
 * <ul>
 *   <li>{@code nPoly < 3}——不是面</li>
 *   <li>Newell 法线模长平方 &lt; {@link #NORMAL_EPS_SQ}——共线/零面积</li>
 *   <li>射线近平行平面——避免 {@code t} 爆炸</li>
 *   <li>{@code t < 0}——交点在射线反方向</li>
 * </ul>
 *
 * <p><b>边界情况</b>：射线恰好打在多边形边上时，PNPOLY 判定为
 * "左/下边算内、右/上边算外"（标准行为）。拾取场景够用；若需"边上也算
 * 命中"，在 PNPOLY 前额外做点在边上检测。
 *
 * <p><b>背面</b>：本算法不做 front/back 判定——几何上穿透即命中，与绕序
 * 无关。需剔除背面时由调用方根据 {@code N·D} 符号判定。
 *
 * <p><b>线程契约</b>：无状态——同一实例可跨线程共享。
 */
public final class RayFaceIntersectionImpl implements RayFaceIntersection {

    /**
     * Newell 法线模长平方阈值——低于此视为退化面。
     * 模长 = 2 × 面积 → 模长平方 = 4 × 面积²。1e-20 对应面积约 1.6e-11。
     */
    private static final double NORMAL_EPS_SQ = 1e-20;

    /**
     * 射线-平面求交的 {@code |cosθ|} 阈值。{@code |N·D| / |N| < DENOM_EPS}
     * 视为平行。1e-6 对应约 89.99994°。
     */
    private static final double DENOM_EPS = 1e-6;

    @Override
    public Result compute(
            Ray ray,
            CopyOnWriteFloats positions,
            CopyOnWriteInts cornerVerts,
            int faceStart, int nPoly,
            Result result) {

        if (ray == null)         throw new NullPointerException("ray");
        if (positions == null)   throw new NullPointerException("positions");
        if (cornerVerts == null) throw new NullPointerException("cornerVerts");
        if (result == null)      throw new NullPointerException("result");

        if (nPoly < 3) {
            return RayFaceIntersection.Result.miss(result);
        }

        try (FloatArenaView.ArraySession posSess = positions.array();
             IntArenaView.ArraySession  cvSess  = cornerVerts.array()) {

            float[] pos   = posSess.getFloats();
            int     pOff  = posSess.offset();
            int[]   cv    = cvSess.getInts();
            int     cvOff = cvSess.offset();

            // ── 1. Newell 法线（double 计算，同时取参考点 P0）──
            double nx = 0, ny = 0, nz = 0;

            int v0 = cv[cvOff + faceStart];
            int b0 = pOff + v0 * 3;
            double x0 = pos[b0];
            double y0 = pos[b0 + 1];
            double z0 = pos[b0 + 2];

            for (int i = 0; i < nPoly; i++) {
                int vCurr = cv[cvOff + faceStart + i];
                int vNext = cv[cvOff + faceStart + ((i + 1) % nPoly)];
                int bCurr = pOff + vCurr * 3;
                int bNext = pOff + vNext * 3;

                double xc = pos[bCurr];
                double yc = pos[bCurr + 1];
                double zc = pos[bCurr + 2];
                double xn = pos[bNext];
                double yn = pos[bNext + 1];
                double zn = pos[bNext + 2];

                nx += (yc - yn) * (zc + zn);
                ny += (zc - zn) * (xc + xn);
                nz += (xc - xn) * (yc + yn);
            }

            double nLenSq = nx * nx + ny * ny + nz * nz;
            if (nLenSq < NORMAL_EPS_SQ) {
                // 退化面——共线、零面积或全部顶点重合
                return RayFaceIntersection.Result.miss(result);
            }

            // ── 2. 射线-平面求交 ──
            double dx = ray.dx;
            double dy = ray.dy;
            double dz = ray.dz;
            double ox = ray.ox;
            double oy = ray.oy;
            double oz = ray.oz;

            double denom = nx * dx + ny * dy + nz * dz;
            // |N·D| / |N| < DENOM_EPS ⇔ 射线近平行平面
            if (Math.abs(denom) < DENOM_EPS * Math.sqrt(nLenSq)) {
                return RayFaceIntersection.Result.miss(result);
            }

            // t = -(N·(O - P0)) / (N·D)
            double tNum = nx * (ox - x0) + ny * (oy - y0) + nz * (oz - z0);
            double t = -tNum / denom;
            if (t < 0) {
                // 交点在射线反方向
                return RayFaceIntersection.Result.miss(result);
            }

            // ── 3. 交点 ──
            double hx = ox + t * dx;
            double hy = oy + t * dy;
            double hz = oz + t * dz;

            // ── 4. 投影到 2D（丢弃 |N| 最大轴——该方向投影面最小、最稳）──
            double anx = Math.abs(nx), any = Math.abs(ny), anz = Math.abs(nz);
            int dropAxis;
            if (anx >= any && anx >= anz)      dropAxis = 0;
            else if (any >= anz)               dropAxis = 1;
            else                               dropAxis = 2;

            double px, py;
            switch (dropAxis) {
                case 0: px = hy; py = hz; break;
                case 1: px = hx; py = hz; break;
                default: px = hx; py = hy; break;
            }

            // ── 5. PNPOLY——流式检测，保持 prev 顶点 ──
            // prev 初始化为最后一个顶点
            int vPrev = cv[cvOff + faceStart + (nPoly - 1)];
            int bPrev = pOff + vPrev * 3;
            double prevX, prevY;
            switch (dropAxis) {
                case 0: prevX = pos[bPrev + 1]; prevY = pos[bPrev + 2]; break;
                case 1: prevX = pos[bPrev];     prevY = pos[bPrev + 2]; break;
                default: prevX = pos[bPrev];    prevY = pos[bPrev + 1]; break;
            }

            boolean inside = false;
            for (int i = 0; i < nPoly; i++) {
                int vCurr = cv[cvOff + faceStart + i];
                int bCurr = pOff + vCurr * 3;
                double currX, currY;
                switch (dropAxis) {
                    case 0: currX = pos[bCurr + 1]; currY = pos[bCurr + 2]; break;
                    case 1: currX = pos[bCurr];     currY = pos[bCurr + 2]; break;
                    default: currX = pos[bCurr];    currY = pos[bCurr + 1]; break;
                }

                // PNPOLY：curr = (xi, yi)，prev = (xj, yj)
                if (((currY > py) != (prevY > py))
                        && (px < (prevX - currX) * (py - currY) / (prevY - currY) + currX)) {
                    inside = !inside;
                }

                prevX = currX;
                prevY = currY;
            }

            if (!inside) {
                return RayFaceIntersection.Result.miss(result);
            }

            // ── 6. 命中 ──
            Point hitPoint = new Point((float) hx, (float) hy, (float) hz);
            return RayFaceIntersection.Result.hit(result, t, hitPoint);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}