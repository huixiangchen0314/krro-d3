(ns top.kzre.krro.d3.core.geometry.bmesh.nav.rotate
  "导航——绕顶点旋转。

   ══════════════════════════════════════════════════════════════
   命名约定
   ══════════════════════════════════════════════════════════════

     带 -manifold 后缀 —— 流形专用 —— 假设边流形 —— O(1)。
     无后缀          —— 通用     —— 无假设   —— O(径向链长)。

   只有 loop 旋转是真有两种公式——O(1) 流形快路径 / O(N) 通用。
   edge 旋转只是 loop 旋转的上层封装——公式唯一——不提供 -manifold 版。

   ══════════════════════════════════════════════════════════════
   入口的隐含约束
   ══════════════════════════════════════════════════════════════

   v.out-edge 指向的边——其上的 loop 不一定从 v 出发——
   面环方向可能让该边在 v 侧充当「入边」(w -> v)。

   loop-from-vert-on-edge 处理这一点：
     优先 —— e 上有从 v 出发的 loop —— 直接用。
     否则 —— e 上有从 w 出发的 loop lw —— lw.next 在面内前进 ——
             .vert == v —— 即绕 v 的合法出 loop。

   first-loop-from-vert 基于此 —— 保证从 v 出发。

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
   边旋转
   ══════════════════════════════════════════════════════════════

   next-edge-around-vert —— 绕 v 从边 e 出发的下一条边。

   情况 1 —— e 上有从 v 出发的 loop —— 用 loop 旋转。
   情况 2 —— e 上只有从 w 出发的 loop lw —— lw.next 从 v 出发 ——
             其 .edge 即答案。

   公式唯一——不提供 -manifold 版——内部用通用 loop 旋转。
   需要流形快路径——调用方自行组合
   loop-at-edge-vert + next-loop-around-vert-manifold。

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

(defn loop-from-vert-on-edge
  "在边 e 上——找一条从 v 出发的 loop（e 可能只是入口）。

   策略：
     1. e 上有从 v 出发的 loop —— 直接返回。
     2. e 上只有从 w 出发的 loop lw —— 取 lw.next（面内前进）——
        其 .vert 应为 v —— 返回它。
     3. 其他 —— 返回 -1。

   保证：只要 e 被 v 的某个面共享，就返回一条绕 v 的 loop。
   若 e 孤立（无 loop），返回 -1。"
  ^long [^BMeshEditor editor ^long e ^long v]
  (let [l (long (loop-at-edge-vert editor e v))]
    (if (not= l -1)
      l
      (let [w  (long (step/edge-other-vert editor e v))
            lw (long (loop-at-edge-vert editor e w))]
        (if (== lw -1)
          -1
          (let [nxt (long (step/loop-next editor lw))]
            (if (== (long (step/loop-vert editor nxt)) v)
              nxt
              -1)))))))

(defn first-loop-from-vert
  "从顶点 v 出发——找一条绕 v 的 loop。
   孤立顶点——返回 -1。

   从 v.out-edge 出发——loop-from-vert-on-edge 兜住方向不匹配。"
  ^long [^BMeshEditor editor ^long v]
  (let [e (long (step/vert-out-edge editor v))]
    (if (== e -1)
      -1
      (loop-from-vert-on-edge editor e v))))

;; ═══════════════════════════════════════════════
;; loop 旋转——流形专用——O(1)
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
;; loop 旋转——通用——O(径向链长)
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
          (== (long (step/loop-vert editor r)) v)  r
          :else
          (let [n (long (step/loop-next editor r))]
            (if (== (long (step/loop-vert editor n)) v)
              n
              (let [rn (long (step/loop-radial-next editor r))]
                (if (or (== rn -1) (== rn r0)) -1 (recur rn))))))))))

(defn prev-loop-around-vert
  "绕 l.vert 的上一条 loop——通用。

   先面内 .prev——再沿径向链走一圈找绕 v 的。"
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

;; ═══════════════════════════════════════════════
;; 边旋转——唯一版本（公式唯一）
;; ═══════════════════════════════════════════════

(defn next-edge-around-vert
  "绕 v 从边 e 出发的下一条边。

   情况 1 —— e 上有从 v 出发的 loop l —— 用 loop 旋转。
   情况 2 —— e 上只有从 w 出发的 loop lw —— lw.next 从 v 出发 ——
             其 .edge 即 e 的下一条边。

   无 —— 返回 -1。

   内部用通用 loop 旋转——不假设流形。
   需要流形快路径的调用方——自行组合
   loop-at-edge-vert + next-loop-around-vert-manifold。"
  ^long [^BMeshEditor editor ^long v ^long e]
  (let [l (long (loop-at-edge-vert editor e v))]
    (if (not= l -1)
      (let [nl (long (next-loop-around-vert editor l))]
        (if (== nl -1) -1 (step/loop-edge editor nl)))
      (let [w  (long (step/edge-other-vert editor e v))
            lw (long (loop-at-edge-vert editor e w))]
        (if (== lw -1)
          -1
          (let [nxt (long (step/loop-next editor lw))]
            (if (== (long (step/loop-vert editor nxt)) v)
              (long (step/loop-edge editor nxt))
              -1)))))))

(set! *unchecked-math* nil)