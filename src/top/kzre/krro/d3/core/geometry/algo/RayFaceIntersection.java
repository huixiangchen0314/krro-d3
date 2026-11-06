package top.kzre.krro.d3.core.geometry.algo;

import top.kzre.krro.d3.core.geometry.Point;
import top.kzre.krro.d3.core.geometry.Ray;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteFloats;
import top.kzre.krro.d3.core.util.cow.CopyOnWriteInts;

/**
 * 射线，面相交算法
 */
public interface RayFaceIntersection {

    /**
     * 计算射线面距离。不处理退化面，退化面一律按Miss 处理
     * faceStart 是面角起始，我们避免在算法做做前缀和，这会导致重复
     */
    Result compute(
            Ray ray,
            CopyOnWriteFloats positions,
                   CopyOnWriteInts cornerVerts,
                   int faceStart, int nPoly,
                   Result result);

    class Result{
        /**
         * 射线参数,double精度，比 point 更精确一点，
         * 如果确实需要精确的比较，用这个
         */
        private double t;
        /**
         * 射线-面交点
         */
        private Point point;
        /**
         * 是否击中
         */
        private boolean hit;

        private Result() {
        }

        private Result set(double t, Point point, boolean hit) {
            this.t = t;
            this.point = point;
            this.hit = hit;
            return this;
        }

        public static Result create(){
            return new Result();
        }

        public static Result hit(Result result, double t, Point point) {
            return result.set(t, point, true);
        }

        public static Result miss(Result result){
            return result.set(Double.MAX_VALUE, null, false);
        }

        public double getT() {
            return t;
        }

        public Point getPoint() {
            return point;
        }

        public boolean isHit() {
            return hit;
        }
    }
}
