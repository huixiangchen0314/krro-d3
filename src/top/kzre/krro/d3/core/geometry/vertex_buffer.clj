(ns top.kzre.krro.d3.core.geometry.vertex-buffer
  (:require
    [top.kzre.deflayout.core :refer [deflayout]])
  (:import (top.kzre.krro.d3.core.geometry IVertexBuffer)
           (top.kzre.krro.util.pool FloatsPool FloatsPools IntsPool IntsPools)))


;; ═════════════════════════════════════════════════════════════
;; 单个分量读取 – 数组版本 (arr idx)
;; ═════════════════════════════════════════════════════════════
(declare position-x)      ; (position-x arr idx) → float
(declare position-y)
(declare position-z)
(declare normal-x)
(declare normal-y)
(declare normal-z)
(declare uv-u)
(declare uv-v)
(declare weights-0)
(declare weights-1)
(declare weights-2)
(declare weights-3)
(declare bone-ids-0)
(declare bone-ids-1)
(declare bone-ids-2)
(declare bone-ids-3)

;; ═════════════════════════════════════════════════════════════
;; 单个分量读取 – obj 版本 (buffer idx)
;; ═════════════════════════════════════════════════════════════
(declare obj-position-x)  ; (obj-position-x buffer idx) → float
(declare obj-position-y)
(declare obj-position-z)
(declare obj-normal-x)
(declare obj-normal-y)
(declare obj-normal-z)
(declare obj-uv-u)
(declare obj-uv-v)
(declare obj-weights-0)
(declare obj-weights-1)
(declare obj-weights-2)
(declare obj-weights-3)
(declare obj-bone-ids-0)
(declare obj-bone-ids-1)
(declare obj-bone-ids-2)
(declare obj-bone-ids-3)

;; ═════════════════════════════════════════════════════════════
;; 单个分量写入 – 数组版本 (arr idx val)
;; ═════════════════════════════════════════════════════════════
(declare set-position-x!)
(declare set-position-y!)
(declare set-position-z!)
(declare set-normal-x!)
(declare set-normal-y!)
(declare set-normal-z!)
(declare set-uv-u!)
(declare set-uv-v!)
(declare set-weights-0!)
(declare set-weights-1!)
(declare set-weights-2!)
(declare set-weights-3!)
(declare set-bone-ids-0!)
(declare set-bone-ids-1!)
(declare set-bone-ids-2!)
(declare set-bone-ids-3!)

;; ═════════════════════════════════════════════════════════════
;; 单个分量写入 – obj 版本 (buffer idx val)
;; ═════════════════════════════════════════════════════════════
(declare obj-set-position-x!)
(declare obj-set-position-y!)
(declare obj-set-position-z!)
(declare obj-set-normal-x!)
(declare obj-set-normal-y!)
(declare obj-set-normal-z!)
(declare obj-set-uv-u!)
(declare obj-set-uv-v!)
(declare obj-set-weights-0!)
(declare obj-set-weights-1!)
(declare obj-set-weights-2!)
(declare obj-set-weights-3!)
(declare obj-set-bone-ids-0!)
(declare obj-set-bone-ids-1!)
(declare obj-set-bone-ids-2!)
(declare obj-set-bone-ids-3!)

;; ═════════════════════════════════════════════════════════════
;; 整字段读取 – 数组版本 (out arr idx)
;; ═════════════════════════════════════════════════════════════
(declare vertex)       ; 将 vertex (3 floats) 复制到 out
(declare normal)
(declare uv)           ; 将 uv (2 floats) 复制到 out
(declare weights)      ; 将 weights (4 floats) 复制到 out
(declare bone-ids)     ; 将 bone-ids (4 ints) 复制到 out

;; ═════════════════════════════════════════════════════════════
;; 整字段读取 – obj 版本 (out buffer idx)
;; ═════════════════════════════════════════════════════════════
(declare obj-vertex)
(declare obj-normal)
(declare obj-uv)
(declare obj-weights)
(declare obj-bone-ids)

;; ═════════════════════════════════════════════════════════════
;; 整字段写入 – 数组版本 (arr idx x y z ...)
;; ═════════════════════════════════════════════════════════════
(declare set-position!)   ; (set-position! arr idx x y z)
(declare set-normal!)
(declare set-uv!)       ; (set-uv! arr idx u v)
(declare set-weights!)  ; (set-weights! arr idx w0 w1 w2 w3)
(declare set-bone-ids!) ; (set-bone-ids! arr idx b0 b1 b2 b3)

;; ═════════════════════════════════════════════════════════════
;; 整字段写入 – obj 版本 (buffer idx x y z ...)
;; ═════════════════════════════════════════════════════════════
(declare obj-set-position!)
(declare obj-set-normal!)
(declare obj-set-uv!)
(declare obj-set-weights!)
(declare obj-set-bone-ids!)

(declare
  clone-vertex-buffer-polled
  dispose-polled-vertex-buffer)

;; 这个符号名称不能 declare
(deflayout VertexBuffer
           {:vertices [:float [:position [:x :y :z]
                               :normal [:x :y :z]
                               :uv    [:u :v]]]
            :weights     [:float [:weights 4]]
            :bone-indices [:int   [:boneIds 4]]}
           {:ext? true
            :unchecked-math? true}   ; 选项 map
           IVertexBuffer
           (copy [this] (clone-vertex-buffer-polled this))
           (dispose [this] (dispose-polled-vertex-buffer this)))

(defn make-vertex-buffer
  "构造 VertexBuffer。

   两 arity：
   - (make-vertex-buffer size & {:keys [skinned? ext]})
     按顶点数分配数组。skinned? 为真时同时分配 weights / bone-indices。
   - (make-vertex-buffer {:keys [vertices weights bone-indices ext]})
     从已有数组构造。"
  (^VertexBuffer [size & {:keys [skinned? ext]}]
   (let [vertices     (float-array (* size 8))
         weights      (if skinned? (float-array (* size 4)) nil)
         bone-indices (if skinned? (int-array (* size 4)) nil)]
     (VertexBuffer. vertices weights bone-indices ext)))
  (^VertexBuffer [{:keys [vertices weights bone-indices ext]}]
   (VertexBuffer. vertices weights bone-indices ext)))


(defn clone-vertex-buffer-polled
  "利用 FloatsPools / IntsPools 池分配新数组并复制数据。
   返回新的 VertexBuffer——ext 引用共享——上层保证 ext 不可变。"
  [^VertexBuffer buffer]
  (let [old-verts (.-vertices buffer)
        len-v     (alength old-verts)
        new-verts (-> ^FloatsPool (FloatsPools/getPool len-v) (.acquire))]
    (System/arraycopy old-verts 0 new-verts 0 len-v)

    (let [new-weights (when-let [old-w (.-weights buffer)]
                        (let [len-w (alength old-w)
                              new-w (-> ^FloatsPool (FloatsPools/getPool len-w) (.acquire))]
                          (System/arraycopy old-w 0 new-w 0 len-w)
                          new-w))

          new-bone    (when-let [old-b (.-bone-indices buffer)]
                        (let [len-b (alength old-b)
                              new-b (-> ^IntsPool (IntsPools/getPool len-b) (.acquire))]
                          (System/arraycopy old-b 0 new-b 0 len-b)
                          new-b))]
      (VertexBuffer. new-verts new-weights new-bone (.ext buffer)))))

;; ═════════════════════════════════════════════════════════════
;; 释放——归还池
;; ═════════════════════════════════════════════════════════════

(defn dispose-polled-vertex-buffer
  "将 VertexBuffer 中的 float 数组和 int 数组归还到对应的池中。"
  [^VertexBuffer buffer]
  (when-let [verts (.-vertices buffer)]
    (.release ^FloatsPool (FloatsPools/getPool (alength verts)) verts))
  (when-let [weights (.-weights buffer)]
    (.release ^FloatsPool (FloatsPools/getPool (alength weights)) weights))
  (when-let [bone-indices (.-bone-indices buffer)]
    (.release ^IntsPool (IntsPools/getPool (alength bone-indices)) bone-indices))
  nil)