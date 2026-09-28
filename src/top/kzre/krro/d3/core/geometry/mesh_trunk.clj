(ns top.kzre.krro.d3.core.geometry.mesh-trunk
  (:require
    [top.kzre.krro.d3.core.geometry.vertex-buffer :as vb])
  (:import
    (top.kzre.krro.d3.core.geometry AABB ImmutableAABB IMeshTrunk)
    (top.kzre.krro.d3.core.geometry.vertex_buffer VertexBuffer)
    (top.kzre.krro.util.math KMath)))

(defn mesh-trunk-aabb
  "计算网格分块的 AABB 包围盒（模型空间）。

   写入 out 并返回 out——避免分配。
   空 trunk 返回空盒（isEmpty 为 true）——调用方检查。"
  (^AABB [^IMeshTrunk trunk ^AABB out]
   {:pre [(some? trunk) (some? out)]}
   (let [^VertexBuffer buffer (.getBuffer trunk)
         vertex-count   (.getVertexCount trunk)]
     (loop [i 0
            min-x Float/POSITIVE_INFINITY
            min-y Float/POSITIVE_INFINITY
            min-z Float/POSITIVE_INFINITY
            max-x Float/NEGATIVE_INFINITY
            max-y Float/NEGATIVE_INFINITY
            max-z Float/NEGATIVE_INFINITY]
       (if (< i vertex-count)
         (let [x (vb/obj-vertex-x buffer i)
               y (vb/obj-vertex-y buffer i)
               z (vb/obj-vertex-z buffer i)]
           (recur (unchecked-inc i)
                  (KMath/minD min-x x)
                  (KMath/minD min-y y)
                  (KMath/minD min-z z)
                  (KMath/maxD max-x x)
                  (KMath/maxD max-y y)
                  (KMath/maxD max-z z)))
         (.set out (float min-x) (float min-y) (float min-z)
               (float max-x) (float max-y) (float max-z)))))
   out)
  (^AABB [^IMeshTrunk trunk]
   (mesh-trunk-aabb trunk (AABB.))))

(defn mesh-trunk-aabb-immutable
  "计算分块 AABB——返回不可变副本。"
  ^ImmutableAABB [^IMeshTrunk trunk]
  (.toImmutable (mesh-trunk-aabb trunk)))