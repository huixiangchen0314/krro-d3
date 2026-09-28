(ns top.kzre.krro.d3.core.geometry.lbvh
  "线性编码BVH，构建速度 O(n log n)，用于本地空间编码加速。"
  (:require
   [top.kzre.deflayout.core :refer [deflayout]]
   [top.kzre.krro.d3.core.geometry.util :as util])
  (:import
   (java.util.concurrent.atomic AtomicInteger)
   (top.kzre.krro.d3.core.geometry
    AABB
    IntersectionAlgo
    MeshTrunk
    Ray)
   (top.kzre.krro.util.math KMath)))

;; 前向声明 deflayout 即将生成的访问器宏，消除 IDE 警告
(declare aabb-min-x aabb-min-y aabb-min-z aabb-max-x aabb-max-y aabb-max-z)
(declare set-aabb-min-x! set-aabb-min-y! set-aabb-min-z! set-aabb-max-x! set-aabb-max-y! set-aabb-max-z!)
(declare children-left children-right)
(declare set-children-left! set-children-right!)


(util/enable-unchecked-math)

;; ─── Lbvh 布局定义 ─────────────────────────────
(deflayout Lbvh
           {:data [:float [:aabb [:min-x :min-y :min-z
                                  :max-x :max-y :max-z]
                           :children [:left :right]]]}
           {:unchecked-math? true})

;; ─── 内部辅助（仍直接操作 float 数组）─────────────
(defn- node-count [n]
  (+ n (max 0 (dec n))))

(defn- allocate-nodes [n]
  (float-array (* (node-count n) 8)))

(defn- write-aabb!
  [^floats nodes node-idx ^AABB aabb]
  (set-aabb-min-x! nodes node-idx (float (.minX aabb)))
  (set-aabb-min-y! nodes node-idx (float (.minY aabb)))
  (set-aabb-min-z! nodes node-idx (float (.minZ aabb)))
  (set-aabb-max-x! nodes node-idx (float (.maxX aabb)))
  (set-aabb-max-y! nodes node-idx (float (.maxY aabb)))
  (set-aabb-max-z! nodes node-idx (float (.maxZ aabb))))

(defn- merge-aabb!
  [^floats nodes parent-idx left-idx right-idx]
  (set-aabb-min-x! nodes parent-idx
                   (KMath/min (aabb-min-x nodes left-idx) (aabb-min-x nodes right-idx)))
  (set-aabb-min-y! nodes parent-idx
                   (KMath/min (aabb-min-y nodes left-idx) (aabb-min-y nodes right-idx)))
  (set-aabb-min-z! nodes parent-idx
                   (KMath/min (aabb-min-z nodes left-idx) (aabb-min-z nodes right-idx)))
  (set-aabb-max-x! nodes parent-idx
                   (KMath/max (aabb-max-x nodes left-idx) (aabb-max-x nodes right-idx)))
  (set-aabb-max-y! nodes parent-idx
                   (KMath/max (aabb-max-y nodes left-idx) (aabb-max-y nodes right-idx)))
  (set-aabb-max-z! nodes parent-idx
                   (KMath/max (aabb-max-z nodes left-idx) (aabb-max-z nodes right-idx))))

(defn- set-leaf!
  [^floats nodes node-idx chunk-idx]
  (set-children-left!  nodes node-idx -1.0)
  (set-children-right! nodes node-idx (float chunk-idx)))

(defn- set-internal!
  [^floats nodes node-idx left-idx right-idx]
  (set-children-left!  nodes node-idx (float left-idx))
  (set-children-right! nodes node-idx (float right-idx)))

;; ─── 构建核心 ──────────────────────────────────
(defn- build-rec
  [^floats nodes trunks start end ^AtomicInteger next-idx]
  (let [n (- end start)]
    (if (= n 1)
      (let [idx (.getAndIncrement next-idx)
            ^MeshTrunk trunk (nth trunks start)]
        (write-aabb! nodes idx (.getAabb trunk))
        (set-leaf! nodes idx start)
        idx)
      (let [codes (map #(.getMorton ^MeshTrunk %) (subvec trunks start end))
            min-code (reduce min codes)
            max-code (reduce max codes)
            diff-bit (Long/highestOneBit (bit-xor min-code max-code))
            split (loop [i start]
                    (if (>= i end)
                      end
                      (if (not= 0 (bit-and (.getMorton ^MeshTrunk (nth trunks i)) diff-bit))
                        i
                        (recur (inc i)))))
            split (if (or (= split start) (= split end))
                    (+ start (quot n 2))
                    split)
            left-idx  (build-rec nodes trunks start split next-idx)
            right-idx (build-rec nodes trunks split end next-idx)
            idx (.getAndIncrement next-idx)]
        (merge-aabb! nodes idx left-idx right-idx)
        (set-internal! nodes idx left-idx right-idx)
        idx))))

;; ─── 公共构建函数（返回 Lbvh 实例） ──────────────
(defn build-lbvh
  "根据已按 Morton 码排序的 MeshTrunk 序列构建线性 BVH。
   返回 {:lbvh Lbvh, :trunk-count n}"
  [trunks]
  (let [n (count trunks)
        nodes (allocate-nodes n)
        next-idx (AtomicInteger. 0)]
    (if (zero? n)
      {:lbvh (Lbvh. (float-array 0)) :trunk-count 0}
      (do
        (build-rec nodes (vec trunks) 0 n next-idx)
        {:lbvh (Lbvh. nodes) :trunk-count n}))))

;; ─── 射线查询（接收 Lbvh 实例） ──────────────────
(defn ray-query
  [^Lbvh lbvh trunk-count ^Ray ray]
  (when (pos? trunk-count)
    (let [^floats nodes (.data lbvh)
          stack (int-array 128)]
      (aset stack 0 0)                        ; 根节点索引
      (loop [stack-ptr (int 1)                ; 栈内元素个数
             best-idx  (int -1)
             best-t    (float Float/MAX_VALUE)]
        (if (zero? stack-ptr)
          (when (>= best-idx 0) best-idx)
          (let [stack-ptr (int (dec stack-ptr))
                node-idx  (int (aget stack stack-ptr))
                min-x (aabb-min-x nodes node-idx)
                min-y (aabb-min-y nodes node-idx)
                min-z (aabb-min-z nodes node-idx)
                max-x (aabb-max-x nodes node-idx)
                max-y (aabb-max-y nodes node-idx)
                max-z (aabb-max-z nodes node-idx)
                aabb (AABB. min-x min-y min-z max-x max-y max-z)
                hit   (IntersectionAlgo/intersectRayAABB ray aabb)]
            (if (and (.hit hit) (< (.t hit) best-t))
              (let [left  (children-left nodes node-idx)
                    right (children-right nodes node-idx)]
                (if (== left -1.0)            ; 叶节点
                  (recur stack-ptr (int right) (.t hit))
                  ;; 内部节点：压入左右子节点（先右后左，栈后进先出）
                  (let [stack-ptr (int (+ stack-ptr 2))]
                    (aset stack (- stack-ptr 2) (int left))
                    (aset stack (- stack-ptr 1) (int right))
                    (recur stack-ptr best-idx best-t))))
              (recur stack-ptr best-idx best-t))))))))

;; ─── 球体查询（接收 Lbvh 实例） ──────────────────
(defn sphere-query
  [^Lbvh lbvh trunk-count cx cy cz radius]
  (when (pos? trunk-count)
    (let [^floats nodes (.data lbvh)
          stack (int-array 128)
          result (transient #{})]
      (aset stack 0 0)                              ; 根节点索引
      (loop [stack-ptr (int 1)                     ; 栈内元素个数
             result result]
        (if (zero? stack-ptr)
          (persistent! result)
          (let [stack-ptr (int (dec stack-ptr))     ; 弹出栈顶
                node-idx  (int (aget stack stack-ptr))
                min-x (aabb-min-x nodes node-idx)
                min-y (aabb-min-y nodes node-idx)
                min-z (aabb-min-z nodes node-idx)
                max-x (aabb-max-x nodes node-idx)
                max-y (aabb-max-y nodes node-idx)
                max-z (aabb-max-z nodes node-idx)
                aabb (AABB. min-x min-y min-z max-x max-y max-z)]
            (if (IntersectionAlgo/sphereIntersectsAABB cx cy cz radius aabb)
              (let [left  (children-left  nodes node-idx)
                    right (children-right nodes node-idx)]
                (if (== left -1.0)                  ; 叶节点
                  (recur stack-ptr (conj! result (int right)))
                  ;; 内部节点：压入左右子节点
                  (let [stack-ptr (int (+ stack-ptr 2))]
                    (aset stack (- stack-ptr 2) (int left))
                    (aset stack (- stack-ptr 1) (int right))
                    (recur stack-ptr result))))
              (recur stack-ptr result))))))))

(util/disable-unchecked-math)