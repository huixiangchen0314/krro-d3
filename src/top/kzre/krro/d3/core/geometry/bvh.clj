(ns top.kzre.krro.d3.core.geometry.bvh
  (:require [top.kzre.deflayout.core :refer [deflayout]]
            [top.kzre.krro.d3.core.geometry.aabb :as aabb])
  (:import (top.kzre.krro.d3.core.geometry SpatialRayHit SpatialOverlay IAABB IntersectionAlgo Ray Sphere)
           (top.kzre.krro.util.math KMath)))

(set! *unchecked-math* true)

;; ═══════════════════════════════════════════════
;; 声明——IDE 友好
;; ═══════════════════════════════════════════════

(declare aabb-min-x aabb-min-y aabb-min-z aabb-max-x aabb-max-y aabb-max-z)
(declare set-aabb-min-x! set-aabb-min-y! set-aabb-min-z!
  set-aabb-max-x! set-aabb-max-y! set-aabb-max-z!)
(declare children-left children-right)
(declare set-children-left! set-children-right!)

(declare obj-aabb-min-x obj-aabb-min-y obj-aabb-min-z
  obj-aabb-max-x obj-aabb-max-y obj-aabb-max-z)
(declare obj-set-aabb-min-x! obj-set-aabb-min-y! obj-set-aabb-min-z!
  obj-set-aabb-max-x! obj-set-aabb-max-y! obj-set-aabb-max-z!)
(declare obj-children-left obj-children-right)
(declare obj-set-children-left! obj-set-children-right!)

;; ═══════════════════════════════════════════════
;; 元数据类型——deftype 承载 primitive 字段
;; ═══════════════════════════════════════════════

(deftype BVHMeta [^long primitive-count])

;; ═══════════════════════════════════════════════
;; 布局——必须在 write-aabb! / merge-aabb! 之前
;; ═══════════════════════════════════════════════

(deflayout BVH
           {:data  [:float [:aabb [:min-x :min-y :min-z :max-x :max-y :max-z]]]
            :child [:int   [:children [:left :right]]]}
           {:unchecked-math? true
            :ext?            true})

;; ═══════════════════════════════════════════════
;; 构造
;; ═══════════════════════════════════════════════

(defn make-bvh
  "构造 BVH。

   两 arity：
   - (make-bvh data child)                空 BVH——primitive-count = 0
   - (make-bvh data child primitive-count) 指定图元数"
  (^BVH [^floats data ^ints child]
   (BVH. data child (BVHMeta. 0)))
  (^BVH [^floats data ^ints child primitive-count]
   (BVH. data child (BVHMeta. primitive-count))))

;; ═══════════════════════════════════════════════
;; 元数据访问——primitive long——无装箱
;; ═══════════════════════════════════════════════

(defn primitive-count
  "BVH 覆盖的图元数量。0 表示空 BVH。"
  ^long [^BVH bvh]
  (.-primitive-count ^BVHMeta (.ext bvh)))

;; ═══════════════════════════════════════════════
;; 数组版本——宏已展开——可安全调用
;; ═══════════════════════════════════════════════

(defn write-aabb!
  [^floats bvh-data node-idx ^IAABB aabb]
  (set-aabb-min-x! bvh-data node-idx (.getMinX aabb))
  (set-aabb-min-y! bvh-data node-idx (.getMinY aabb))
  (set-aabb-min-z! bvh-data node-idx (.getMinZ aabb))
  (set-aabb-max-x! bvh-data node-idx (.getMaxX aabb))
  (set-aabb-max-y! bvh-data node-idx (.getMaxY aabb))
  (set-aabb-max-z! bvh-data node-idx (.getMaxZ aabb)))

(defmacro merge-aabb!
  "合并左右子 AABB 到父节点——宏——调用点展开。

   布局知识全在 bvh.clj——本宏不涉及 AabbArray。"
  [bvh-data parent-idx left-idx right-idx]
  `(let [b# ~bvh-data
         p# (int ~parent-idx)
         l# (int ~left-idx)
         r# (int ~right-idx)]
     (set-aabb-min-x! b# p#
                      (KMath/min (aabb-min-x b# l#)
                                 (aabb-min-x b# r#)))
     (set-aabb-min-y! b# p#
                      (KMath/min (aabb-min-y b# l#)
                                 (aabb-min-y b# r#)))
     (set-aabb-min-z! b# p#
                      (KMath/min (aabb-min-z b# l#)
                                 (aabb-min-z b# r#)))
     (set-aabb-max-x! b# p#
                      (KMath/max (aabb-max-x b# l#)
                                 (aabb-max-x b# r#)))
     (set-aabb-max-y! b# p#
                      (KMath/max (aabb-max-y b# l#)
                                 (aabb-max-y b# r#)))
     (set-aabb-max-z! b# p#
                      (KMath/max (aabb-max-z b# l#)
                                 (aabb-max-z b# r#)))))

(defmacro write-aabb-from!
  "从 AabbArray 布局的数组第 src-idx 槽写入 BVH 的 node-idx 槽。

   布局知识全在 aabb.clj——本宏不硬编码下标。"
  [bvh-data node-idx src src-idx]
  `(do (set-aabb-min-x! ~bvh-data ~node-idx (aabb/aabb-min-x ~src ~src-idx))
       (set-aabb-min-y! ~bvh-data ~node-idx (aabb/aabb-min-y ~src ~src-idx))
       (set-aabb-min-z! ~bvh-data ~node-idx (aabb/aabb-min-z ~src ~src-idx))
       (set-aabb-max-x! ~bvh-data ~node-idx (aabb/aabb-max-x ~src ~src-idx))
       (set-aabb-max-y! ~bvh-data ~node-idx (aabb/aabb-max-y ~src ~src-idx))
       (set-aabb-max-z! ~bvh-data ~node-idx (aabb/aabb-max-z ~src ~src-idx))))

;; ═══════════════════════════════════════════════
;; 对象版本——转发到数组版
;; ═══════════════════════════════════════════════

(defn obj-write-aabb!
  [^BVH bvh node-idx ^IAABB aabb]
  (write-aabb! (.data bvh) node-idx aabb))

(defn obj-merge-aabb!
  [^BVH bvh parent-idx left-idx right-idx]
  (merge-aabb! (.data bvh) parent-idx left-idx right-idx))

;; ═══════════════════════════════════════════════
;; 查询——概念操作——无 obj- 前缀
;; ═══════════════════════════════════════════════
;; TODO 多图元查询升级

(defn ray-query ^SpatialRayHit
  [^BVH lbvh ^Ray ray]
  (let [n (primitive-count lbvh)]
    (if (zero? n)
      SpatialRayHit/MISS
      (let [^floats nodes (.data lbvh)
            ^ints   child (.child lbvh)
            ^ints   stack (int-array 128)]
        (aset stack 0 0)
        (loop [stack-ptr (int 1)
               best-idx  (int -1)
               best-t    Float/MAX_VALUE]
          (if (zero? stack-ptr)
            (if (>= best-idx 0)
              (SpatialRayHit. best-idx best-t)
              SpatialRayHit/MISS)
            (let [stack-ptr (int (dec stack-ptr))
                  node-idx  (int (aget stack stack-ptr))
                  min-x (aabb-min-x nodes node-idx)
                  min-y (aabb-min-y nodes node-idx)
                  min-z (aabb-min-z nodes node-idx)
                  max-x (aabb-max-x nodes node-idx)
                  max-y (aabb-max-y nodes node-idx)
                  max-z (aabb-max-z nodes node-idx)
                  hit   (IntersectionAlgo/intersectRayAABB
                          ray min-x min-y min-z max-x max-y max-z)]
              (if (and (.hit hit) (< (.t hit) best-t))
                (let [left  (children-left  child node-idx)
                      right (children-right child node-idx)]
                  (if (== left -1)
                    (recur stack-ptr right (.t hit))
                    (let [stack-ptr (int (+ stack-ptr 2))]
                      (aset stack (- stack-ptr 2) left)
                      (aset stack (- stack-ptr 1) right)
                      (recur stack-ptr best-idx best-t))))
                (recur stack-ptr best-idx best-t)))))))))

(defn sphere-query ^SpatialOverlay
  [^BVH lbvh ^Sphere sphere]
  (let [n (primitive-count lbvh)]
    (if (zero? n)
      SpatialOverlay/EMPTY
      (let [^floats nodes  (.data lbvh)
            ^ints   child  (.child lbvh)
            ^ints   stack  (int-array 128)
            cx     (.cx sphere)
            cy     (.cy sphere)
            cz     (.cz sphere)
            radius (.radius sphere)
            out    (int-array 64)     ; 预分配——不够再扩容
            cap    (int-array 1)]     ; 当前有效长度（用数组避免装箱）
        (aset cap 0 0)
        (aset stack 0 0)
        (loop [stack-ptr (int 1)]
          (if (zero? stack-ptr)
            (SpatialOverlay. out (aget cap 0))
            (let [stack-ptr (int (dec stack-ptr))
                  node-idx  (int (aget stack stack-ptr))
                  min-x (aabb-min-x nodes node-idx)
                  min-y (aabb-min-y nodes node-idx)
                  min-z (aabb-min-z nodes node-idx)
                  max-x (aabb-max-x nodes node-idx)
                  max-y (aabb-max-y nodes node-idx)
                  max-z (aabb-max-z nodes node-idx)]
              (if (IntersectionAlgo/sphereIntersectsAABB
                    cx cy cz radius min-x min-y min-z max-x max-y max-z)
                (let [left  (children-left  child node-idx)
                      right (children-right child node-idx)]
                  (if (== left -1)
                    (do (aset out (aget cap 0) right)
                        (aset cap 0 (inc (aget cap 0)))
                        (recur stack-ptr))
                    (let [stack-ptr (int (+ stack-ptr 2))]
                      (aset stack (- stack-ptr 2) left)
                      (aset stack (- stack-ptr 1) right)
                      (recur stack-ptr))))
                (recur stack-ptr)))))))))
