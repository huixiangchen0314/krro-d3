(ns top.kzre.krro.d3.core.geometry.bmesh.ops.link
  "BMesh 连接原语——建立 / 断开元素间的关系。

   无流形假设——纯字段写入 + 双向链维护。

   ── 双向链（成对设置——保证一致性）──
     link-loop-next!        l1.next = l2 且 l2.prev = l1
     unlink-loop-next!      断开 l1 与 l1.next
     link-loop-radial!      l1.radialNext = l2 且 l2.radialPrev = l1
     unlink-loop-radial!    断开 l1 与 l1.radialNext

   ── 单向连接（语义封装——值类型参数）──
     set-vert-out-edge!     顶点出边
     set-edge-loop!         边径向入口
     set-loop-ownership!    环归属——值类型
     set-edge-endpoints!    边端点——值类型
     set-face-topology!     面拓扑——值类型

   契约：
     全部只碰 access 的写——不检查邻接——不做遍历。
     双向链——两字段同时设置——中间状态不可见。
     调用方保证索引有效。
     -1 作为哨兵——unlink 遇 -1 无操作。"
  (:require
    [top.kzre.krro.d3.core.geometry.bmesh.access :as access])
  (:import
    (top.kzre.krro.d3.core.geometry.bmesh BMeshEditor)
    (top.kzre.krro.d3.core.geometry.bmesh
      EdgeEndpoints LoopOwnership FaceTopology)))

(set! *unchecked-math* true)

;; ═══════════════════════════════════════════════
;; 双向链——面内环
;; ═══════════════════════════════════════════════

(defn link-loop-next!
  "连接 l1.next = l2 且 l2.prev = l1。"
  [^BMeshEditor editor ^long l1 ^long l2]
  (access/set-loop-next! editor l1 l2)
  (access/set-loop-prev! editor l2 l1))

(defn unlink-loop-next!
  "断开 l1 与其 next 的连接。
   l1 == -1 或 l1.next == -1 —— 无操作。"
  [^BMeshEditor editor ^long l1]
  (when (not= l1 -1)
    (let [l2 (long (access/get-loop-next editor l1))]
      (when (not= l2 -1)
        (access/set-loop-next! editor l1 -1)
        (access/set-loop-prev! editor l2 -1)))))

;; ═══════════════════════════════════════════════
;; 双向链——径向环
;; ═══════════════════════════════════════════════

(defn link-loop-radial!
  "连接 l1.radialNext = l2 且 l2.radialPrev = l1。"
  [^BMeshEditor editor ^long l1 ^long l2]
  (access/set-loop-radial-next! editor l1 l2)
  (access/set-loop-radial-prev! editor l2 l1))

(defn unlink-loop-radial!
  "断开 l1 与其 radialNext 的连接。
   l1 == -1 或 l1.radialNext == -1 —— 无操作。"
  [^BMeshEditor editor ^long l1]
  (when (not= l1 -1)
    (let [l2 (long (access/get-loop-radial-next editor l1))]
      (when (not= l2 -1)
        (access/set-loop-radial-next! editor l1 -1)
        (access/set-loop-radial-prev! editor l2 -1)))))

;; ═══════════════════════════════════════════════
;; 单向连接——值类型参数
;; ═══════════════════════════════════════════════

(defn set-vert-out-edge!
  "设置顶点 v 的出边 e。"
  [^BMeshEditor editor ^long v ^long e]
  (access/set-vert-out-edge! editor v e))

(defn set-edge-loop!
  "设置边 e 的径向环入口 l。"
  [^BMeshEditor editor ^long e ^long l]
  (access/set-edge-loop! editor e l))

(defn set-loop-ownership!
  "设置环 l 的归属。"
  [^BMeshEditor editor ^long l ^LoopOwnership o]
  (access/set-loop-ownership! editor l o))

(defn set-edge-endpoints!
  "设置边 e 的两端点。"
  [^BMeshEditor editor ^long e ^EdgeEndpoints p]
  (access/set-edge-endpoints! editor e p))

(defn set-face-topology!
  "设置面 f 的拓扑。"
  [^BMeshEditor editor ^long f ^FaceTopology t]
  (access/set-face-topology! editor f t))

(set! *unchecked-math* nil)