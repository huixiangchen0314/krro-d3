(ns top.kzre.krro.d3.core.geometry.util
  (:require
    [top.kzre.krro.d3.core.geometry.mesh]
   [top.kzre.krro.d3.core.geometry.vertex-buffer :as vb])
  (:import
    (top.kzre.krro.d3.core.geometry AABB ImmutableAABB IMeshTrunk)
    (top.kzre.krro.d3.core.geometry.vertex_buffer VertexBuffer)
    (top.kzre.krro.util.math KMath)))

(defn mesh-trunk-aabb
  "计算网格分块的 AABB 包围盒（模型空间），使用原始类型避免装箱。"
  ^AABB [^IMeshTrunk trunk]
  (let [^VertexBuffer buffer (.getBuffer trunk)
        vertex-count (.getVertexCount trunk)]
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
        (AABB. min-x min-y min-z max-x max-y max-z)))))

(defn mesh-trunk-aabb-immutable ^ImmutableAABB
  [^IMeshTrunk trunk]
  (.toImmutable (mesh-trunk-aabb trunk)))
