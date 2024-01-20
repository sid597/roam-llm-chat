(ns ui.graph-overview-ai.client
  (:require
    [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]
    [applied-science.js-interop :as j]
    [ui.utils :refer [q create-new-block create-new-block-with-id get-todays-uid create-struct get-explorer-pages]]
    ["@blueprintjs/core" :as bp :refer [Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]))



;; Button only appears when we are on the graph-view page



(defn dialog-box [])



(defn create-chat-ui-blocks-for-selected-overview [chat-block-uid context-block-uid]
  (let [ai-block (ffirst
                   (q '[:find ?uid
                        :in $ ?today
                        :where
                        [?e :block/uid ?today]
                        [?e :block/children ?c]
                        [?c :block/string "AI chats"]
                        [?c :block/uid ?uid]]
                     "G4gWzpp6q"
                     #_(get-todays-uid)))]
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
        chat-block-uid
        true)
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
        chat-block-uid
        true))))


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
