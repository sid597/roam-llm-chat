(ns ui.graph-overview-ai.client
  (:require
    [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]
    [cljs.core.async.interop :as asy :refer [<p!]]
    [applied-science.js-interop :as j]
    [ui.extract-data :as ed :refer [q]]
    [ui.render-comp :as rc :refer [create-new-block]]
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
  1)


(defn get-explorer-pages []
  (println "get explorer pages")
  (let [page-name (str (js->clj (first (j/call-in js/window [:roamAlphaAPI :ui :graphView :wholeGraph :getExplorePages]))))
        [in out]  (->> (j/call js/document :querySelectorAll ".bp3-slider-handle > .bp3-slider-label")
                       (map (fn [x]
                             (j/get x :textContent))))
        in-pages (get-in-refs page-name  (js/parseInt in))]
    (println "page-name" page-name in)
    (println "in-pages")
    in-pages))



;; Button only appears when we are on the graph-view page



(defn dialog-box [])

(defn create-new-block-with-id [{:keys [parent-uid block-uid order string callback open]}]
  (println "create new block" parent-uid)
  (-> (j/call-in js/window [:roamAlphaAPI :data :block :create]
        (clj->js {:location {:parent-uid parent-uid
                             :order       order}
                  :block    {:uid    block-uid
                             :string string
                             :open   open}}))
    (.then (fn []
              callback))))

(defn create-struct [struct top-parent chat-block-uid]
  (let [stack (atom [struct])
        res   (atom [top-parent])]
    (go
     (while (not-empty @stack)
      (let [cur (first @stack)
            {:keys [u s o op]} cur
            new-uid (j/call-in js/window [:roamAlphaAPI :util :generateUID])
            parent (first @res)
            args {:parent-uid parent
                  :block-uid  (if (some? u) u new-uid)
                  :order      (if (some? o) o "last")
                  :string     s
                  :open      (if (some? op) op true)
                  :callback   (println "callback")}]
        (swap! stack rest)
        (swap! stack #(vec (concat % (:c cur))))
        ;(println "block-" string "-parent-" parent #_(first @res))
        (<p! (create-new-block-with-id args))
        (cljs.pprint/pprint  args)
        (swap! res rest)
        (swap! res #(vec (concat % (vec (repeat (count (:c cur))
                                          (if (some? (:u cur))
                                           (:u cur)
                                           new-uid))))))))
     (<p! (-> (j/call-in js/window [:roamAlphaAPI :ui :rightSidebar :addWindow]
                (clj->js {:window {:type "block"
                                   :block-uid chat-block-uid
                                   :order 0}}))
            (.then (fn []
                    (do
                     (println "window added to right sidebar")
                     (j/call-in js/window [:roamAlphaAPI :ui :rightSidebar :open])))))))))

#_(extract-struct
    {:s "AI chats"
     :c [{:s "{{ chat-llm }}"
          :c [{:s "Messages"}
              {:s "Context"}]}]}
    "8yCGreTXI")

(defn add-pages-to-context []
  (println "add pages to context"))


(add-pages-to-context)

(defn create-chat-ui-blocks-for-selected-overview [chat-block-uid context-block-uid]
  (let [todays-uid  (->> (js/Date.)
                      (j/call-in js/window [:roamAlphaAPI :util :dateToPageUid]))
        ai-block (ffirst
                   (q '[:find ?uid
                        :in $ ?today
                        :where
                        [?e :block/uid ?today]
                        [?e :block/children ?c]
                        [?c :block/string "AI chats"]
                        [?c :block/uid ?uid]]
                     "G4gWzpp6q"
                     #_todays-uid))]
    (if (some? ai-block)
      (create-struct
        {:s "{{ chat-llm }}"
         :op false
         :u chat-block-uid
         :c [{:s "Messages"}
             {:s "Context"
              :c [{:s (str
                        "``` \n "
                        "Contents of the following pages will be extracted here: \n \n"
                        (clojure.string/join "\n" (get-explorer-pages))
                        " \n ```")}]
              :u context-block-uid}]}
        ai-block
        chat-block-uid)
      (create-struct
        {:s "AI chats"
         :c [{:s "{{ chat-llm }}"
              :op false
              :u chat-block-uid
              :c [{:s "Messages"}
                  {:s "Context"
                   :u context-block-uid
                   :c [{:s (str
                             "``` \n "
                             "Contents of the following pages will be extracted here: \n \n"
                             (clojure.string/join "\n" (get-explorer-pages))
                             " \n ```")}]}]}]}
        "G4gWzpp6q"
        chat-block-uid))))


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
                              (let [chat-block-uid (j/call-in js/window [:roamAlphaAPI :util :generateUID])
                                    context-block-uid (j/call-in js/window [:roamAlphaAPI :util :generateUID])]
                                (println "chat-block-uid" chat-block-uid context-block-uid)
                                (create-chat-ui-blocks-for-selected-overview
                                   chat-block-uid
                                   context-block-uid)))}
       "AI"])))
