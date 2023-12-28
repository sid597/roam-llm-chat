(ns ui.graph-overview-ai.client
  (:require
    [applied-science.js-interop :as j]
    [ui.extract-data :as ed :refer [q]]
    [clojure.set :as set]
    ["@blueprintjs/core" :as bp :refer [Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]))


(defn is-discourse-node [s]
  ;(println "-->" s)
  (let [node ["QUE" "CLM" "EVD" "RES" "ISS" "HYP" "EXP" "CON"]
        node-regex (re-pattern (str "\\[\\[" (clojure.string/join "\\]\\]|\\[\\[" node) "\\]\\]"))
        src-regex (re-pattern "^@[^\\s]+")]
    (if (or (re-find node-regex s)
          (re-find src-regex s))
      s
      nil)))

;(is-discourse-node "a [QUE]] - Is this a question?")
;(is-discourse-node "[[QUE]] - How do Hip1R binding kinetics and periodicity affect endocytic actin architecture and integrity?")


(defn generate-query-pattern [depth]
  (let [queries (concat
                  [[(symbol  "?e"  ) :node/title (symbol (str "?page"))]]
                  (mapcat (fn [d]
                            (let [base-index (- d 1)]
                              [[(symbol (str "?d" base-index)) :block/refs (symbol
                                                                             (if (= 0 base-index)
                                                                               "?e"
                                                                               (str "?r"  (- base-index 1))))]
                               [(symbol (str "?d" base-index)) :block/parents (symbol (str "?r" base-index))]]))
                    (range 1 (inc depth)))
                  [[(symbol (str "?r" (- depth 1) )) :node/title (symbol (str "?title"))]])]
    (pr-str (vec (concat [:find (symbol (str "?title" )) :in '$ '?page :where]
                   queries)))))

(generate-query-pattern 2)

(defn get-in-refs [page-name depth]
  (let [qry-res  (q
                   (generate-query-pattern depth)
                   page-name)
        filtered-discourse-nodes (reduce (fn [acc x]
                                           (if (some? (is-discourse-node (first x)))
                                             (conj acc (first x))
                                             acc))
                                   #{}
                                   qry-res)]
    filtered-discourse-nodes))


(get-in-refs
  "[[QUE]] - How does frequency of Arp2/3 complex binding to actin filaments affect endocytic actin architecture and integrity?"
  3)


(defn get-explorer-pages []
  (println "get explorer pages")
  (let [page-name  (js->clj (j/call-in js/window [:roamAlphaAPI :ui :graphView :wholeGraph :getExplorePages])
                     :keywordize-keys true)
        degrees    (->> (j/call js/document :querySelectorAll ".bp3-slider-handle > .bp3-slider-label")
                        (map (fn [x]
                              (println "x" x)
                              (j/get x :textContent))))]

    (println "result from exploare pages" page-name degrees)))



;; Button only appears when we are on the graph-view page
(defn toolbar-button [icon callback]
  (println "toolbar button")
  (js/console.log "rm-topbar?" (j/call js/document :getElementsByClassName  "rm-topbar")
    (.getElementsByClassName js/document "rm-graph-view"))
  (fn [_ _]
   (when (j/call js/document :getElementsByClassName "rm-graph-view")
    (println "toolbar button")
    [:> Button {:class-name "sp"
                :icon icon
                :minimal true
                :small true
                :fill true
                :style {:height "10px"
                        :border "none"}
                :on-click (fn []
                            (get-explorer-pages))}


     "AI"])))


(defn dialog-box [])


(defn sm [a b]
  (+ a b))


(sm 1 3)
