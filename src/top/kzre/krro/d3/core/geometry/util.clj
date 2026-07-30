(ns top.kzre.krro.d3.core.geometry.util
  (:require
    [top.kzre.krro.d3.core.geometry.mesh]
   [top.kzre.krro.d3.core.geometry.vertex-buffer :as vb])
  (:import
    (top.kzre.krro.d3.core.geometry AABB Morton)
    (top.kzre.krro.d3.core.geometry MeshTrunk)
    (top.kzre.krro.d3.core.geometry.vertex_buffer VertexBuffer)
    (top.kzre.krro.util.math KMath)))

(defn enable-unchecked-math [] (set! *unchecked-math* :warn-on-boxed))

(defn disable-unchecked-math [] (set! *unchecked-math* nil))


(defn mesh-trunk-aabb
  "计算网格分块的 AABB 包围盒（模型空间），使用原始类型避免装箱。"
  ^AABB [^MeshTrunk trunk]
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
                 (KMath/mind min-x x)
                 (KMath/mind min-y y)
                 (KMath/mind min-z z)
                 (KMath/maxd max-x x)
                 (KMath/maxd max-y y)
                 (KMath/maxd max-z z)))
        (AABB. min-x min-y min-z max-x max-y max-z)))))

(defn morton ^long [^AABB trunk-aabb ^AABB global-aabb]
  (Morton/fromAABBs trunk-aabb global-aabb))