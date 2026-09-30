(ns top.kzre.krro.d3.core.geometry.bmesh.vert
  "BMesh 顶点——属性布局 + 拓扑连接。

   顶点 = 三维空间中的一个点——带一条出边索引。

   ── 属性（BMVertAttrs）──────────────────────────
     - position   位置 x y z
                  stride = 3

   ── 连接（BMVertConn）──────────────────────────
     - out-edge   一条出边的索引——从本顶点出发遍历邻接
                  孤立顶点 = -1
                  stride = 1

   顶点身份 = 数组下标——非负整数。
   布局由 deflayout 管理——逻辑代码不硬编码下标。

   对应 BMesh 字段：
     vertPositions  ← BMVertAttrs
     vertOutEdges   ← BMVertConn"
  (:require
    [top.kzre.deflayout.core :refer [deflayout]]))

;; ═══════════════════════════════════════════════
;; 声明
;; ═══════════════════════════════════════════════

;; BMVertAttrs
(declare position-x position-y position-z)
(declare set-position! set-position-x! set-position-y! set-position-z!)
(declare obj-position-x obj-position-y obj-position-z)
(declare obj-set-position-x! obj-set-position-y! obj-set-position-z!)

;; BMVertConn
(declare out-edge-idx set-out-edge-idx!)
(declare obj-out-edge-idx obj-set-out-edge-idx!)

;; ═══════════════════════════════════════════════
;; 属性
;; ═══════════════════════════════════════════════

(deflayout BMVertAttrs
           {:data [:float [:position [:x :y :z]]]}
           {:unchecked-math? true})

;; ═══════════════════════════════════════════════
;; 连接
;; ═══════════════════════════════════════════════

(deflayout BMVertConn
           {:conn [:int [:out-edge [:idx]]]}
           {:unchecked-math? true})