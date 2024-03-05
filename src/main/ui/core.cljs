(ns ui.core
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            ["@blueprintjs/core" :as bp :refer [Button InputGroup Card]]
            [ui.render-comp.chat :as rc :refer [main]]
            [ui.render-comp.bottom-bar :refer [bottom-bar-main]]
            [ui.utils :refer [p pp chat-ui-with-context-struct create-struct gen-new-uid get-block-parent-with-order common-chat-struct q llm-chat-settings-page-struct]]
            [reagent.dom :as rd]))



(defn log
  [& args]  (apply js/console.log args))

(defn create-new-block-with-id [parent-uid block-uid order string]
  (p "Create new *" string "* child block with uid:" block-uid " and parent uid:" parent-uid)
  (j/call-in js/window [:roamAlphaAPI :data :block :create]
    (clj->js {:location {:parent-uid parent-uid
                         :order       order}
              :block    {:uid    block-uid
                         :string string
                         :open   true}})))


(defn create-blocks [puid cb]
   (let [m-uid (j/call-in js/window [:roamAlphaAPI :util :generateUID])
         c-uid (j/call-in js/window [:roamAlphaAPI :util :generateUID])
         cc-uid (j/call-in js/window [:roamAlphaAPI :util :generateUID])]
     (-> (create-new-block-with-id puid m-uid 0 "Messages")
         (.then (fn [] (create-new-block-with-id puid c-uid 1 "Context")))
         (.then (fn [] (create-new-block-with-id c-uid cc-uid 0 "")))
         (.then (fn [] (j/call-in js/window [:roamAlphaAPI :data :block :update]
                                  (clj->js {:block    {:uid    puid
                                                       :open   false}}))))
         (.finally (fn [] (js/setTimeout cb 200))))))


(defn children-exist? [puid]
  (let [children (sort-by :order (:children
                                   (ffirst (q '[:find (pull ?e [:block/string :block/uid :block/order {:block/children ...}])
                                                :in $ ?uid
                                                :where
                                                [?e :block/uid ?uid]]


                                             puid))))
        messages? (= "Messages" (-> children first :string))
        context?  (= "Context" (-> children second :string))]
    (and messages? context?)))


(defn add-new-option-to-context-menu []
    (p "Add option to create chat block using context menu")
    (j/call-in js/window [:roamAlphaAPI :ui :blockContextMenu :addCommand]
      ;; Returns
      #_{:block-uid "8CskYJbhx"
         :page-uid "11-08-2023"
         :window-id "m0n15kMpYIaPLcMEchKcuWKdLAK2-body-outline-11-08-2023"
         :read-only? false
         :block-string ""
         :heading nil}
     (clj->js {:label "New LLM chat"
               :display-conditional (fn [e]
                                      true)
               :callback (fn [e]
                           (let [block-uid (j/get e :block-uid)
                                 chat-block-uid (gen-new-uid)
                                 [parent-uid
                                  block-order] (get-block-parent-with-order block-uid)]
                             (p "Using context menu option to create chat block with Parent uid: " parent-uid "Block order: " block-order)
                             (create-struct (chat-ui-with-context-struct chat-block-uid nil nil block-order)
                               parent-uid
                               chat-block-uid
                               false
                               (p "Created chat block using context menu option, chat block uid" chat-block-uid))))})))


(defn extract-last-substring [s]
  (if (>= (count s) 9)
    (subs s (- (count s) 9))
    s))

(defn load-ui [node]
  (let [dom-id (-> (j/call node :closest "div") (j/get :id))
        pbuid (and (not (empty? dom-id)) (extract-last-substring dom-id))]
    (when pbuid
      (p "Load plugin ui for block with uid: " pbuid)
      (let [children-exist? (children-exist? pbuid)]
        (p "Does this {{ chat-llm }} block have Context, Messages and Chat as children?: " children-exist?)
        (main {:block-uid pbuid} "filler" dom-id)
        #_(if children-exist?
            (main {:block-uid pbuid} "filler" dom-id)
            (do
             (p "Seems like the block was created by directly typing in {{ chat-llm }}, so creating children for the plugin. ")
             (create-blocks pbuid #(rc/main {:block-uid pbuid} "filler" dom-id))))))))


(defn get-matches [d class-name tag-name]
  (let [matches (->> (.getElementsByClassName  d class-name)
                    array-seq
                    (filter #(and
                               (= (j/get % :nodeName) tag-name)
                               (= "chat-llm" (j/get % :innerText)))))]
    matches))

(defn mutation-callback [mutations observer]
  (doseq [mutation mutations]
    (when (= (.-type mutation) "childList")
      (doseq [node (array-seq (.-addedNodes mutation))]
        (when (instance? js/Element node)
          (doseq [match (get-matches node "bp3-button" "BUTTON")]
            (js/setTimeout
             (fn [] (load-ui match))
             200)))))))

(defn start-observing []
  (let [observer (js/MutationObserver. mutation-callback)]
    (.observe observer js/document #js {:childList true
                                        :subtree true})))

(defn setup []
  (p "Load the plugin UI for each chat-llm roam render button i.e all blocks with text: `{{ chat-llm }}` ")
  (doseq [match (get-matches js/document "bp3-button" "BUTTON")]
    (load-ui match)))


(defn init []
 (p "Hello from  chat-llm! PROD v-ff380d")
 (p "Starting initial setup.")
 (llm-chat-settings-page-struct)
 ;(append-and-render-component)
  ;; check if the dom already has a chat-llm button, if so render for them
 (setup)
  ;; observe for new chat-llm buttons
 (start-observing)
  ;; a way to add the chat-llm button
 (add-new-option-to-context-menu)
 (bottom-bar-main)
 (p "Finished initial setup."))

