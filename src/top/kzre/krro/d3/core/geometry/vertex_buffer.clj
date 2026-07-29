(ns top.kzre.krro.d3.core.geometry.vertex-buffer
  (:require
   [top.kzre.deflayout.core :refer [deflayout]]))


(deflayout VertexBuffer
           {:vertices [:float [:vertex [:x :y :z]
                               :normal [:x :y :z]
                               :uv    [:u :v]]]
            :weights     [:float [:weights 4]]
            :bone-indices [:int   [:boneIds 4]]}
           :ext? true
           :unchecked-math? true)

(defn make-vertex-buffer
  (^VertexBuffer [size & {:keys [skinned? ext]}]
   (let [vertices (float-array (size * 8))
         weights (if skinned? (float-array (size * 4)) nil)
         bone-indices (if skinned? (int-array (size * 4)) nil)]
     (make-vertex-buffer vertices weights bone-indices ext)))
  (^VertexBuffer [{:keys [vertices weights bone-indices ext]}]
   (VertexBuffer. vertices weights bone-indices ext)))



;; ═════════════════════════════════════════════════════════════
;; 单个分量读取 – 数组版本 (arr idx)
;; ═════════════════════════════════════════════════════════════
(declare vertex-x)      ; (vertex-x arr idx) → float
(declare vertex-y)
(declare vertex-z)
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
(declare obj-vertex-x)  ; (obj-vertex-x buffer idx) → float
(declare obj-vertex-y)
(declare obj-vertex-z)
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
(declare set-vertex-x!)
(declare set-vertex-y!)
(declare set-vertex-z!)
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
(declare obj-set-vertex-x!)
(declare obj-set-vertex-y!)
(declare obj-set-vertex-z!)
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
(declare set-vertex!)   ; (set-vertex! arr idx x y z)
(declare set-normal!)
(declare set-uv!)       ; (set-uv! arr idx u v)
(declare set-weights!)  ; (set-weights! arr idx w0 w1 w2 w3)
(declare set-bone-ids!) ; (set-bone-ids! arr idx b0 b1 b2 b3)

;; ═════════════════════════════════════════════════════════════
;; 整字段写入 – obj 版本 (buffer idx x y z ...)
;; ═════════════════════════════════════════════════════════════
(declare obj-set-vertex!)
(declare obj-set-normal!)
(declare obj-set-uv!)
(declare obj-set-weights!)
(declare obj-set-bone-ids!)