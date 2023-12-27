(ns ui.graph-overview-ai.client
  (:require
    [applied-science.js-interop :as j]
    [ui.extract-data :as ed :refer [q]]
    [clojure.set :as set]
    [cljs.core.async.interop :as asy :refer [<p!]]
    [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]
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

(is-discourse-node "a [QUE]] - Is this a question?")

(defn find-block-parent [uids]
  (println "find block parent" uids)
  (let [parent (reduce
                 (fn [acc uid]
                   (let [title (ffirst (q '[:find ?title
                                             :in $ ?uid
                                             :where
                                             [?e :block/uid ?uid]
                                             [?e :block/parents ?r]
                                             [?r :node/title ?title]]
                                          (:uid uid)))]
                     (if (some? (is-discourse-node title))
                       (conj acc title)
                       acc)))
                 []
                 uids)]
    parent))

(find-block-parent ["vJNRzQ5KB"])

(defn query-in-refs [page]
  (->> (q '[:find ?title
            :in $ ?page
            :where
            [?e :node/title ?page]
            [?d :block/refs ?e]
            [?d :block/parents ?r]
            [?r :node/title ?title]]
         page)
    (map first)
    (set)))


(defn query-in-refs-dg [page]
  (->> (q '[:find ?title
            :in $ ?page
            :where
            [?e :node/title ?page]
            [?d :block/refs ?e]
            [?d :block/parents ?r]
            [?r :node/title ?title]]
         page)
    (map first)
    (set)
    (reduce
      (fn [acc x]
        (if (some? (is-discourse-node x))
          (conj acc x)
          acc))
      #{})))


(defn bfs-level [current-level visited depth]
  (if (or (empty? current-level) (<= depth 0))
    visited
    (let [next-level  (apply clojure.set/union
                        (map #(do
                                (println "-->"  %)
                                (set (query-in-refs %)))
                          current-level))
          next-visited (clojure.set/union visited current-level)]
      (println "====================================")
      (cljs.pprint/pprint next-level)
      (println "====================================")
      (bfs-level next-level next-visited (dec depth)))))

(defn get-refs [page-name depth]
  (let [qry-ref       (query-in-refs page-name)
        starting-level (set  qry-ref)]
    (println "qry-ref" qry-ref)
    (cljs.pprint/pprint  starting-level)
    (bfs-level starting-level #{} (dec depth))
    #_(map #(vector % %) (bfs-level starting-level #{} (dec depth)))))

(query-in-refs-dg "[[QUE]] - How does frequency of Arp2/3 complex binding to actin filaments affect endocytic actin architecture and integrity?")

(get-refs "[[QUE]] - How does frequency of Arp2/3 complex binding to actin filaments affect endocytic actin architecture and integrity?"
  2)


#_(defn get-refs [page-name node]
    (let [node-regex (re-pattern (str "\\[\\[" (clojure.string/join "\\]\\]|\\[\\[" node) "\\]\\]"))
          src-regex (re-pattern "^@[^\\s]+")]
      (q '[:find ?title
           :in $ ?page ?node-regex ?src-regex
           :where
           [?e :node/title ?page]
           [?d :block/refs ?e]
           [?d :block/parents ?r]
           [?r :node/title ?title]]
        page-name node-regex src-regex)))



(defn pull-refs-in [page-name depth]
  (let [x-ref-y  (atom {})
        discourse-nodes (-> (ffirst (q '[:find (pull ?e [:block/uid {:block/_refs 1}])
                                         :in $ ?page
                                         :where
                                         [?e :node/title  ?page]]
                                      page-name))
                          :_refs
                          find-block-parent)]
    discourse-nodes))

(pull-refs-in "[[QUE]] - How does frequency of Arp2/3 complex binding to actin filaments affect endocytic actin architecture and integrity?"
  1)


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


