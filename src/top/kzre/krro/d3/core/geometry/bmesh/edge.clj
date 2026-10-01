(ns top.kzre.krro.d3.core.geometry.bmesh.edge
  "BMesh 边——属性布局 + 拓扑连接。

   边 = 两个顶点之间的一条线段——带一个径向环入口。

   ── 属性（BMEdgeAttrs）──────────────────────────
     当前为空——扩展点。
     stride = 0

   ── 连接（BMEdgeEndpoints）──────────────────────
     - v0       端点 0
     - v1       端点 1
                约定：loop.vert 为 v0 时——loop.next.vert 为 v1
                stride = 2

   ── 连接（BMEdgeLoops）──────────────────────────
     - loop     径向环入口——-1 表示孤立边
                允许非流形：径向环是双向链表——不限于两项
                stride = 1

   ── 连接（BMEdgeDiskRing）───────────────────────
     - v0-ring-next / v0-ring-prev   边在 v0 周围磁盘环的前后
     - v1-ring-next / v1-ring-prev   边在 v1 周围磁盘环的前后
                单元素环：next == prev == self
                孤立边：  全 -1
                与 Blender 的 edge-disk-link 语义一致
                stride = 4

   边身份 = 数组下标——非负整数。

   对应 BMesh 字段：
     edgeEndpoints  ← BMEdgeEndpoints（v0 / v1）
     edgeLoops      ← BMEdgeLoops（径向环入口）
     edgeDiskRing   ← BMEdgeDiskRing（v0 / v1 磁盘环）"
  (:refer-clojure :exclude [loop])
  (:require
    [top.kzre.deflayout.core :refer [deflayout]]))

;; ═══════════════════════════════════════════════
;; 声明
;; ═══════════════════════════════════════════════

;; BMEdgeAttrs —— 空——扩展点

;; BMEdgeEndpoints
(declare v0-idx v1-idx)
(declare set-v0-idx! set-v1-idx!)
(declare obj-v0-idx obj-v1-idx)
(declare obj-set-v0-idx! obj-set-v1-idx!)

;; BMEdgeLoops
(declare loop-idx set-loop-idx!)
(declare obj-loop-idx obj-set-loop-idx!)

;; BMEdgeDiskRing
(declare v0-ring-next v0-ring-prev v1-ring-next v1-ring-prev)
(declare set-v0-ring-next! set-v0-ring-prev!
  set-v1-ring-next! set-v1-ring-prev!)
(declare obj-v0-ring-next obj-v0-ring-prev
  obj-v1-ring-next obj-v1-ring-prev)
(declare obj-set-v0-ring-next! obj-set-v0-ring-prev!
  obj-set-v1-ring-next! obj-set-v1-ring-prev!)

;; ═══════════════════════════════════════════════
;; 属性——空——扩展点
;; ═══════════════════════════════════════════════

(deflayout BMEdgeAttrs
           {:data [:float []]}
           {:unchecked-math? true})

;; ═══════════════════════════════════════════════
;; 连接——端点
;; ═══════════════════════════════════════════════

(deflayout BMEdgeEndpoints
           {:conn [:int
                   [:v0 [:idx]
                    :v1 [:idx]]]}
           {:unchecked-math? true})

;; ═══════════════════════════════════════════════
;; 连接——径向环入口
;; ═══════════════════════════════════════════════

(deflayout BMEdgeLoops
           {:conn [:int [:loop [:idx]]]}
           {:unchecked-math? true})

;; ═══════════════════════════════════════════════
;; 连接——磁盘环（绕两端的出边双向环）
;; ═══════════════════════════════════════════════

(deflayout BMEdgeDiskRing
           {:conn [:int
                   [:v0-ring [:next :prev]
                    :v1-ring [:next :prev]]]}
           {:unchecked-math? true})