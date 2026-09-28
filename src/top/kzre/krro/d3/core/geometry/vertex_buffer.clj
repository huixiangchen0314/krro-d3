(ns top.kzre.krro.d3.core.geometry.vertex-buffer
  (:require
    [top.kzre.deflayout.core :refer [deflayout]])
  (:import
    (top.kzre.krro.d3.core.geometry IVertexBuffer)
    (top.kzre.krro.util.pool FloatsHolder FloatsPool
                             IntsHolder   IntsPool
                             PoolManagers)))

;; ═════════════════════════════════════════════════════════════
;; 声明——IDE 友好
;; ═════════════════════════════════════════════════════════════

;; 单个分量读取——数组版本
(declare position-x position-y position-z)
(declare normal-x normal-y normal-z)
(declare uv-u uv-v)
(declare weights-0 weights-1 weights-2 weights-3)
(declare bone-ids-0 bone-ids-1 bone-ids-2 bone-ids-3)

;; 单个分量读取——obj 版本
(declare obj-position-x obj-position-y obj-position-z)
(declare obj-normal-x obj-normal-y obj-normal-z)
(declare obj-uv-u obj-uv-v)
(declare obj-weights-0 obj-weights-1 obj-weights-2 obj-weights-3)
(declare obj-bone-ids-0 obj-bone-ids-1 obj-bone-ids-2 obj-bone-ids-3)

;; 单个分量写入——数组版本
(declare set-position-x! set-position-y! set-position-z!)
(declare set-normal-x! set-normal-y! set-normal-z!)
(declare set-uv-u! set-uv-v!)
(declare set-weights-0! set-weights-1! set-weights-2! set-weights-3!)
(declare set-bone-ids-0! set-bone-ids-1! set-bone-ids-2! set-bone-ids-3!)

;; 单个分量写入——obj 版本
(declare obj-set-position-x! obj-set-position-y! obj-set-position-z!)
(declare obj-set-normal-x! obj-set-normal-y! obj-set-normal-z!)
(declare obj-set-uv-u! obj-set-uv-v!)
(declare obj-set-weights-0! obj-set-weights-1! obj-set-weights-2! obj-set-weights-3!)
(declare obj-set-bone-ids-0! obj-set-bone-ids-1! obj-set-bone-ids-2! obj-set-bone-ids-3!)

;; 整字段读取——数组版本
(declare position normal uv weights bone-ids)

;; 整字段读取——obj 版本
(declare obj-position obj-normal obj-uv obj-weights obj-bone-ids)

;; 整字段写入——数组版本
(declare set-position! set-normal! set-uv! set-weights! set-bone-ids!)

;; 整字段写入——obj 版本
(declare obj-set-position! obj-set-normal! obj-set-uv! obj-set-weights! obj-set-bone-ids!)

(declare clone-vertex-buffer-polled dispose-polled-vertex-buffer)

;; ═════════════════════════════════════════════════════════════
;; 池——全局单例——走 PoolManagers
;; ═════════════════════════════════════════════════════════════

(def ^:private ^FloatsHolder floats-holder
  (.getHolder (PoolManagers/floats)))

(def ^:private ^IntsHolder ints-holder
  (.getHolder (PoolManagers/ints)))

;; ═════════════════════════════════════════════════════════════
;; 布局
;; ═════════════════════════════════════════════════════════════

(deflayout VertexBuffer
           {:vertices     [:float [:position [:x :y :z]
                                   :normal   [:x :y :z]
                                   :uv       [:u :v]]]
            :weights      [:float [:weights 4]]
            :bone-indices [:int   [:boneIds 4]]}
           {:ext?            true
            :unchecked-math? true}
           IVertexBuffer
           (copy    [this] (clone-vertex-buffer-polled this))
           (dispose [this] (dispose-polled-vertex-buffer this)))

;; ═════════════════════════════════════════════════════════════
;; 构造
;; ═════════════════════════════════════════════════════════════

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

;; ═════════════════════════════════════════════════════════════
;; 克隆——池分配
;; ═════════════════════════════════════════════════════════════

(defn clone-vertex-buffer-polled
  "利用全局池分配新数组并复制数据。
   返回新的 VertexBuffer——ext 引用共享——上层保证 ext 不可变。"
  [^VertexBuffer buffer]
  (let [old-verts (.-vertices buffer)
        len-v     (alength old-verts)
        new-verts (.acquire (.getPool floats-holder len-v))]
    (System/arraycopy old-verts 0 new-verts 0 len-v)

    (let [new-weights (when-let [old-w (.-weights buffer)]
                        (let [len-w (alength old-w)
                              new-w (.acquire (.getPool floats-holder len-w))]
                          (System/arraycopy old-w 0 new-w 0 len-w)
                          new-w))

          new-bone    (when-let [old-b (.-bone-indices buffer)]
                        (let [len-b (alength old-b)
                              new-b (.acquire (.getPool ints-holder len-b))]
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
    (.release (.getPool floats-holder (alength verts)) verts))
  (when-let [weights (.-weights buffer)]
    (.release (.getPool floats-holder (alength weights)) weights))
  (when-let [bone-indices (.-bone-indices buffer)]
    (.release (.getPool ints-holder (alength bone-indices)) bone-indices))
  nil)