(ns top.kzre.krro.d3.core.renderer.core
  (:import (top.kzre.krro.d3.core.renderer Pass)))

(defmulti pipeline
          "合成管线：按 renderer 分发，返回 {:passes [Pass ...]}。
           passes 为有序 pass 序列，顺序即执行顺序。
           参数抽象由各实现自行约定，本层只做分发。"
          (fn
            [{:keys [renderer]} & _more]
            renderer))

(defmulti render-pass
          "执行单个 pass，返回新的累积状态。
           约定：
             - acc 为上一 pass 的返回值；序列首个 pass 收到 nil。
               acc 的具体形状由 pass 自行约定，本层不解释。
             - params 为外部配置（资源池、选项等），原样透传，本层不解释。
             - 绘制目标与资源使用由 pass 自行决定。"
          (fn [^Pass pass & _more]
            (.getId pass)))

(defn render
  "执行合成管线：pipeline 产出 pass 序列，按序 fold 执行。
   每个 pass 收到 (pass acc params)，返回新 acc 传给下一 pass；
   初始 acc 为 nil。最终 acc 的 :canvas 字段作为返回值。
   acc 与 params 的具体内容由 pipeline 与各 pass 自行约定，
   本层只提供流程与约定，不解释其结构。"
  ([init-acc params]
   (let [{:keys [passes]} (pipeline params)
         {:keys [canvas]}
         (reduce
           (fn [acc pass]
             (let [result (render-pass pass acc params)]
               result))
           init-acc
           passes)]
     canvas))
  ([params] (render nil params)))