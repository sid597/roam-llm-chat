(ns ui.components.get-context
  (:require [cljs.core.async.interop :as asy :refer [<p!]]
    [reagent.core :as r :refer [atom]]
    [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]
    [ui.extract-data.chat :as ed :refer [extract-query-pages data-for-nodes get-all-images-for-node]]
    [ui.components.chat :refer [chat-context]]
    [ui.utils :refer [button-popover create-new-block button-with-tooltip model-mappings get-safety-settings update-block-string-for-block-with-child settings-button-popover image-to-text-for p get-child-of-child-with-str title->uid q block-has-child-with-str? call-llm-api update-block-string uid->title log get-child-with-str get-child-of-child-with-str-on-page get-open-page-uid get-block-parent-with-order get-focused-block create-struct gen-new-uid default-chat-struct get-todays-uid]]
    ["@blueprintjs/core" :as bp :refer [ControlGroup Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]))


;; visible pages as context
;; include query res
;; include dg refs
;; if under <800 tokens include linked refs

(defn get-context-button [parent-id]
  (let [get-context-uid          (:uid (get-child-with-str
                                         (block-has-child-with-str? (title->uid "LLM chat settings") "Quick action buttons")
                                         "Get context"))
        muid                 (:uid (get-child-with-str parent-id "Messages"))
        default-model            (r/atom (get-child-of-child-with-str get-context-uid "Settings" "Model"))
        default-temp             (r/atom (js/parseFloat (get-child-of-child-with-str get-context-uid "Settings" "Temperature")))
        get-linked-refs?         (r/atom (if (= "true" (get-child-of-child-with-str get-context-uid "Settings" "Get linked refs"))
                                            true
                                            false))
        extract-query-pages?     (r/atom (if (= "true" (get-child-of-child-with-str get-context-uid "Settings" "Extract query pages"))
                                           true
                                           false))
        extract-query-pages-ref? (r/atom (if (= "true" (get-child-of-child-with-str get-context-uid "Settings" "Extract query pages ref?"))
                                           true
                                           false))
        pre-prompt                (get-child-of-child-with-str get-context-uid "Prompt" "Pre-prompt")
        remaining-prompt         (get-child-of-child-with-str get-context-uid "Prompt" "Further instructions")
        active?                  (r/atom false)]
    (fn []
      [:div {:style {:flex "1 1 1 1"}}
       [button-with-tooltip
        "Get Context"
        [:> Button
         {:minimal true
          :fill false
          :loading @active?
          :on-click (fn [e]
                      (when (not @active?)
                        (reset! active? true))
                      (go
                        (let [current-page-uid    (<p! (get-open-page-uid))
                              title               (uid->title current-page-uid)
                              tref                (if (nil? title)
                                                    (str "((" current-page-uid "))")
                                                    (str "[[" title "]]"))
                              nodes               {:children [{:string tref}]}
                              page-context-data   (extract-query-pages
                                                    {:context              nodes
                                                     :get-linked-refs?     @get-linked-refs?
                                                     :extract-query-pages? @extract-query-pages?
                                                     :only-pages?          @extract-query-pages-ref?
                                                     :vision?              false})
                              prompt              (str pre-prompt
                                                    "\n"
                                                    "<discourse-node-page-content> \n" page-context-data "\n </discourse-node-page-content> \n"
                                                    remaining-prompt)
                              llm-context         [{:role "user"
                                                    :content prompt}]
                              settings           {:model        "gemini-1.5-flash"#_"gpt-4o-mini"
                                                   :temperature @default-temp
                                                   :max-tokens  1000}]

                          (do
                           (<p! (create-new-block
                                  muid
                                  "last"
                                  (str "**User:** ^^get context on:^^ " tref)
                                  #()))
                           (<p! (js/Promise.
                                  (fn [_]
                                    (call-llm-api
                                      {:messages llm-context
                                       :settings settings
                                       :callback (fn [response]
                                                   (let [res-str (-> response :body)]
                                                     (create-new-block
                                                       muid
                                                       "last"
                                                       (str "**Assistant:** " res-str)
                                                       (js/setTimeout
                                                         (fn [] (reset! active? false))
                                                         500))))}))))))))}
         "Get context"]]])))


(defn extract-context-children-data-as-str [context]
  (let [res (r/atom "")
        children (sort-by :order (:children @context))]
    (doseq [child children]
      (let [child-str (:string child)]
        ;(println "res " child-str)
        (swap! res str  (str child-str "\n"))))
    res))

(-> (get-child-of-child-with-str-on-page
      "LLM chat settings" "Quick action buttons" "Get context" "Prompt")
  :children
  first
  :string)


(sort-by :order (:children (get-child-of-child-with-str-on-page
                             "LLM chat settings" "Quick action buttons" "Get context" "Prompt")))

(get-child-of-child-with-str (:uid (get-child-with-str
                                     (block-has-child-with-str? (title->uid "LLM chat settings") "Quick action buttons")
                                     "Get context")) "Prompt" "Pre-prompt")


