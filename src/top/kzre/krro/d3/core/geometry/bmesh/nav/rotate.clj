(ns top.kzre.krro.d3.core.geometry.bmesh.nav.rotate
  "导航——绕顶点旋转。

   ══════════════════════════════════════════════════════════════
   命名约定
   ══════════════════════════════════════════════════════════════

     带 -manifold 后缀 —— 流形专用 —— 假设边流形 —— O(1)。
     无后缀          —— 通用     —— 无假设   —— O(径向链长)。

   两套并存——不是为了性能取舍——是体现从特殊到一般的拓展：

     流形版公式简洁——读一遍即懂'绕顶点旋转'的几何本质。
     通用版在其上处理非流形——读代码时能看清通用版多做了什么。
     调用方按数据特征直接选——不需要运行时判断。

   ══════════════════════════════════════════════════════════════
   流形假设（-manifold 版）
   ══════════════════════════════════════════════════════════════

     每条边的径向链长度 == 2 —— 即边恰好被两个面共享（或 1 个面 + 边界）。

   非流形（径向链长度 > 2 / 多扇顶点）——行为未定义。

   ══════════════════════════════════════════════════════════════
   流形版公式推导
   ══════════════════════════════════════════════════════════════

   设 l 是一条从 v 出发的 loop —— l.vert == v —— l.edge = (v, w)。

   next 方向：
     r = l.radial-next
       r.vert == v —— 对面同向 —— r 即下一条
       r.vert == w —— 对面反向 —— r.next 从 v 出发 —— 面内前进

     next(l) = l.radial-next               if r.vert == v
             = (l.radial-next).next        if r.vert == w

   prev 方向——反推：
     由 next(prev(l)) == l
     ⟹ (prev(l).radial-next).next = l
     ⟹ prev(l).radial-next = l.prev
     ⟹ prev(l) = (l.prev).radial-prev

   对称观察：
     next —— 先径向（跨边）—— 后面内
     prev —— 先面内 —— 后径向（跨边）

   ══════════════════════════════════════════════════════════════
   通用版扩展
   ══════════════════════════════════════════════════════════════

   非流形下——radial 链长度 > 2——单步 radial-next 不足——
   需沿径向链走一圈找绕 v 的 loop。
   若无——面内 fallback 跨到相邻边再查。

   边界（-1）——沿径向链绕回自身 / 旋转结果不从 v 出发。

   ══════════════════════════════════════════════════════════════
   全部只读——返回索引 / -1——不修改。
   ══════════════════════════════════════════════════════════════"
  (:require
    [top.kzre.krro.d3.core.geometry.bmesh.nav.step :as step])
  (:import
    (top.kzre.krro.d3.core.geometry.bmesh BMeshEditor)))

(set! *unchecked-math* true)

;; ═══════════════════════════════════════════════
;; 入口——中立（无流形假设）
;; ═══════════════════════════════════════════════

(defn loop-at-edge-vert
  "在边 e 上——找 .vert == v 的 loop。
   无——返回 -1。

   遍历 e 的径向链——所有在 e 上的 loop——找从 v 出发的那一条。
   流形假设——径向链长度 ≤ 2——O(1)。"
  ^long [^BMeshEditor editor ^long e ^long v]
  (let [start (long (step/edge-loop editor e))]
    (if (== start -1)
      -1
      (loop [l start]
        (cond
          (== (long (step/loop-vert editor l)) v)  l
          :else
          (let [rn (long (step/loop-radial-next editor l))]
            (if (or (== rn -1) (== rn start))
              -1
              (recur rn))))))))

(defn first-loop-from-vert
  "从顶点 v 出发——找一条绕 v 的 loop。
   孤立顶点——返回 -1。"
  ^long [^BMeshEditor editor ^long v]
  (let [e (long (step/vert-out-edge editor v))]
    (if (== e -1)
      -1
      (loop-at-edge-vert editor e v))))

;; ═══════════════════════════════════════════════
;; 流形专用——O(1)
;; ═══════════════════════════════════════════════

(defn next-loop-around-vert-manifold
  "绕 l.vert 的下一条 loop——流形专用——O(1)。

   公式：
     next(l) = l.radial-next               if r.vert == v
             = (l.radial-next).next        if r.vert == w

   边界——返回 -1。"
  ^long [^BMeshEditor editor ^long l]
  (let [v (long (step/loop-vert editor l))
        r (long (step/loop-radial-next editor l))]
    (cond
      (== r -1)  -1
      (== r l)   -1
      (== (long (step/loop-vert editor r)) v)  r
      :else  (step/loop-next editor r))))

(defn prev-loop-around-vert-manifold
  "绕 l.vert 的上一条 loop——流形专用——O(1)。

   公式：
     prev(l) = (l.prev).radial-prev

   边界——返回 -1。"
  ^long [^BMeshEditor editor ^long l]
  (let [v (long (step/loop-vert editor l))
        s (long (step/loop-prev editor l))
        r (long (step/loop-radial-prev editor s))]
    (cond
      (== r -1)  -1
      (== r s)   -1
      (== (long (step/loop-vert editor r)) v)  r
      :else  -1)))

;; ═══════════════════════════════════════════════
;; 通用——O(径向链长)
;; ═══════════════════════════════════════════════

(defn next-loop-around-vert
  "绕 l.vert 的下一条 loop——通用。

   对面同向——直接返回；
   对面反向——用对面 loop 的 .next 修正；
   修正失败——继续沿径向链。"
  ^long [^BMeshEditor editor ^long l]
  (let [v  (long (step/loop-vert editor l))
        r0 (long (step/loop-radial-next editor l))]
    (if (== r0 -1)
      -1
      (loop [r r0]
        (cond
          (== r l)  -1
          ;; 对面同向——直接
          (== (long (step/loop-vert editor r)) v)  r
          ;; 对面反向——r.next 修正
          :else
          (let [n (long (step/loop-next editor r))]
            (if (== (long (step/loop-vert editor n)) v)
              n
              ;; 修正失败——继续径向链
              (let [rn (long (step/loop-radial-next editor r))]
                (if (or (== rn -1) (== rn r0)) -1 (recur rn))))))))))

(defn prev-loop-around-vert
  "绕 l.vert 的上一条 loop——通用。

   先面内 .prev——再沿径向链走一圈找绕 v 的。

   非流形正确——复杂度 O(径向链长)。"
  ^long [^BMeshEditor editor ^long l]
  (let [v  (long (step/loop-vert editor l))
        s  (long (step/loop-prev editor l))
        r0 (long (step/loop-radial-prev editor s))]
    (loop [r r0]
      (cond
        (== r -1)  -1
        (== r s)   -1
        (== (long (step/loop-vert editor r)) v)  r
        :else  (recur (step/loop-radial-prev editor r))))))

(set! *unchecked-math* nil)