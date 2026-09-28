(ns top.kzre.krro.d3.core.geometry.bmesh.edge
  "BMesh 边——拓扑连接。

   ── 连接（BMEdgeConn）──────────────────────────
     - v0       端点 0——约定：loop.vert 为 v0 时，loop.next.vert 为 v1
     - v1       端点 1
     - loop     径向环入口——-1 表示孤立边

   允许非流形：径向环是双向链表，不限于两项。
   边身份 = 数组下标——非负整数。

   当前无边特有属性。将来若需要（crease / bevel-weight / sharp），
   在本命名空间新增 BMEdgeAttrs deflayout——不改连接。"
  (:require
    [top.kzre.deflayout.core :refer [deflayout]]))

;; ═══════════════════════════════════════════════
;; 声明
;; ═══════════════════════════════════════════════

(declare v0-idx v1-idx loop-idx)
(declare set-v0-idx! set-v1-idx! set-loop-idx!)
(declare obj-v0-idx obj-v1-idx obj-loop-idx)
(declare obj-set-v0-idx! obj-set-v1-idx! obj-set-loop-idx!)

;; ═══════════════════════════════════════════════
;; 连接
;; ═══════════════════════════════════════════════

(deflayout BMEdgeConn
           {:conn [:int
                   [:v0   [:idx]
                    :v1   [:idx]
                    :loop [:idx]]]}
           {:unchecked-math? true})