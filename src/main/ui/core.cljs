(ns ui.core
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            ["@blueprintjs/core" :as bp :refer [Button InputGroup Card]]
            [ui.render-comp.persistent-chat :refer [persistent-chat-ui]]
            [ui.render-comp.chat :as rc :refer [main]]
            [ui.render-comp.bottom-bar :refer [bottom-bar-main]]
            [ui.utils :refer [p chat-ui-with-context-struct create-struct gen-new-uid get-block-parent-with-order common-chat-struct q llm-chat-settings-page-struct]]
            [ui.render-comp.discourse-suggestions :refer [llm-dg-suggestions-main]]
            [ui.components.cytoscape :refer [cytoscape-main]]
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
  (p "load ui for chat-llm button: ")
  (js/console.log node)
  (let [dom-id (-> (j/call node :closest "div") (j/get :id))
        pbuid (and (not (empty? dom-id)) (extract-last-substring dom-id))]
    (when pbuid
      (p "Load plugin ui for block with uid: " pbuid)
      (let [inner-text (j/get node :innerText)]
       (case inner-text
         "chat-llm"           (main {:block-uid pbuid} "filler" dom-id)
         "llm-dg-suggestions" (llm-dg-suggestions-main  pbuid  dom-id)
         "visualise-suggestions"          (cytoscape-main  pbuid dom-id))))))


(defn get-matches [d class-name tag-name]
  (let [matches (->> (.getElementsByClassName  d class-name)
                    array-seq
                    (filter #(and
                              (= (j/get % :nodeName) tag-name)
                              (some #{(j/get % :innerText)} ["chat-llm" "llm-dg-suggestions" "visualise-suggestions"]))))]
    matches))

(defn mutation-callback [mutations observer]
  (doseq [mutation mutations]
    (when (= (.-type mutation) "childList")
      (doseq [node (array-seq (.-addedNodes mutation))]
        (when (instance? js/Element node)
          (doseq [match (get-matches node "bp3-button" "BUTTON")]
            (js/setTimeout
                (fn []
                  (do
                    (p "Set Timeout: New chat-llm button candidate found: ")
                    (js/console.log match)
                    (load-ui match)))
             500)))))))

(defn start-observing []
  (let [observer (js/MutationObserver. mutation-callback)]
    (.observe observer js/document #js {:childList true
                                        :subtree true})))

(defn setup []
  (p "Load the plugin UI for each chat-llm roam render button i.e all blocks with text: `{{ chat-llm }}` ")
  (doseq [match (get-matches js/document "bp3-button" "BUTTON")]
    (p "Initialising setup for matching buttons")
    (load-ui match)))

#_(defn render-persistent []
   (let [parent-el (.querySelector js/document ".roam-body-main")
         new-child (.createElement js/document "div")]
     (set! (.-className new-child) "persistent-chat")
     (.appendChild parent-el new-child)
     (rd/render [persistent-chat-ui] new-child)))




(defn render-persistent []
  (let [parent-el (.querySelector js/document ".roam-body-main")
        article-wrapper (.querySelector parent-el ".rm-article-wrapper")
        new-child (.createElement js/document "div")]

    (when article-wrapper
      (set! (.-className new-child) "persistent-chat")
      (set! (.-style new-child)
        (str
          "float: left;
           width: 37%;
           height: 100%;
           padding: 10px;
           box-sizing: border-box;"))

      (if-let [next-sibling (.-nextSibling article-wrapper)]
        (.insertAfter parent-el new-child next-sibling)
        (.appendChild parent-el new-child))

      ; Style the article wrapper to occupy the remaining space
      (set! (.-style article-wrapper)
        (str "float: left; width: 63%; box-sizing: border-box;"))

      (rd/render [persistent-chat-ui] new-child))))


(defn init []
 (p "Hello from  chat-llm! ")
 (p "PROD Version: v-acc0dbb, previous version: v-13ca7a")
 (p "Starting initial setup.")
 (llm-chat-settings-page-struct)
 (render-persistent)
 ;(append-and-render-component)
  ;; check if the dom already has a chat-llm button, if so render for them
 (setup)
  ;; observe for new chat-llm buttons
 (start-observing)
  ;; a way to add the chat-llm button
 (add-new-option-to-context-menu)
 (bottom-bar-main)
 (p "Finished initial setup."))

